package com.salestracker.order;

import java.math.BigDecimal;

/** Order count plus summed revenue and cost for one status, computed in SQL. */
public record StatusTotals(OrderStatus status, long orders, BigDecimal revenue, BigDecimal cost) {
    public StatusTotals {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
        cost = cost == null ? BigDecimal.ZERO : cost;
    }
}
