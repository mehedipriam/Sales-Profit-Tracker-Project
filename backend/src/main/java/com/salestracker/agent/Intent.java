package com.salestracker.agent;

import java.time.LocalDate;

/**
 * What a typed message is asking for, as worked out by the AI. It only chooses the kind of request and the period;
 * the figures are always calculated by the app. Anything the AI could not tell stays null.
 */
public record Intent(Kind kind, Question question, PeriodName period, LocalDate from, LocalDate to,
                     String customerName, String reply, String phone, String address, String platform, String newName) {

    /** The customer fields apply to CUSTOMER_* kinds only; this is for everything else. */
    public Intent(Kind kind, Question question, PeriodName period, LocalDate from, LocalDate to, String customerName, String reply) {
        this(kind, question, period, from, to, customerName, reply, null, null, null, null);
    }

    /**
     * ORDER: a pasted order message. QUESTION: asks about the business. CUSTOMER_ADD, CUSTOMER_FIND and CUSTOMER_UPDATE:
     * adding, looking up or changing a saved customer (customerName is who; phone, address, platform and newName are
     * the details given). UNCLEAR: none of these, or not supported yet.
     */
    public enum Kind { ORDER, QUESTION, CUSTOMER_ADD, CUSTOMER_FIND, CUSTOMER_UPDATE, UNCLEAR }

    public enum Question {
        PROFIT, TOP_PRODUCTS, LOWEST_MARGIN, TOP_CUSTOMERS, CUSTOMER_SPEND, LOW_STOCK, ORDER_COUNTS, EXPENSES, PLATFORMS;

        /** Profit, cost, revenue and spending: Owner and Admin only, like the Dashboard and Reports pages. */
        public boolean needsFinancials() { return this != LOW_STOCK && this != ORDER_COUNTS; }
    }

    public enum PeriodName { TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, ALL_TIME, CUSTOM }
}
