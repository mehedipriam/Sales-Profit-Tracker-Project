package com.salestracker.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One customer's order count, delivery charges and latest order date for one status, computed in SQL. */
public record CustomerStatusTotals(Long customerId, OrderStatus status, long orders, BigDecimal delivery,
                                   LocalDateTime lastOrderedAt) {
    public CustomerStatusTotals {
        delivery = delivery == null ? BigDecimal.ZERO : delivery;
    }
}
