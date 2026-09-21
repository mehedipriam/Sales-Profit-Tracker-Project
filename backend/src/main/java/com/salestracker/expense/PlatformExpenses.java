package com.salestracker.expense;

import java.math.BigDecimal;

/** Expenses linked to orders of one platform, summed in SQL. */
public record PlatformExpenses(Long platformId, BigDecimal total) {
    public PlatformExpenses {
        total = total == null ? BigDecimal.ZERO : total;
    }
}
