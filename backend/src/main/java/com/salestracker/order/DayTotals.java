package com.salestracker.order;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Order count plus summed revenue and cost for one calendar day, computed in SQL. */
public record DayTotals(LocalDate day, long orders, BigDecimal revenue, BigDecimal cost) {
    public DayTotals {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
        cost = cost == null ? BigDecimal.ZERO : cost;
    }
}
