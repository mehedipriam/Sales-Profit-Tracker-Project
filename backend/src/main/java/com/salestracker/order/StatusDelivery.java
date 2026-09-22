package com.salestracker.order;

import java.math.BigDecimal;

/** Delivery charges customers paid on orders of one status, summed in SQL. */
public record StatusDelivery(OrderStatus status, BigDecimal total) {
    public StatusDelivery {
        total = total == null ? BigDecimal.ZERO : total;
    }
}
