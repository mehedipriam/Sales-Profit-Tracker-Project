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
    private final CustomerRepository customers;
    private final PlatformRepository platforms;
    private final SaleOrderRepository orders;

    public CustomerService(CustomerRepository customers, PlatformRepository platforms, SaleOrderRepository orders) {
        this.customers = customers;
        this.platforms = platforms;
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(Long tenantId, String q, Long platformId, int page, int size) {
        Page<Customer> result = customers.search(tenantId, Search.pattern(q), platformId == null ? 0L : platformId,
                Search.page(page, size, "name"));
        Map<Long, Insights> insights = insights(tenantId, result.map(Customer::getId).toSet());
        return PageResponse.of(result, c -> CustomerResponse.of(c, insights.get(c.getId())));
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
