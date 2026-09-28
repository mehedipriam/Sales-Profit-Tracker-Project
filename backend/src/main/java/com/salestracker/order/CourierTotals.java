package com.salestracker.order;

import java.math.BigDecimal;

/** Orders and cash-on-delivery money for one courier in one status, summed in SQL. */
public record CourierTotals(Long courierId, OrderStatus status, long orders, BigDecimal amount) {
    public CourierTotals {
        amount = amount == null ? BigDecimal.ZERO : amount;
    }
}
