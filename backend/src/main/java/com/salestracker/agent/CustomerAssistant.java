package com.salestracker.agent;

import com.salestracker.agent.CustomerReplies.CustomerView;
import com.salestracker.agent.CustomerReplies.Field;
import com.salestracker.agent.CustomerReplies.Kind;
import com.salestracker.agent.CustomerReplies.Proposal;
import com.salestracker.auth.ApiException;
import com.salestracker.auth.AuthUser;
import com.salestracker.common.Search;
import com.salestracker.customer.Customer;
import com.salestracker.customer.CustomerDtos.CustomerPatch;
import com.salestracker.customer.CustomerDtos.CustomerRequest;
import com.salestracker.customer.CustomerDtos.CustomerResponse;
import com.salestracker.customer.CustomerRepository;
import com.salestracker.customer.CustomerService;
import com.salestracker.platform.Platform;
import com.salestracker.platform.PlatformRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Adding, finding and updating customers from the chat. Finding changes nothing. Adding and updating only prepare a
 * proposal; nothing is saved until Confirm, which goes through the same customer rules as the Customers page.
 */
@Service
public class CustomerAssistant {
    private final CustomerMatcher matcher;
    private final CustomerRepository customers;
    private final CustomerService customerService;
    private final PlatformRepository platforms;
    private final DraftService drafts;

    public CustomerAssistant(CustomerMatcher matcher, CustomerRepository customers, CustomerService customerService,
                             PlatformRepository platforms, DraftService drafts) {
        this.matcher = matcher;
        this.customers = customers;
        this.customerService = customerService;
        this.platforms = platforms;
        this.drafts = drafts;
    }

    @Transactional(readOnly = true)
    public AgentReply handle(AuthUser user, Intent intent) {
        return switch (intent.kind()) {
            case CUSTOMER_ADD -> add(user.tenantId(), intent);
            case CUSTOMER_FIND -> find(user, intent);
            default -> update(user.tenantId(), intent);
        };
    }

    // ---- add ----

    /** A card to fill in and confirm; any details the message gave are already in it, and a blank card is fine. */
    private AgentReply add(Long tenant, Intent in) {
        Optional<Customer> same = drafts.findByPhone(tenant, in.phone());
        if (same.isPresent()) {
            Customer c = same.get();
            return AgentReply.message("That phone number is already saved as " + c.getName()
                    + (c.getAddress() == null ? "" : " (" + c.getAddress() + ")") + ". No new customer was made.");
        }
        List<String> warnings = new ArrayList<>();
        Platform platform = null;
        if (in.platform() != null) {
            platform = platformNamed(tenant, in.platform());
            if (platform == null) warnings.add("No platform called \"" + in.platform() + "\", so it is left blank");
        }
        List<Field> fields = List.of(
                new Field("name", "Name", null, nz(in.customerName())),
                new Field("phone", "Phone", null, nz(in.phone())),
                new Field("address", "Address", null, nz(in.address())),
                new Field("platform", "Platform", null, platform == null ? "" : platform.getName()));
        return AgentReply.proposal(new Proposal(Kind.ADD, null, null, fields, platform == null ? null : platform.getId(), warnings));
    }

    // ---- find ----

    private AgentReply find(AuthUser user, Intent in) {
        if (in.customerName() == null && in.phone() == null) {
            return AgentReply.message("Whose details do you want? Tell me the customer's name or phone number.");
        }
        List<Customer> found = matcher.find(user.tenantId(), in.customerName(), in.phone());
        String said = in.customerName() != null ? in.customerName() : in.phone();
        if (found.isEmpty()) return AgentReply.message("No customer matches \"" + said.trim() + "\".");
        if (found.size() > 1) return AgentReply.message(CustomerMatcher.which(said, found));
        Customer c = found.get(0);
        CustomerResponse r = customerService.get(user.tenantId(), c.getId());
        return AgentReply.customer(new CustomerView(c.getId(), c.getName(), c.getPhone(), c.getAddress(),
                platformName(user.tenantId(), c.getSourcePlatformId()), r.insights().orders(), r.insights().lastOrderAt(),
                user.role().seesFinancials() ? r.insights().spent() : null));
    }

    // ---- update ----

    private AgentReply update(Long tenant, Intent in) {
        if (in.customerName() == null) return AgentReply.message("Which customer should I change? Tell me their name.");
        if (in.newName() == null && in.phone() == null && in.address() == null && in.platform() == null) {
            return AgentReply.message("What should I change for " + in.customerName().trim() + "? For example their address or phone number.");
        }
        List<Customer> found = matcher.find(tenant, in.customerName(), null);
        if (found.isEmpty()) return AgentReply.message("No customer matches \"" + in.customerName().trim() + "\".");
        if (found.size() > 1) return AgentReply.message(CustomerMatcher.which(in.customerName(), found));
        Customer c = found.get(0);

        List<Field> fields = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Long platformId = null;
        change(fields, "name", "Name", c.getName(), in.newName());
        if (in.phone() != null) {
            Optional<Customer> other = drafts.findByPhone(tenant, in.phone());
            if (other.isPresent() && !other.get().getId().equals(c.getId())) {
                return AgentReply.message("That phone number is already saved for " + other.get().getName() + ". Nothing was changed.");
            }
            change(fields, "phone", "Phone", c.getPhone(), in.phone());
        }
        change(fields, "address", "Address", c.getAddress(), in.address());
        if (in.platform() != null) {
            Platform p = platformNamed(tenant, in.platform());
            if (p == null) {
                warnings.add("No platform called \"" + in.platform() + "\", so the platform is not changed");
            } else if (!p.getId().equals(c.getSourcePlatformId())) {
                fields.add(new Field("platform", "Platform", platformName(tenant, c.getSourcePlatformId()), p.getName()));
                platformId = p.getId();
            }
        }
        if (fields.isEmpty()) return AgentReply.message(c.getName() + " already has those details. Nothing to change.");
        return AgentReply.proposal(new Proposal(Kind.UPDATE, c.getId(), c.getName(), fields, platformId, warnings));
    }

    /** Adds a before/after row when a new value was given and differs from what is saved. */
    private static void change(List<Field> fields, String key, String label, String before, String wanted) {
        String after = Search.blankToNull(wanted);
        if (after == null || Objects.equals(after, before)) return;
        fields.add(new Field(key, label, before, after));
    }

    // ---- confirm ----

    /** Creates the customer, unless the phone number is already on file. */
    @Transactional
    public CustomerResponse confirmAdd(AuthUser user, CustomerRequest req) {
        drafts.findByPhone(user.tenantId(), req.phone()).ifPresent(c -> {
            throw new ApiException(HttpStatus.CONFLICT, "That phone number is already saved as " + c.getName());
        });
        return customerService.create(user.tenantId(), req);
    }

    /** Saves only the fields given; everything else about the customer stays as it was. */
    @Transactional
    public CustomerResponse confirmUpdate(AuthUser user, Long id, CustomerPatch patch) {
        Customer c = customers.findByIdAndTenantId(id, user.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found"));
        if (patch.phone() != null) {
            drafts.findByPhone(user.tenantId(), patch.phone()).filter(o -> !o.getId().equals(c.getId())).ifPresent(o -> {
                throw new ApiException(HttpStatus.CONFLICT, "That phone number is already saved as " + o.getName());
            });
        }
        return customerService.update(user.tenantId(), id, new CustomerRequest(
                patch.name() != null ? patch.name() : c.getName(),
                patch.phone() != null ? patch.phone() : c.getPhone(),
                patch.address() != null ? patch.address() : c.getAddress(),
                patch.sourcePlatformId() != null ? patch.sourcePlatformId() : c.getSourcePlatformId(),
                c.getNotes()));
    }

    // ---- helpers ----

    private String platformName(Long tenant, Long platformId) {
        return platformId == null ? null : platforms.findByIdAndTenantId(platformId, tenant).map(Platform::getName).orElse(null);
    }

    /** The active platform the name means: the same name, or one that contains it or is contained in it. */
    private Platform platformNamed(Long tenant, String named) {
        String n = named.trim().toLowerCase(Locale.ROOT);
        List<Platform> active = platforms.findByTenantIdAndActiveTrueOrderByName(tenant);
        return active.stream().filter(p -> p.getName().equalsIgnoreCase(named.trim())).findFirst()
                .or(() -> active.stream().filter(p -> p.getName().toLowerCase(Locale.ROOT).contains(n)
                        || n.contains(p.getName().toLowerCase(Locale.ROOT))).findFirst())
                .orElse(null);
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
