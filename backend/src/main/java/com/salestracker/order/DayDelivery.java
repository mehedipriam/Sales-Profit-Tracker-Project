package com.salestracker.order;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Delivery charges customers paid on one day's orders, summed in SQL. */
public record DayDelivery(LocalDate day, BigDecimal total) {
    public DayDelivery {
        total = total == null ? BigDecimal.ZERO : total;
    }
}
