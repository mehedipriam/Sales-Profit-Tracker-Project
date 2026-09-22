package com.salestracker.order;

import java.math.BigDecimal;

/** Delivery charges customers paid on one platform's orders, summed in SQL. */
public record PlatformDelivery(Long platformId, BigDecimal total) {
    public PlatformDelivery {
        total = total == null ? BigDecimal.ZERO : total;
    }
}
