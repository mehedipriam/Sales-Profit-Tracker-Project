package com.salestracker.agent;

import com.salestracker.agent.OrderReplies.StatusChange;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Changing an order's status from the chat. The message only finds the order and the new status and shows a card with
 * what will happen; Confirm then uses the app's own status change, so stock, commission and the Activity log update
 * exactly as they do on the Orders page.
 */
@Service
public class OrderStatusAssistant {
    /** ORD-2610-0005, also written ord2610-5. */
    private static final Pattern FULL_NUMBER = Pattern.compile("(?i)^#?\\s*ORD\\s*-?\\s*(\\d{4})\\s*-?\\s*(\\d{1,6})$");
    /** "4" or "#4": the order's yearly count, in the current year. */
    private static final Pattern SHORT_NUMBER = Pattern.compile("^#?\\s*(\\d{1,6})$");

    private final SaleOrderRepository orders;
    private final OrderService orderService;
    private final CustomerMatcher matcher;

    public OrderStatusAssistant(SaleOrderRepository orders, OrderService orderService, CustomerMatcher matcher) {
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
            Optional<SaleOrder> found = byNumber(tenant, in.orderRef().trim());
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

    /** The order a number means: the full number, or just its count (in the current year). */
    private Optional<SaleOrder> byNumber(Long tenant, String ref) {
        Matcher full = FULL_NUMBER.matcher(ref);
        if (full.matches()) {
            return orders.findByTenantIdAndOrderNo(tenant, "ORD-" + full.group(1) + "-" + pad(full.group(2)));
        }
        Matcher count = SHORT_NUMBER.matcher(ref);
        if (count.matches()) {
            String yy = String.format("%02d", LocalDate.now().getYear() % 100);
            return orders.findByTenantIdAndOrderNoLike(tenant, "ORD-" + yy + "__-" + pad(count.group(1))).stream().findFirst();
        }
        return orders.findByTenantIdAndOrderNo(tenant, ref.toUpperCase());
    }

    private static String pad(String digits) {
        return String.format("%04d", Integer.parseInt(digits));
    }

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
