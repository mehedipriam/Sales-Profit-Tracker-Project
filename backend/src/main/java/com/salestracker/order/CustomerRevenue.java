package com.salestracker.order;

import java.math.BigDecimal;

/** Product revenue from one customer's orders, computed in SQL. */
public record CustomerRevenue(Long customerId, BigDecimal revenue) {
    public CustomerRevenue {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
    }
}
