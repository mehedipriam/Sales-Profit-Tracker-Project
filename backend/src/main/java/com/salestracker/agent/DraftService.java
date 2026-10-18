package com.salestracker.agent;

import com.salestracker.agent.DraftOrder.*;
import com.salestracker.auth.AuthUser;
import com.salestracker.customer.Customer;
import com.salestracker.customer.CustomerRepository;
import com.salestracker.order.OrderDtos.OrderDetail;
import com.salestracker.order.OrderDtos.OrderRequest;
import com.salestracker.order.OrderService;
import com.salestracker.order.OrderStatus;
import com.salestracker.platform.Platform;
import com.salestracker.platform.PlatformRepository;
import com.salestracker.product.Product;
import com.salestracker.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Turns what the AI read from a message into a draft order checked against the shop's customers, products and platforms. */
@Service
public class DraftService {
    private final AiClient ai;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final PlatformRepository platforms;
    private final OrderService orders;

    public DraftService(AiClient ai, CustomerRepository customers, ProductRepository products,
                        PlatformRepository platforms, OrderService orders) {
        this.ai = ai;
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.platforms = platforms;
    }

    /** Reads the message and builds the draft. Nothing is saved. */
    @Transactional(readOnly = true)
    public DraftOrder prepare(AuthUser actor, String text) {
        return build(actor.tenantId(), ai.extractOrder(text));
    }

    DraftOrder build(Long tenantId, ExtractedOrder x) {
        List<Issue> issues = new ArrayList<>();

        DraftCustomer customer = customer(tenantId, x, issues);

        Optional<Platform> platform = platform(tenantId, x.platform());
        if (platform.isEmpty()) issues.add(new Issue(Level.ERROR, "Choose a platform for this order"));

        List<Product> catalog = products.findByTenantIdAndActiveTrue(tenantId);
        List<Line> lines = new ArrayList<>();
        for (ExtractedOrder.Item item : x.items()) lines.add(line(item, catalog, issues));
        if (lines.isEmpty()) issues.add(new Issue(Level.ERROR, "No products were found in the message"));

        BigDecimal delivery = x.deliveryCharge() != null && x.deliveryCharge().signum() >= 0 ? x.deliveryCharge() : BigDecimal.ZERO;
        boolean canConfirm = issues.stream().noneMatch(i -> i.level() == Level.ERROR);
        return new DraftOrder(customer, platform.map(Platform::getId).orElse(null), platform.map(Platform::getName).orElse(null),
                OrderStatus.PENDING, delivery, x.notes(), lines, issues, canConfirm);
    }

    // ---- customer ----

    private DraftCustomer customer(Long tenantId, ExtractedOrder x, List<Issue> issues) {
        Optional<Customer> existing = findByPhone(tenantId, x.phone());
        if (existing.isPresent()) {
            Customer c = existing.get();
            // A name written in another script (রহিম / Rahim) is the same person written differently, not a mismatch.
            if (x.customerName() != null && !c.getName().equalsIgnoreCase(x.customerName())
                    && isLatin(c.getName()) == isLatin(x.customerName())) {
                issues.add(new Issue(Level.WARNING, "This phone number belongs to " + c.getName()
                        + ", but the message says " + x.customerName()));
            }
            return new DraftCustomer(c.getId(), c.getName(), c.getPhone(), c.getAddress(), false);
        }
        if (x.customerName() == null) issues.add(new Issue(Level.ERROR, "The customer's name is missing"));
        if (x.phone() == null) issues.add(new Issue(Level.WARNING, "No phone number, so this customer cannot be matched with past orders"));
        if (x.address() == null) issues.add(new Issue(Level.WARNING, "No delivery address"));
        return new DraftCustomer(null, x.customerName(), x.phone(), x.address(), true);
    }

    /** The confirm step: creates the order (and the customer, unless their phone is already on file) like the order form does. */
    public OrderDetail confirm(AuthUser actor, OrderRequest req) {
        return orders.create(actor, reuseCustomer(actor.tenantId(), req));
    }

    private static boolean isLatin(String name) {
        return name.codePoints().filter(Character::isLetter)
                .allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN);
    }

    /** Finds a customer by phone however it was typed (dashes, spaces, +880 or 0 in front): the last 10 digits decide. */
    Optional<Customer> findByPhone(Long tenantId, String phone) {
        String suffix = phoneSuffix(phone);
        if (suffix == null) return Optional.empty();
        return customers.findByPhoneEndingWith(tenantId, suffix).stream().findFirst();
    }

    static String phoneSuffix(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) return null;
        return digits.substring(Math.max(0, digits.length() - 10));
    }

    /** The same request with an existing customer used when its phone is already on file, so no duplicate is created. */
    OrderRequest reuseCustomer(Long tenantId, OrderRequest req) {
        if (req.customerId() != null || req.newCustomer() == null) return req;
        return findByPhone(tenantId, req.newCustomer().phone())
                .map(c -> new OrderRequest(req.platformId(), c.getId(), null, req.status(), req.deliveryCharge(),
                        req.courierId(), req.consignmentId(), req.trackingNumber(), req.codAmount(), req.orderedAt(),
                        req.notes(), req.items()))
                .orElse(req);
    }

    // ---- platform ----

    /** The platform the message names; failing that Facebook (where the orders come from), or the only one there is. */
    private Optional<Platform> platform(Long tenantId, String named) {
        List<Platform> active = platforms.findByTenantIdAndActiveTrueOrderByName(tenantId);
        if (named != null) {
            String n = named.toLowerCase(Locale.ROOT);
            Optional<Platform> hit = active.stream().filter(p -> p.getName().equalsIgnoreCase(named)).findFirst()
                    .or(() -> active.stream().filter(p -> p.getName().toLowerCase(Locale.ROOT).contains(n)
                            || n.contains(p.getName().toLowerCase(Locale.ROOT))).findFirst());
            if (hit.isPresent()) return hit;
        }
        List<Platform> facebook = active.stream().filter(p -> p.getName().toLowerCase(Locale.ROOT).contains("facebook")).toList();
        if (facebook.size() == 1) return Optional.of(facebook.get(0));
        return active.size() == 1 ? Optional.of(active.get(0)) : Optional.empty();
    }

    // ---- items ----

    private Line line(ExtractedOrder.Item item, List<Product> catalog, List<Issue> issues) {
        String text = item.name() == null ? "" : item.name().trim();
        int qty = item.quantity() != null && item.quantity() >= 1 ? item.quantity() : 1;
        ProductMatcher.Match m = ProductMatcher.match(text, catalog);

        if (m.status() != MatchStatus.MATCHED) {
            List<Candidate> candidates = m.candidates().stream().map(p -> new Candidate(p.getId(), p.getName(), p.getSellingPrice(), p.getStockQty())).toList();
            issues.add(new Issue(Level.ERROR, m.status() == MatchStatus.AMBIGUOUS
                    ? "\"" + text + "\" could be more than one product: choose one"
                    : "No product matches \"" + text + "\""));
            return new Line(text, null, null, qty, item.price(), null, null, m.status(), candidates);
        }

        Product p = m.product();
        BigDecimal price = item.price();
        if (price == null) {
            price = p.getSellingPrice();
            issues.add(new Issue(Level.WARNING, "No price for " + p.getName() + ": using its list price " + p.getSellingPrice().toPlainString()));
        } else if (price.compareTo(p.getSellingPrice()) != 0) {
            issues.add(new Issue(Level.WARNING, p.getName() + " is " + price.toPlainString() + " in the message but "
                    + p.getSellingPrice().toPlainString() + " in your products"));
        }
        if (p.getStockQty() != null && qty > p.getStockQty()) {
            issues.add(new Issue(Level.WARNING, "Only " + p.getStockQty() + " of " + p.getName() + " in stock, ordering " + qty));
        }
        return new Line(text, p.getId(), p.getName(), qty, price, p.getSellingPrice(), p.getStockQty(), MatchStatus.MATCHED, List.of());
    }
}
