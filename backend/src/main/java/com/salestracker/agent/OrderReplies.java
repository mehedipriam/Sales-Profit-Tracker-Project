package com.salestracker.agent;

import com.salestracker.order.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

/** The order-related things the assistant can show. */
public final class OrderReplies {
    private OrderReplies() {}

    /**
     * An order's status change waiting for Confirm: what the order is, "from to to", and in a sentence what it does to
     * stock and profit. total is what the customer pays (products and delivery).
     */
    public record StatusChange(Long orderId, String orderNo, String customerName, List<String> items, BigDecimal total,
                               OrderStatus from, OrderStatus to, String effect) {}
}
