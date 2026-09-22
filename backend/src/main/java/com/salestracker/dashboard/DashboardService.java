package com.salestracker.dashboard;

import com.salestracker.common.DateRange;
import com.salestracker.order.OrderDtos.OrderSummary;
import com.salestracker.expense.ExpenseRepository;
import com.salestracker.expense.ExpenseTypeTotals;
import com.salestracker.order.OrderService;
import com.salestracker.order.OrderStatus;
import com.salestracker.order.SaleOrderRepository;
import com.salestracker.order.StatusDelivery;
import com.salestracker.order.StatusTotals;
import com.salestracker.product.Product;
import com.salestracker.product.ProductRepository;
import org.springframework.data.domain.PageRequest;
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
    private static final int LOW_STOCK_LIMIT = 20;

    /**
     * revenue, cost and profit cover the products; delivery is what customers paid for delivery on top, kept apart
     * so product margins stay honest. It is income that offsets delivery expenses in net profit.
     */
    public record Totals(long orders, BigDecimal revenue, BigDecimal cost, BigDecimal profit, BigDecimal delivery) {
        static Totals of(StatusTotals t, BigDecimal delivery) {
            return new Totals(t.orders(), t.revenue(), t.cost(), t.revenue().subtract(t.cost()), delivery);
        }
    }

    /**
     * realized = PAID orders; pending = PENDING orders (expected, e.g. cash on delivery);
     * RETURNED and CANCELLED orders are excluded from money totals and only counted.
     */
    public record DashboardResponse(Totals realized, Totals pending, long returnedOrders, long cancelledOrders,
                                    BigDecimal expenses, BigDecimal netProfit,
                                    List<OrderSummary> recentOrders, List<LowStockItem> lowStock) {}

    /** A product whose stock has fallen to or below its own low-stock threshold. */
    public record LowStockItem(Long productId, String name, int stockQty, int threshold) {
        static LowStockItem of(Product p) {
            return new LowStockItem(p.getId(), p.getName(), p.getStockQty(), p.getLowStockThreshold());
        }
    }

    private final SaleOrderRepository orders;
    private final OrderService orderService;
    private final ProductRepository products;
    private final ExpenseRepository expenseRepository;

    public DashboardService(SaleOrderRepository orders, OrderService orderService, ProductRepository products,
                            ExpenseRepository expenseRepository) {
        this.orders = orders;
        this.orderService = orderService;
        this.products = products;
        this.expenseRepository = expenseRepository;
    }

    public DashboardResponse get(Long tenantId) {
        DateRange allTime = DateRange.of(null, null);
        Map<OrderStatus, StatusTotals> byStatus = new EnumMap<>(OrderStatus.class);
        orders.totalsByStatus(tenantId, 0L, allTime.from(), allTime.toExclusive())
                .forEach(t -> byStatus.put(t.status(), t));
        Map<OrderStatus, BigDecimal> delivery = deliveryByStatus(
                orders.deliveryByStatus(tenantId, 0L, allTime.from(), allTime.toExclusive()));

        // Expenses that reduce realized profit: all of them except those tied to a still-pending order.
        BigDecimal expenses = expenseRepository.realizedByType(tenantId, 0L,
                        allTime.from().toLocalDate(), allTime.toExclusive().toLocalDate()).stream()
                .map(ExpenseTypeTotals::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        Totals realized = totals(byStatus, delivery, OrderStatus.PAID);

        return new DashboardResponse(
                realized,
                totals(byStatus, delivery, OrderStatus.PENDING),
                totals(byStatus, delivery, OrderStatus.RETURNED).orders(),
                totals(byStatus, delivery, OrderStatus.CANCELLED).orders(),
                expenses, netProfit(realized, expenses),
                orderService.list(tenantId, null, null, allTime, 0, RECENT_ORDERS).content(),
                products.lowStock(tenantId, PageRequest.of(0, LOW_STOCK_LIMIT)).stream().map(LowStockItem::of).toList());
    }

    /** Totals for one status out of the status->totals and status->delivery maps; zeros when it has no orders. */
    public static Totals totals(Map<OrderStatus, StatusTotals> byStatus, Map<OrderStatus, BigDecimal> delivery,
                                OrderStatus status) {
        StatusTotals t = byStatus.getOrDefault(status, new StatusTotals(status, 0, null, null));
        return Totals.of(t, delivery.getOrDefault(status, BigDecimal.ZERO));
    }

    public static Map<OrderStatus, BigDecimal> deliveryByStatus(List<StatusDelivery> rows) {
        Map<OrderStatus, BigDecimal> m = new EnumMap<>(OrderStatus.class);
        rows.forEach(r -> m.put(r.status(), r.total()));
        return m;
    }

    /** Product profit plus the delivery customers paid, less every realized expense (delivery costs included). */
    public static BigDecimal netProfit(Totals realized, BigDecimal expenses) {
        return realized.profit().add(realized.delivery()).subtract(expenses);
    }
}
