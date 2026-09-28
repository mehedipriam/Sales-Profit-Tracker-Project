package com.salestracker.courier;

import com.salestracker.auth.ApiException;
import com.salestracker.customer.Customer;
import com.salestracker.customer.CustomerRepository;
import com.salestracker.order.CourierTotals;
import com.salestracker.order.OrderService;
import com.salestracker.order.OrderStatus;
import com.salestracker.order.SaleOrder;
import com.salestracker.order.SaleOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Cash on delivery through couriers: the courier collects the customer's cash and pays it out to the business later.
 * An order's cash is "on the way" while it is PENDING, "due" once it is PAID (delivered, collected) until the
 * business records the payout, and "received" after that. Returned and cancelled orders carry no cash.
 */
@Service
@Transactional
public class CourierPayoutService {
    static final int DUE_LIMIT = 500;
    static final int RECENT_LIMIT = 30;

    /** Per courier: cash still with it for delivered orders (due), and cash on parcels still on the way. */
    public record CourierBalance(Long courierId, String name, long dueOrders, BigDecimal dueAmount,
                                 long transitOrders, BigDecimal transitAmount) {}

    public record PayoutOrder(Long id, LocalDateTime orderedAt, String customerName, Long courierId,
                              String courierName, String consignmentId, String trackingNumber, String trackingLink,
                              BigDecimal codAmount,
                              OrderStatus status, LocalDate paidOn) {}

    public record PayoutsResponse(BigDecimal dueTotal, long dueOrders, BigDecimal transitTotal, long transitOrders,
                                  List<CourierBalance> couriers, List<PayoutOrder> due, List<PayoutOrder> recent) {}

    /** What the Dashboard shows: all cash the couriers hold for delivered orders. */
    public record CashWithCouriers(long orders, BigDecimal amount) {}

    private final SaleOrderRepository orders;
    private final CourierRepository couriers;
    private final CustomerRepository customers;
    private final OrderService orderService;

    public CourierPayoutService(SaleOrderRepository orders, CourierRepository couriers, CustomerRepository customers,
                                OrderService orderService) {
        this.orders = orders;
        this.couriers = couriers;
        this.customers = customers;
        this.orderService = orderService;
    }

    @Transactional(readOnly = true)
    public PayoutsResponse payouts(Long tenantId, Long courierId) {
        long filter = courierId == null ? 0L : courierId;
        List<CourierTotals> totals = orders.unpaidCourierTotals(tenantId, List.of(OrderStatus.PAID, OrderStatus.PENDING));
        Map<Long, Courier> courierById = new HashMap<>();
        couriers.findAllById(totals.stream().map(CourierTotals::courierId).collect(Collectors.toSet()))
                .forEach(c -> courierById.put(c.getId(), c));
        // Active couriers with nothing outstanding still get a row, so the page lists every courier.
        couriers.findByTenantIdAndActiveTrueOrderByName(tenantId).forEach(c -> courierById.putIfAbsent(c.getId(), c));

        List<CourierBalance> balances = courierById.values().stream()
                .map(c -> balance(c, totals))
                .sorted(Comparator.comparing(CourierBalance::dueAmount).reversed()
                        .thenComparing(CourierBalance::transitAmount, Comparator.reverseOrder())
                        .thenComparing(CourierBalance::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<SaleOrder> due = orders.payoutsDue(tenantId, filter, PageRequest.of(0, DUE_LIMIT));
        List<SaleOrder> recent = orders.recentPayouts(tenantId, filter, PageRequest.of(0, RECENT_LIMIT));
        Map<Long, Customer> customerById = byId(customers.findAllById(
                concat(due, recent).map(SaleOrder::getCustomerId).collect(Collectors.toSet())), Customer::getId);
        concat(due, recent).map(SaleOrder::getCourierId).filter(id -> !courierById.containsKey(id)).distinct()
                .forEach(id -> couriers.findById(id).ifPresent(c -> courierById.put(id, c)));

        CashWithCouriers dueTotal = sum(totals, OrderStatus.PAID);
        CashWithCouriers transitTotal = sum(totals, OrderStatus.PENDING);
        return new PayoutsResponse(dueTotal.amount(), dueTotal.orders(), transitTotal.amount(), transitTotal.orders(),
                balances,
                due.stream().map(o -> payoutOrder(o, customerById, courierById)).toList(),
                recent.stream().map(o -> payoutOrder(o, customerById, courierById)).toList());
    }

    @Transactional(readOnly = true)
    public CashWithCouriers cashWithCouriers(Long tenantId) {
        return sum(orders.unpaidCourierTotals(tenantId, List.of(OrderStatus.PAID)), OrderStatus.PAID);
    }

    /**
     * Records that the courier paid out these orders' cash on paidOn. A still-pending order counts as delivered
     * (the courier could only pay what it collected), so it becomes PAID through the normal order path.
     */
    public void markPaid(Long tenantId, Collection<Long> orderIds, LocalDate paidOn) {
        for (SaleOrder o : load(tenantId, orderIds)) {
            boolean payable = o.getCourierId() != null && o.getCourierPaidOn() == null
                    && o.getCodAmount() != null && o.getCodAmount().signum() > 0
                    && (o.getStatus() == OrderStatus.PAID || o.getStatus() == OrderStatus.PENDING);
            if (!payable) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Order #" + o.getId() + " has no courier cash waiting to be paid out");
            }
            if (o.getStatus() == OrderStatus.PENDING) {
                orderService.changeStatus(tenantId, o.getId(), OrderStatus.PAID);
            }
            o.setCourierPaidOn(paidOn);
        }
    }

    /** Undo a payout recorded by mistake: the cash counts as still with the courier again. */
    public void markUnpaid(Long tenantId, Collection<Long> orderIds) {
        load(tenantId, orderIds).forEach(o -> o.setCourierPaidOn(null));
    }

    // ---- internals ----

    private List<SaleOrder> load(Long tenantId, Collection<Long> orderIds) {
        Set<Long> ids = new HashSet<>(orderIds);
        List<SaleOrder> found = orders.findByTenantIdAndIdIn(tenantId, ids);
        if (found.size() != ids.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return found;
    }

    private static CourierBalance balance(Courier c, List<CourierTotals> totals) {
        List<CourierTotals> own = totals.stream().filter(t -> t.courierId().equals(c.getId())).toList();
        CashWithCouriers due = sum(own, OrderStatus.PAID);
        CashWithCouriers transit = sum(own, OrderStatus.PENDING);
        return new CourierBalance(c.getId(), c.getName(), due.orders(), due.amount(), transit.orders(), transit.amount());
    }

    private static CashWithCouriers sum(List<CourierTotals> totals, OrderStatus status) {
        long count = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (CourierTotals t : totals) {
            if (t.status() == status) {
                count += t.orders();
                amount = amount.add(t.amount());
            }
        }
        return new CashWithCouriers(count, amount);
    }

    private static PayoutOrder payoutOrder(SaleOrder o, Map<Long, Customer> customerById, Map<Long, Courier> courierById) {
        Courier c = courierById.get(o.getCourierId());
        Customer customer = customerById.get(o.getCustomerId());
        return new PayoutOrder(o.getId(), o.getOrderedAt(), customer == null ? null : customer.getName(),
                o.getCourierId(), c == null ? null : c.getName(), o.getConsignmentId(), o.getTrackingNumber(),
                c == null ? null : c.trackingLink(o.getTrackingRef()), o.getCodAmount(), o.getStatus(),
                o.getCourierPaidOn());
    }

    private static Stream<SaleOrder> concat(List<SaleOrder> a, List<SaleOrder> b) {
        return Stream.concat(a.stream(), b.stream());
    }

    private static <T> Map<Long, T> byId(Collection<T> values, Function<T, Long> id) {
        return values.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
