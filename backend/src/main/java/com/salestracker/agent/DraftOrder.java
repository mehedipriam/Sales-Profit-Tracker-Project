package com.salestracker.agent;

import com.salestracker.order.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * An order the agent has prepared but not saved. Everything in it is a suggestion from the AI checked against the
 * shop's own data; a person confirms it (possibly after fixing the issues) before anything is created.
 */
public record DraftOrder(DraftCustomer customer, Long platformId, String platformName, OrderStatus status,
                         BigDecimal deliveryCharge, String notes, List<Line> items, List<Issue> issues,
                         boolean canConfirm) {

    /** id is set for a customer already in the shop's list; isNew means confirming will create them. */
    public record DraftCustomer(Long id, String name, String phone, String address, boolean isNew) {}

    public enum MatchStatus { MATCHED, AMBIGUOUS, NOT_FOUND }

    /** A product that might be the one meant, with what the chat needs to use it as a line. */
    public record Candidate(Long id, String name, BigDecimal sellingPrice, Integer stockQty) {}

    /**
     * text is what the message said; productId is null until the product is matched. catalogPrice is the product's
     * own selling price, shown when the customer's price differs; stockQty is null when stock is not tracked.
     */
    public record Line(String text, Long productId, String productName, int quantity, BigDecimal soldPrice,
                       BigDecimal catalogPrice, Integer stockQty, MatchStatus match, List<Candidate> candidates) {}

    public enum Level { ERROR, WARNING }

    /** An ERROR blocks confirming until fixed; a WARNING is only worth a look. */
    public record Issue(Level level, String message) {}
}
