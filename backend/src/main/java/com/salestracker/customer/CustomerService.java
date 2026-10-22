package com.salestracker.customer;

import com.salestracker.auth.ApiException;
import com.salestracker.common.PageResponse;
import com.salestracker.common.Search;
import com.salestracker.customer.CustomerDtos.CustomerRequest;
import com.salestracker.customer.CustomerDtos.CustomerResponse;
import com.salestracker.customer.CustomerDtos.Insights;
import com.salestracker.order.CustomerRevenue;
import com.salestracker.order.CustomerStatusTotals;
import com.salestracker.order.OrderStatus;
import com.salestracker.order.SaleOrderRepository;
import com.salestracker.platform.PlatformRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class CustomerService {
    /** The most customers read at once when a group (repeat, new, often returns) has to be worked out. */
    private static final int MAX_GROUP_SCAN = 10_000;

    private final CustomerRepository customers;
    private final PlatformRepository platforms;
    private final SaleOrderRepository orders;

    public CustomerService(CustomerRepository customers, PlatformRepository platforms, SaleOrderRepository orders) {
        this.customers = customers;
        this.platforms = platforms;
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    /** sort is "name" (alphabetical, the default, good for pickers) or "created" (in the order they were added). */
    public PageResponse<CustomerResponse> list(Long tenantId, String q, Long platformId, String group, int page, int size,
                                               String sort) {
        if (Set.of("repeat", "new", "risk").contains(group)) return listGroup(tenantId, q, platformId, group, page, size, sort);
        Page<Customer> result = customers.search(tenantId, Search.pattern(q), platformId == null ? 0L : platformId,
                Search.page(page, size, "created".equals(sort) ? "id" : "name"));
        Map<Long, Insights> insights = insights(tenantId, result.map(Customer::getId).toSet());
        return PageResponse.of(result, c -> CustomerResponse.of(c, insights.get(c.getId())));
    }

    /**
     * The customers in one group: repeat (2+ orders), new (0 or 1 order) or risk (often returns). These depend on each
     * customer's order totals, so the matching customers are read, filtered here and then cut into pages.
     */
    private PageResponse<CustomerResponse> listGroup(Long tenantId, String q, Long platformId, String group, int page,
                                                     int size, String sort) {
        Sort order = Sort.by("created".equals(sort) ? "id" : "name");
        List<Customer> all = customers.search(tenantId, Search.pattern(q), platformId == null ? 0L : platformId,
                PageRequest.of(0, MAX_GROUP_SCAN, order)).getContent();
        Map<Long, Insights> insights = insights(tenantId, all.stream().map(Customer::getId).collect(Collectors.toSet()));
        List<Customer> matching = all.stream().filter(c -> inGroup(insights.get(c.getId()), group)).toList();

        Pageable paging = Search.page(page, size, "name");
        int from = (int) Math.min(paging.getOffset(), matching.size());
        int to = Math.min(from + paging.getPageSize(), matching.size());
        Page<Customer> slice = new PageImpl<>(matching.subList(from, to), paging, matching.size());
        return PageResponse.of(slice, c -> CustomerResponse.of(c, insights.get(c.getId())));
    }

    private static boolean inGroup(Insights i, String group) {
        return switch (group) {
            case "repeat" -> i.orders() >= 2;
            case "new" -> i.orders() <= 1;
            default -> i.flagged();
        };
    }

    /** Counts and money for the cards above the customer list. */
    @Transactional(readOnly = true)
    public CustomerSummary summary(Long tenantId) {
        List<Customer> all = customers.search(tenantId, "%", 0L, PageRequest.of(0, MAX_GROUP_SCAN, Sort.by("id"))).getContent();
        Map<Long, Insights> insights = insights(tenantId, all.stream().map(Customer::getId).collect(Collectors.toSet()));
        long repeat = 0;
        long risk = 0;
        BigDecimal spent = BigDecimal.ZERO;
        for (Insights i : insights.values()) {
            if (i.orders() >= 2) repeat++;
            if (i.flagged()) risk++;
            spent = spent.add(i.spent());
        }
        return new CustomerSummary(all.size(), repeat, risk, spent);
    }

    public record CustomerSummary(long customers, long repeat, long oftenReturn, BigDecimal totalSpent) {}

    /** One customer with their order history. */
    @Transactional(readOnly = true)
    public CustomerResponse get(Long tenantId, Long id) {
        Customer c = find(tenantId, id);
        return CustomerResponse.of(c, insights(tenantId, Set.of(id)).get(id));
    }

    public CustomerResponse create(Long tenantId, CustomerRequest req) {
        return save(new Customer(tenantId), req);
    }

    public CustomerResponse update(Long tenantId, Long id, CustomerRequest req) {
        return save(find(tenantId, id), req);
    }

    public void delete(Long tenantId, Long id) {
        Customer c = find(tenantId, id);
        try {
            customers.delete(c);
            customers.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "This customer has orders and cannot be deleted");
        }
    }

    private Customer find(Long tenantId, Long id) {
        return customers.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private CustomerResponse save(Customer c, CustomerRequest req) {
        Long platformId = req.sourcePlatformId();
        if (platformId != null && !platforms.existsByIdAndTenantId(platformId, c.getTenantId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown platform");
        }
        c.apply(req.name().trim(), Search.blankToNull(req.phone()), Search.blankToNull(req.address()),
                platformId, Search.blankToNull(req.notes()));
        Customer saved = customers.save(c);
        return CustomerResponse.of(saved, insights(saved.getTenantId(), Set.of(saved.getId())).get(saved.getId()));
    }

    /** Insights for the given customers, two grouped queries however many there are; NONE for those without orders. */
    private Map<Long, Insights> insights(Long tenantId, Set<Long> customerIds) {
        if (customerIds.isEmpty()) return Map.of();
        Map<Long, List<CustomerStatusTotals>> totals = orders.totalsByCustomer(tenantId, customerIds).stream()
                .collect(Collectors.groupingBy(CustomerStatusTotals::customerId));
        Map<Long, BigDecimal> revenue = orders.paidRevenueByCustomer(tenantId, customerIds).stream()
                .collect(Collectors.toMap(CustomerRevenue::customerId, CustomerRevenue::revenue));

        Map<Long, Insights> result = new HashMap<>();
        for (Long id : customerIds) {
            List<CustomerStatusTotals> rows = totals.getOrDefault(id, List.of());
            Map<OrderStatus, CustomerStatusTotals> byStatus = new EnumMap<>(OrderStatus.class);
            rows.forEach(r -> byStatus.put(r.status(), r));
            CustomerStatusTotals paid = byStatus.get(OrderStatus.PAID);
            BigDecimal spent = revenue.getOrDefault(id, BigDecimal.ZERO)
                    .add(paid == null ? BigDecimal.ZERO : paid.delivery());
            LocalDateTime last = rows.stream().map(CustomerStatusTotals::lastOrderedAt)
                    .max(Comparator.naturalOrder()).orElse(null);
            result.put(id, rows.isEmpty() ? Insights.NONE : Insights.of(count(byStatus, OrderStatus.PAID),
                    count(byStatus, OrderStatus.PENDING), count(byStatus, OrderStatus.RETURNED),
                    count(byStatus, OrderStatus.CANCELLED), spent, last));
        }
        return result;
    }

    private static long count(Map<OrderStatus, CustomerStatusTotals> byStatus, OrderStatus status) {
        CustomerStatusTotals t = byStatus.get(status);
        return t == null ? 0 : t.orders();
    }
}
