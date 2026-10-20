package com.salestracker.agent;

import com.salestracker.auth.AuthUser;

import java.util.ArrayList;
import java.util.List;

/**
 * Example messages shown under a plain reply (a refusal, a missing detail, "I did not understand"), chosen to fit what
 * the person was trying to do and what their role may do. They are only text to start from: the person edits the
 * example in the input box and sends it.
 */
final class Suggestions {
    private Suggestions() {}

    static List<String> forIntent(Intent in, AuthUser user) {
        boolean money = user.role().seesFinancials();
        Intent.Kind kind = in.kind();
        if (kind == null) return general(money);
        return switch (kind) {
            case CUSTOMER_ADD -> List.of("Add customer Rahim, 01711222333, Mirpur 10, from Facebook", "Add a customer");
            case CUSTOMER_FIND -> List.of("Show me Imdad's phone and address", "Find the customer with phone 01711222333");
            case CUSTOMER_UPDATE -> List.of("Imdad's address is House 12, Road 3, Dhanmondi", "Change Imdad's phone to 01711222333");
            case ORDER_STATUS -> List.of("Mark ORD-2610-0005 as returned", "Cancel Imdad's last order", "Order 4 is paid");
            case ORDER_FIND -> List.of("Show Imdad's orders", "What is the status of ORD-2610-0005?", "Show Imdad's last order");
            case EXPENSE_ADD -> money ? List.of("Facebook ads 500 today", "Packaging cost 120 for ORD-2610-0005", "Delivery 60 yesterday")
                                      : general(false);
            case STOCK_ADJUST -> List.of("Restock 20 beans pudding", "Damaged 2 Cow & Gate", "Stock of Cow & Gate is actually 9");
            case QUESTION -> money ? List.of("This month's profit", "Best-selling products this month", "Top customers this month",
                                             "Expenses this month", "How much did Imdad spend?")
                                   : List.of("How many orders this month?", "What is running low on stock?");
            default -> general(money);
        };
    }

    /** One example of each thing the assistant can do. */
    static List<String> general(boolean money) {
        List<String> out = new ArrayList<>();
        if (money) out.add("This month's profit");
        out.add("What is running low on stock?");
        out.add("Show Imdad's orders");
        out.add("Mark ORD-2610-0005 as returned");
        out.add("Add customer Rahim, 01711222333, Mirpur 10");
        out.add("Show me Imdad's phone and address");
        if (money) out.add("Facebook ads 500 today");
        out.add("Restock 20 beans pudding");
        return out;
    }
}
