package com.salestracker.agent;

import java.math.BigDecimal;
import java.util.List;

/** What the AI read out of a pasted order message. Every field is optional: anything it could not find stays null. */
public record ExtractedOrder(
        String customerName,
        String phone,
        String address,
        String platform,
        BigDecimal deliveryCharge,
        String notes,
        List<Item> items) {

    public record Item(String name, Integer quantity, BigDecimal price) {}

    public ExtractedOrder {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
