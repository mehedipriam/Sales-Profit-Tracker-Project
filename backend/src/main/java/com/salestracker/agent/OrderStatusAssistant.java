package com.salestracker.agent;

import com.salestracker.agent.Answer.Column;
import com.salestracker.agent.Answer.Kind;
import com.salestracker.agent.Answer.Stat;
import com.salestracker.agent.Answer.Table;
import com.salestracker.agent.Answer.Tone;
import com.salestracker.agent.OrderReplies.StatusChange;
import com.salestracker.common.DateRange;
import com.salestracker.common.PageResponse;
import com.salestracker.customer.CustomerDtos.Insights;
import com.salestracker.customer.CustomerService;
import com.salestracker.order.OrderDtos.OrderSummary;
import java.math.BigDecimal;
import com.salestracker.auth.ApiException;
import com.salestracker.auth.AuthUser;
import com.salestracker.customer.Customer;
import com.salestracker.order.OrderDtos.OrderDetail;
import com.salestracker.order.OrderService;
import com.salestracker.order.OrderStatus;
import com.salestracker.order.SaleOrder;
import com.salestracker.order.SaleOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Changing an order's status from the chat. The message only finds the order and the new status and shows a card with
 * what will happen; Confirm then uses the app's own status change, so stock, commission and the Activity log update
 * exactly as they do on the Orders page.
 */
@Service
public class OrderStatusAssistant {
    private final SaleOrderRepository orders;
    private final OrderService orderService;
    private final CustomerMatcher matcher;
    private final OrderLookup lookup;
    private final CustomerService customerService;

    public OrderStatusAssistant(SaleOrderRepository orders, OrderService orderService, CustomerMatcher matcher,
                                OrderLookup lookup, CustomerService customerService) {
        this.customerService = customerService;
        this.lookup = lookup;
        this.orders = orders;
        this.orderService = orderService;
        this.matcher = matcher;
    }

    @Transactional(readOnly = true)
    public AgentReply handle(AuthUser user, Intent in) {
        if (in.status() == null) {
            return AgentReply.message("What should the order's status become: paid, pending, returned or cancelled?");
        }
        Long tenant = user.tenantId();
        SaleOrder order;
        if (in.orderRef() != null && !in.orderRef().isBlank()) {
            Optional<SaleOrder> found = lookup.byNumber(tenant, in.orderRef().trim());
            if (found.isEmpty()) return AgentReply.message("No order matches \"" + in.orderRef().trim() + "\".");
            order = found.get();
        } else if (in.customerName() != null && !in.customerName().isBlank()) {
            List<Customer> who = matcher.find(tenant, in.customerName(), null);
            if (who.isEmpty()) return AgentReply.message("No customer matches \"" + in.customerName().trim() + "\".");
            if (who.size() > 1) return AgentReply.message(CustomerMatcher.which(in.customerName(), who));
            Optional<SaleOrder> last = orders.findFirstByTenantIdAndCustomerIdOrderByOrderedAtDescIdDesc(tenant, who.get(0).getId());
            if (last.isEmpty()) return AgentReply.message(who.get(0).getName() + " has no orders.");
            order = last.get();
        } else {
            return AgentReply.message("Which order? Tell me its number, like ORD-2610-0005, or the customer's name for their last order.");
        }

        if (order.getStatus() == in.status()) {
            return AgentReply.message(order.getOrderNo() + " is already " + label(order.getStatus()) + ". Nothing to do.");
        }
        OrderDetail d = orderService.get(tenant, order.getId());
        List<String> items = d.items().stream().map(i -> i.productName() + " x " + i.quantity()).toList();
        return AgentReply.status(new StatusChange(d.id(), d.orderNo(), d.customerName(), items,
                d.revenue().add(d.deliveryCharge()), d.status(), in.status(), effect(d.status(), in.status())));
    }

    /** Read-only: the status of one order, or a customer's latest orders and how they stand. Nothing changes. */
    @Transactional(readOnly = true)
    public AgentReply find(AuthUser user, Intent in) {
        Long tenant = user.tenantId();
        if (in.orderRef() != null && !in.orderRef().isBlank()) {
            Optional<SaleOrder> found = lookup.byNumber(tenant, in.orderRef().trim());
            if (found.isEmpty()) return AgentReply.message("No order matches \"" + in.orderRef().trim() + "\".");
            return AgentReply.answer(oneOrder(orderService.get(tenant, found.get().getId())));
        }
        if (in.customerName() == null || in.customerName().isBlank()) {
            // "Show me the last order": the newest order of the shop.
            List<OrderSummary> latest = orderService.list(tenant, null, null, null, null, DateRange.of(null, null), 0, 1).content();
            if (latest.isEmpty()) return AgentReply.message("There are no orders yet.");
            return AgentReply.answer(oneOrder(orderService.get(tenant, latest.get(0).id())));
        }
        List<Customer> who = matcher.find(tenant, in.customerName(), null);
        if (who.isEmpty()) return AgentReply.message("No customer matches \"" + in.customerName().trim() + "\".");
        if (who.size() > 1) return AgentReply.message(CustomerMatcher.which(in.customerName(), who));
        Customer c = who.get(0);
        PageResponse<OrderSummary> page = orderService.list(tenant, null, c.getId(), null, null, DateRange.of(null, null), 0, SHOWN);
        if (page.content().isEmpty()) return AgentReply.message(c.getName() + " has no orders.");
        Insights i = customerService.get(tenant, c.getId()).insights();
        return AgentReply.answer(ordersOf(c.getName(), page, i));
    }

    private static final int SHOWN = 8;

    private static Answer oneOrder(OrderDetail d) {
        Tone tone = d.status() == OrderStatus.PAID ? Tone.GOOD : live(d.status()) ? Tone.NEUTRAL : Tone.BAD;
        BigDecimal total = d.revenue().add(d.deliveryCharge());
        Table items = new Table(List.of(new Column("Product", Kind.TEXT), new Column("Qty", Kind.COUNT), new Column("Price", Kind.MONEY)),
                d.items().stream().map(x -> List.<Object>of(x.productName(), x.quantity(), x.soldPrice())).toList());
        return new Answer(d.orderNo(), d.customerName(),
                List.of(new Stat("Status", cap(label(d.status())), Kind.TEXT, tone), new Stat("Customer pays", total, Kind.MONEY, Tone.NEUTRAL)),
                items, d.orderNo() + " for " + d.customerName() + " is " + label(d.status()) + ", total " + total.stripTrailingZeros().toPlainString() + ".");
    }

    private static Answer ordersOf(String name, PageResponse<OrderSummary> page, Insights i) {
        Table table = new Table(List.of(new Column("Order", Kind.TEXT), new Column("Date", Kind.TEXT),
                        new Column("Status", Kind.TEXT), new Column("Total", Kind.MONEY)),
                page.content().stream().map(o -> List.<Object>of(o.orderNo(), o.orderedAt().toLocalDate().toString(),
                        cap(label(o.status())), o.revenue().add(o.deliveryCharge()))).toList());
        List<Stat> stats = List.of(Stat.count("Paid", i.paid()), Stat.count("Pending", i.pending()),
                new Stat("Returned", i.returned(), Kind.COUNT, i.returned() > 0 ? Tone.BAD : Tone.NEUTRAL),
                new Stat("Cancelled", i.cancelled(), Kind.COUNT, i.cancelled() > 0 ? Tone.BAD : Tone.NEUTRAL));
        OrderSummary latest = page.content().get(0);
        String more = page.totalElements() > SHOWN ? " Showing the latest " + SHOWN + "." : "";
        return new Answer(name + "'s orders", "Latest first", stats, table, name + " has " + i.orders() + " order"
                + (i.orders() == 1 ? "" : "s") + ": " + i.paid() + " paid, " + i.pending() + " pending, " + i.returned()
                + " returned and " + i.cancelled() + " cancelled. The latest, " + latest.orderNo() + ", is " + label(latest.status()) + "." + more);
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Confirm: changes the status unless the order has moved on since the card was shown. */
    public OrderDetail confirm(AuthUser user, Long id, OrderStatus from, OrderStatus to) {
        SaleOrder order = orders.findByIdAndTenantId(id, user.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getStatus() != from) {
            throw new ApiException(HttpStatus.CONFLICT, order.getOrderNo() + " is now " + label(order.getStatus())
                    + ", not " + label(from) + ". Ask again to see the current state");
        }
        if (from == to) throw new ApiException(HttpStatus.BAD_REQUEST, order.getOrderNo() + " is already " + label(to));
        return orderService.changeStatus(user, id, to, true);
    }

    // ---- helpers ----

    private static boolean live(OrderStatus s) {
        return s == OrderStatus.PAID || s == OrderStatus.PENDING;
    }

    /** What the change does, in words: stock goes back or out again, and whether it counts as revenue. */
    static String effect(OrderStatus from, OrderStatus to) {
        if (!live(to)) {
            return live(from) ? "The stock is put back and the order is removed from profit."
                              : "The stock stays back and the order stays out of profit.";
        }
        String stock = live(from) ? "" : "The stock is taken out again. ";
        return stock + (to == OrderStatus.PAID ? "The order counts as revenue and profit."
                                               : "The order is expected income, not counted as revenue until it is paid.");
    }

    private static String label(OrderStatus s) {
        return switch (s) {
            case PAID -> "paid";
            case PENDING -> "pending";
            case RETURNED -> "returned";
            case CANCELLED -> "cancelled";
        };
    }
}
