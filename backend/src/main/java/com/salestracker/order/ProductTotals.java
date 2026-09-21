package com.salestracker.order;

import java.math.BigDecimal;

/** Units sold plus summed revenue and cost for one product, computed in SQL. */
public record ProductTotals(Long productId, long quantity, BigDecimal revenue, BigDecimal cost) {
    public ProductTotals {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
        cost = cost == null ? BigDecimal.ZERO : cost;
    }
}
