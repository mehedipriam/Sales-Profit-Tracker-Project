package com.salestracker.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salestracker.agent.DraftOrder.*;
import com.salestracker.agent.DraftRevision.Change;
import com.salestracker.auth.ApiException;
import com.salestracker.auth.AuthUser;
import com.salestracker.customer.Customer;
import com.salestracker.customer.CustomerRepository;
import com.salestracker.order.OrderStatus;
import com.salestracker.platform.Platform;
import com.salestracker.platform.PlatformRepository;
import com.salestracker.product.Product;
import com.salestracker.product.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Changes a draft because the user said so ("make it 3", "remove the red shirt", "the phone is ..."). The AI only
 * says what to change; the changes are applied here and the whole draft is checked again, exactly as a new one is.
 * The draft comes from the browser, so everything in it is looked up again for this shop.
 */
@Service
public class RevisionService {
    public record CustomerState(Long id, @Size(max = 150) String name, @Size(max = 32) String phone,
                                @Size(max = 500) String address, boolean isNew) {}

    public record LineState(@Size(max = 300) String text, Long productId, Integer quantity, BigDecimal soldPrice) {}

    public record DraftState(@NotNull @Valid CustomerState customer, Long platformId, OrderStatus status,
                             BigDecimal deliveryCharge, @Size(max = 5000) String notes,
                             @NotNull @Size(max = 100) List<@Valid LineState> lines) {}

    public record RevisionRequest(@NotBlank @Size(max = 1000) String instruction, @NotNull @Valid DraftState draft) {}

    /** reply says what was done, or what is still needed. */
    public record Revised(DraftOrder draft, String reply) {}

    /** A line while it is being changed. */
    private static final class Work {
        String text;
        Long productId;
        Integer quantity;
        BigDecimal price;
        boolean removed;

        Work(String text, Long productId, Integer quantity, BigDecimal price) {
            this.text = text;
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
        }
    }

    private final AiClient ai;
    private final DraftService drafts;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final PlatformRepository platforms;
    private final ObjectMapper json;

    public RevisionService(AiClient ai, DraftService drafts, CustomerRepository customers, ProductRepository products,
                           PlatformRepository platforms, ObjectMapper json) {
        this.ai = ai;
        this.drafts = drafts;
        this.customers = customers;
        this.products = products;
        this.platforms = platforms;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Revised revise(AuthUser actor, RevisionRequest req) {
        Long tenantId = actor.tenantId();
        DraftState in = req.draft();
        List<Product> catalog = products.findByTenantIdAndActiveTrue(tenantId);
        Map<Long, Product> byId = new HashMap<>();
        catalog.forEach(p -> byId.put(p.getId(), p));
        Optional<Customer> saved = in.customer().id() == null ? Optional.empty()
                : customers.findByIdAndTenantId(in.customer().id(), tenantId);

        List<Work> work = new ArrayList<>();
        for (LineState l : in.lines()) {
            // A product id that is not this shop's (or no longer active) is dropped; the name is matched again.
            work.add(new Work(l.text(), byId.containsKey(l.productId()) ? l.productId() : null, l.quantity(), l.soldPrice()));
        }

        DraftRevision plan = ai.reviseDraft(summary(in, saved, work, byId, tenantId), req.instruction().trim());

        List<Issue> issues = new ArrayList<>();
        applyChanges(plan, work, issues);
        List<Work> kept = work.stream().filter(w -> !w.removed).toList();

        DraftCustomer customer = customer(tenantId, in.customer(), saved, plan, issues);
        Optional<Platform> platform = platform(tenantId, in, plan, issues);

        List<Line> lines = new ArrayList<>();
        for (Work w : kept) lines.add(drafts.line(w.text, w.productId, w.quantity, w.price, catalog, issues));

        OrderStatus status = plan.status() != null ? plan.status() : in.status() != null ? in.status() : OrderStatus.PENDING;
        BigDecimal delivery = plan.deliveryCharge() != null && plan.deliveryCharge().signum() >= 0 ? plan.deliveryCharge()
                : in.deliveryCharge() != null && in.deliveryCharge().signum() >= 0 ? in.deliveryCharge() : BigDecimal.ZERO;
        String notes = plan.notes() != null ? (plan.notes().isBlank() ? null : plan.notes()) : in.notes();

        drafts.warnIfDuplicate(tenantId, customer, lines, issues);
        DraftOrder draft = drafts.assemble(customer, platform, status, delivery, notes, lines, issues);
        return new Revised(draft, plan.reply() != null ? plan.reply() : "Done.");
    }

    // ---- what the AI sees ----

    private String summary(DraftState in, Optional<Customer> saved, List<Work> work, Map<Long, Product> byId, Long tenantId) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", saved.map(Customer::getName).orElse(in.customer().name()));
        customer.put("phone", saved.map(Customer::getPhone).orElse(in.customer().phone()));
        customer.put("address", saved.map(Customer::getAddress).orElse(in.customer().address()));
        customer.put("alreadySaved", saved.isPresent());
        out.put("customer", customer);
        out.put("platform", platformName(tenantId, in.platformId()));
        out.put("status", in.status() != null ? in.status() : OrderStatus.PENDING);
        out.put("deliveryCharge", in.deliveryCharge() != null ? in.deliveryCharge() : BigDecimal.ZERO);
        out.put("notes", in.notes());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (int i = 0; i < work.size(); i++) {
            Work w = work.get(i);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("line", i + 1);
            line.put("product", w.productId != null ? byId.get(w.productId).getName() : w.text);
            line.put("productChosen", w.productId != null);
            line.put("quantity", w.quantity);
            line.put("price", w.price);
            lines.add(line);
        }
        out.put("lines", lines);
        try {
            return json.writeValueAsString(out);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The draft could not be read");
        }
    }

    private String platformName(Long tenantId, Long platformId) {
        return platformId == null ? null : platforms.findByIdAndTenantId(platformId, tenantId).map(Platform::getName).orElse(null);
    }

    // ---- applying the changes ----

    private void applyChanges(DraftRevision plan, List<Work> work, List<Issue> issues) {
        int original = work.size(); // line numbers mean the draft as shown, so lines added here cannot be referred to
        for (Change c : plan.changes()) {
            if (c.action() == null) continue;
            if (c.action() == DraftRevision.Action.ADD) {
                if (c.product() == null || c.product().isBlank()) continue;
                work.add(new Work(c.product().trim(), null, c.quantity() != null && c.quantity() >= 1 ? c.quantity() : 1,
                        c.price() != null && c.price().signum() >= 0 ? c.price() : null));
                continue;
            }
            Integer n = c.line();
            if (n == null || n < 1 || n > original || work.get(n - 1).removed) {
                issues.add(new Issue(Level.WARNING, "There is no line " + n + " to change"));
                continue;
            }
            Work w = work.get(n - 1);
            switch (c.action()) {
                case SET_QUANTITY -> { if (c.quantity() != null && c.quantity() >= 1) w.quantity = c.quantity(); }
                case SET_PRICE -> { if (c.price() != null && c.price().signum() >= 0) w.price = c.price(); }
                case REMOVE -> w.removed = true;
                case REPLACE_PRODUCT -> {
                    if (c.product() != null && !c.product().isBlank()) {
                        w.text = c.product().trim();
                        w.productId = null;
                        w.price = c.price() != null && c.price().signum() >= 0 ? c.price() : null; // the old price was for the old product
                    }
                }
                default -> { }
            }
        }
    }

    private DraftCustomer customer(Long tenantId, CustomerState in, Optional<Customer> saved, DraftRevision plan, List<Issue> issues) {
        String name = plan.customerName() != null ? plan.customerName() : in.name();
        String phone = plan.customerPhone() != null ? plan.customerPhone() : in.phone();
        String address = plan.customerAddress() != null ? plan.customerAddress() : in.address();

        if (saved.isPresent()) {
            Customer c = saved.get();
            boolean otherPhone = plan.customerPhone() != null && !Objects.equals(DraftService.phoneSuffix(plan.customerPhone()),
                    DraftService.phoneSuffix(c.getPhone()));
            if (!otherPhone) {
                // The shop's saved customer is not rewritten from here.
                boolean wantsChange = (plan.customerName() != null && !plan.customerName().equalsIgnoreCase(c.getName()))
                        || (plan.customerAddress() != null && !plan.customerAddress().equalsIgnoreCase(String.valueOf(c.getAddress())));
                if (wantsChange) {
                    issues.add(new Issue(Level.WARNING, "This customer is already saved, so their name and address were not changed. Edit them on the Customers page"));
                }
                return new DraftCustomer(c.getId(), c.getName(), c.getPhone(), c.getAddress(), false);
            }
            // A different phone number: another saved customer if that number is on file, otherwise a new customer.
            name = plan.customerName() != null ? plan.customerName() : c.getName();
            address = plan.customerAddress() != null ? plan.customerAddress() : c.getAddress();
        }
        // A phone number that is on file means this is that saved customer; the name left in the draft was only a guess.
        if (plan.customerPhone() != null && plan.customerName() == null) {
            Optional<Customer> onFile = drafts.findByPhone(tenantId, plan.customerPhone());
            if (onFile.isPresent()) {
                Customer c = onFile.get();
                return new DraftCustomer(c.getId(), c.getName(), c.getPhone(), c.getAddress(), false);
            }
        }
        ExtractedOrder x = new ExtractedOrder(blank(name), blank(phone), blank(address), null, null, null, List.of());
        return drafts.customer(tenantId, x, issues);
    }

    private Optional<Platform> platform(Long tenantId, DraftState in, DraftRevision plan, List<Issue> issues) {
        if (plan.platform() != null) {
            Optional<Platform> named = platforms.findByTenantIdAndActiveTrueOrderByName(tenantId).stream()
                    .filter(p -> p.getName().equalsIgnoreCase(plan.platform())
                            || p.getName().toLowerCase(Locale.ROOT).contains(plan.platform().toLowerCase(Locale.ROOT)))
                    .findFirst();
            if (named.isPresent()) return named;
            issues.add(new Issue(Level.WARNING, "There is no platform called " + plan.platform()));
        }
        Optional<Platform> current = in.platformId() == null ? Optional.empty()
                : platforms.findByIdAndTenantId(in.platformId(), tenantId).filter(Platform::isActive);
        return current.isPresent() ? current : drafts.platform(tenantId, null);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
