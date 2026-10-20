package com.salestracker.agent;

import com.salestracker.expense.ExpenseType;
import com.salestracker.order.OrderStatus;
import com.salestracker.stock.StockReason;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a typed message is asking for, as worked out by the AI. It only chooses the kind of request and the details
 * given; the figures are always calculated by the app. Anything the AI could not tell stays null.
 */
public record Intent(Kind kind, Question question, PeriodName period, LocalDate from, LocalDate to,
                     String customerName, String reply, String phone, String address, String platform, String newName,
                     String orderRef, OrderStatus status, ExpenseDetails expense, StockDetails stock) {

    /** An expense to record. date and orderRef are null when the message did not say. */
    public record ExpenseDetails(ExpenseType type, BigDecimal amount, LocalDate date, String description, String orderRef) {}

    /**
     * A stock change. quantity is a count of units (above zero) for a restock or damage; newStock is "the stock is
     * really N" for a correction, which the app turns into a change.
     */
    public record StockDetails(String product, Integer quantity, Integer newStock, StockReason reason) {}

    /** The customer fields apply to CUSTOMER_* kinds only; this is for everything else. */
    public Intent(Kind kind, Question question, PeriodName period, LocalDate from, LocalDate to, String customerName, String reply) {
        this(kind, question, period, from, to, customerName, reply, null, null, null, null, null, null, null, null);
    }

    public Intent(Kind kind, Question question, PeriodName period, LocalDate from, LocalDate to, String customerName,
                  String reply, String phone, String address, String platform, String newName) {
        this(kind, question, period, from, to, customerName, reply, phone, address, platform, newName, null, null, null, null);
    }

    /** The order fields apply to ORDER_STATUS only. */
    public Intent(Kind kind, Question question, PeriodName period, LocalDate from, LocalDate to, String customerName,
                  String reply, String phone, String address, String platform, String newName, String orderRef, OrderStatus status) {
        this(kind, question, period, from, to, customerName, reply, phone, address, platform, newName, orderRef, status, null, null);
    }

    /**
     * ORDER: a pasted order message. QUESTION: asks about the business. CUSTOMER_ADD, CUSTOMER_FIND and CUSTOMER_UPDATE:
     * adding, looking up or changing a saved customer (customerName is who; phone, address, platform and newName are
     * the details given). ORDER_STATUS: change an order's status (orderRef is the order as written, or customerName for
     * "the last order of ..."; status is the new one). ORDER_FIND: look at an order's status, or a customer's orders,
     * without changing anything. EXPENSE_ADD: record an expense. STOCK_ADJUST: restock, damaged
     * goods or a count correction. UNCLEAR: none of these, or not supported.
     */
    public enum Kind {
        ORDER, QUESTION, CUSTOMER_ADD, CUSTOMER_FIND, CUSTOMER_UPDATE, ORDER_STATUS, ORDER_FIND, EXPENSE_ADD, STOCK_ADJUST, UNCLEAR
    }

    public enum Question {
        PROFIT, TOP_PRODUCTS, LOWEST_MARGIN, TOP_CUSTOMERS, CUSTOMER_SPEND, LOW_STOCK, ORDER_COUNTS, EXPENSES, PLATFORMS;

        /** Profit, cost, revenue and spending: Owner and Admin only, like the Dashboard and Reports pages. */
        public boolean needsFinancials() { return this != LOW_STOCK && this != ORDER_COUNTS; }
    }

    public enum PeriodName { TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, ALL_TIME, CUSTOM }
}
