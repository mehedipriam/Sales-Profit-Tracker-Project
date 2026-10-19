package com.salestracker.agent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** The customer-related things the assistant can show: a customer's details, and a change waiting for Confirm. */
public final class CustomerReplies {
    private CustomerReplies() {}

    /** A saved customer. spent is only filled for Owner and Admin; null otherwise. */
    public record CustomerView(Long id, String name, String phone, String address, String platform, long orders,
                               LocalDateTime lastOrderAt, BigDecimal spent) {}

    public enum Kind { ADD, UPDATE }

    /** One field of a proposal. before is null for a new customer; after is what Confirm will save. */
    public record Field(String key, String label, String before, String after) {}

    /**
     * A customer to add, or the fields of a saved one to change. Nothing is saved until Confirm. platformId is the
     * source platform to save with it (null: none, or unchanged unless "platform" is among the fields).
     */
    public record Proposal(Kind kind, Long customerId, String customerName, List<Field> fields, Long platformId,
                           List<String> warnings) {}
}
