package com.salestracker.expense;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Expenses dated on one day, summed in SQL. */
public record DayExpenses(LocalDate day, BigDecimal total) {
    public DayExpenses {
        total = total == null ? BigDecimal.ZERO : total;
    }
}
