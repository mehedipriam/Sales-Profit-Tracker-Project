package com.salestracker.dashboard;

import com.salestracker.common.DateRange;
import com.salestracker.order.OrderDtos.OrderSummary;
import com.salestracker.order.OrderService;
import com.salestracker.order.OrderStatus;
import com.salestracker.order.SaleOrderRepository;
import com.salestracker.order.StatusTotals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private static final int RECENT_ORDERS = 8;

    public record Totals(long orders, BigDecimal revenue, BigDecimal cost, BigDecimal profit) {
        static Totals of(StatusTotals t) {
            return new Totals(t.orders(), t.revenue(), t.cost(), t.revenue().subtract(t.cost()));
        }
    }

    /**
     * realized = PAID orders; pending = PENDING orders (expected, e.g. cash on delivery);
     * RETURNED and CANCELLED orders are excluded from money totals and only counted.
     */
    public record DashboardResponse(Totals realized, Totals pending, long returnedOrders, long cancelledOrders,
                                    List<OrderSummary> recentOrders) {}

    private final SaleOrderRepository orders;
    private final OrderService orderService;

    public DashboardService(SaleOrderRepository orders, OrderService orderService) {
        this.orders = orders;
        this.orderService = orderService;
    }

    public DashboardResponse get(Long tenantId) {
        DateRange allTime = DateRange.of(null, null);
        Map<OrderStatus, StatusTotals> byStatus = new EnumMap<>(OrderStatus.class);
        orders.totalsByStatus(tenantId, 0L, allTime.from(), allTime.toExclusive())
                .forEach(t -> byStatus.put(t.status(), t));

        return new DashboardResponse(
                totals(byStatus, OrderStatus.PAID),
                totals(byStatus, OrderStatus.PENDING),
                totals(byStatus, OrderStatus.RETURNED).orders(),
                totals(byStatus, OrderStatus.CANCELLED).orders(),
                orderService.list(tenantId, null, null, allTime, 0, RECENT_ORDERS).content());
    }

    /** Totals for one status out of a status->totals map; zeros when that status has no orders. */
    public static Totals totals(Map<OrderStatus, StatusTotals> byStatus, OrderStatus status) {
        StatusTotals t = byStatus.getOrDefault(status, new StatusTotals(status, 0, null, null));
        return Totals.of(t);
    }
}
