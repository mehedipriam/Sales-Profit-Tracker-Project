package com.salestracker.agent;

import com.salestracker.expense.ExpenseType;
import com.salestracker.stock.StockReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The money and stock changes the assistant can show for Confirm. */
public final class LedgerReplies {
    private LedgerReplies() {}

    /** An expense to record. orderId, orderNo and customerName are set when it is tied to an order. */
    public record ExpenseCard(ExpenseType type, BigDecimal amount, LocalDate date, String description, Long orderId,
                              String orderNo, String customerName, List<String> warnings) {}

    /** A stock change: change is signed, and before and after are the stock as it is now and as it will be. */
    public record StockCard(Long productId, String productName, int change, StockReason reason, int before, int after) {}
}
