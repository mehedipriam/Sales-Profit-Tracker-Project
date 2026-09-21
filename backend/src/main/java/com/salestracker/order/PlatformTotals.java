package com.salestracker.order;

import java.math.BigDecimal;

/** Order count plus summed revenue and cost for one platform, computed in SQL. */
public record PlatformTotals(Long platformId, long orders, BigDecimal revenue, BigDecimal cost) {
    public PlatformTotals {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
        cost = cost == null ? BigDecimal.ZERO : cost;
    }
}
