package com.salestracker.activity;

import com.salestracker.order.OrderItem;
import com.salestracker.order.OrderStatus;
import com.salestracker.order.SaleOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * An order's editable parts as they were before an edit, so the log can say which parts the edit changed.
 * Money is kept as plain strings without trailing zeros so 60 and 60.00 count as the same amount.
 */
public record OrderSnapshot(Long platformId, Long customerId, OrderStatus status, Map<Long, String> items,
                            String deliveryCharge, List<Object> courier, LocalDateTime orderedAt, String notes,
                            BigDecimal total) {

    public static OrderSnapshot of(SaleOrder o) {
        Map<Long, String> items = new HashMap<>();
        for (OrderItem i : o.getItems()) items.put(i.getProductId(), i.getQuantity() + " x " + plain(i.getSoldPrice()));
        return new OrderSnapshot(o.getPlatformId(), o.getCustomerId(), o.getStatus(), items,
                plain(o.getDeliveryCharge()),
                Arrays.asList(o.getCourierId(), o.getConsignmentId(), o.getTrackingNumber(), plain(o.getCodAmount())),
                o.getOrderedAt(), o.getNotes(), total(o));
    }

    /** The parts that differ from this snapshot in the order as it is now, in a fixed order. */
    public List<OrderChange> changesTo(SaleOrder now) {
        OrderSnapshot after = of(now);
        List<OrderChange> changes = new ArrayList<>();
        if (!Objects.equals(platformId, after.platformId)) changes.add(OrderChange.PLATFORM);
        if (!Objects.equals(customerId, after.customerId)) changes.add(OrderChange.CUSTOMER);
        if (status != after.status) changes.add(OrderChange.STATUS);
        if (!items.equals(after.items)) changes.add(OrderChange.ITEMS);
        if (!Objects.equals(deliveryCharge, after.deliveryCharge)) changes.add(OrderChange.DELIVERY_CHARGE);
        if (!courier.equals(after.courier)) changes.add(OrderChange.COURIER);
        if (!Objects.equals(orderedAt, after.orderedAt)) changes.add(OrderChange.DATE);
        if (!Objects.equals(notes, after.notes)) changes.add(OrderChange.NOTES);
        return changes;
    }

    /** What the customer pays for the order: the products plus the delivery charge. */
    public static BigDecimal total(SaleOrder o) {
        return o.getItems().stream().map(OrderItem::lineRevenue).reduce(o.getDeliveryCharge(), BigDecimal::add);
    }

    private static String plain(BigDecimal n) {
        return n == null ? null : n.stripTrailingZeros().toPlainString();
    }
}
