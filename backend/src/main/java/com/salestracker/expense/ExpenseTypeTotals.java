package com.salestracker.expense;

import java.math.BigDecimal;

/** Count and summed amount for one expense type, computed in SQL. */
public record ExpenseTypeTotals(ExpenseType type, long count, BigDecimal total) {
    public ExpenseTypeTotals {
        total = total == null ? BigDecimal.ZERO : total;
    }
}
