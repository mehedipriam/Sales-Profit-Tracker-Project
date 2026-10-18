package com.salestracker.agent;

import com.salestracker.order.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the AI understood from "make it 3" or "the address is Dhanmondi 5": only the things to change, with null for
 * everything left alone. It never edits the draft itself; DraftService applies these and checks the result.
 * reply is a short sentence for the user, in the language they wrote in (also used to ask for a missing detail).
 */
public record DraftRevision(String reply, String customerName, String customerPhone, String customerAddress,
                            BigDecimal deliveryCharge, String notes, OrderStatus status, String platform,
                            List<Change> changes) {

    public enum Action { SET_QUANTITY, SET_PRICE, REMOVE, ADD, REPLACE_PRODUCT }

    /** line is the 1-based number shown in the draft (not used by ADD); product names the product for ADD and REPLACE_PRODUCT. */
    public record Change(Action action, Integer line, String product, Integer quantity, BigDecimal price) {}

    public DraftRevision {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
