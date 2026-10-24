package com.salestracker.order;

import com.salestracker.expense.ExpenseDtos.ExpenseLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    public record NewCustomer(@NotBlank @Size(max = 150) String name,
                              @Size(max = 32) String phone,
                              @Size(max = 500) String address) {}

    /** costPrice is optional: this sale's cost for the product when it differs from the product's current cost (Owner/Admin). */
    public record ItemRequest(@NotNull Long productId,
                              @Min(1) @Max(1_000_000) int quantity,
                              @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal soldPrice,
                              @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice) {}

    /**
     * Provide either customerId (existing) or newCustomer (quick-add), not both.
     * deliveryCharge is what the customer paid for delivery on top of the products; omitted means 0.
     * courierId is optional (no courier: delivered by hand, picked up...). consignmentId is the courier's parcel ID;
     * trackingNumber is a tracking code or the
     * courier's full tracking link. codAmount is the cash the courier collects;
     * omitted means everything the customer pays (products + delivery charge), 0 means paid in advance.
     */
    public record OrderRequest(@NotNull Long platformId,
                               Long customerId,
                               @Valid NewCustomer newCustomer,
                               @NotNull OrderStatus status,
                               @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal deliveryCharge,
                               Long courierId,
                               @Size(max = 64) String consignmentId,
                               @Size(max = 300) String trackingNumber,
                               @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal codAmount,
                               @Pattern(regexp = "INSIDE|OUTSIDE") String deliveryZone,
                               @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal courierFee,
                               @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal courierEarning,
                               LocalDateTime orderedAt,
                               @Size(max = 5000) String notes,
                               @NotEmpty @Size(max = 100) List<@Valid ItemRequest> items) {}

    public record StatusRequest(@NotNull OrderStatus status) {}

    /**
     * How an order travels: the courier, its tracking number and link, the cash it collects, and the day it paid
     * that cash out (null while the courier still holds it). Null on orders without a courier.
     */
    public record CourierInfo(Long courierId, String courierName, String consignmentId, String trackingNumber,
                              String trackingLink,
                              BigDecimal codAmount, LocalDate paidOn,
                              String deliveryZone, BigDecimal deliveryFee, BigDecimal codFee, BigDecimal youReceive,
                              BigDecimal earning) {
        /** The courier's own charges are a cost of the business, so Staff doesn't see them. */
        public CourierInfo hideCharges() {
            return new CourierInfo(courierId, courierName, consignmentId, trackingNumber, trackingLink, codAmount, paidOn,
                    deliveryZone, null, null, null, null);
        }
    }

    public record ItemResponse(Long productId, String productName, int quantity, BigDecimal costPrice,
                               BigDecimal soldPrice, BigDecimal lineRevenue, BigDecimal lineCost,
                               BigDecimal lineProfit) {
        /** Phase 7b: Staff sees what was sold and for how much, not the cost/profit behind it. */
        ItemResponse hideFinancials() {
            return new ItemResponse(productId, productName, quantity, null, soldPrice, lineRevenue, null, null);
        }
    }

    public record OrderDetail(Long id, String orderNo, LocalDateTime orderedAt, Long platformId, String platformName,
                              Long customerId, String customerName, String customerPhone, String customerAddress,
                              OrderStatus status,
                              BigDecimal commissionPct, BigDecimal deliveryCharge, CourierInfo courier, String notes,
                              List<ItemResponse> items,
                              BigDecimal revenue, BigDecimal cost, BigDecimal profit,
                              List<ExpenseLine> expenses) {
        /** Phase 7b: same boundary as the dashboard/reports - Staff records the sale, not its margin. */
        public OrderDetail hideFinancials() {
            return new OrderDetail(id, orderNo, orderedAt, platformId, platformName, customerId, customerName, customerPhone,
                    customerAddress, status, null, deliveryCharge, courier == null ? null : courier.hideCharges(), notes, items.stream().map(ItemResponse::hideFinancials).toList(),
                    revenue, null, null, List.of());
        }
    }

    public record OrderSummary(Long id, String orderNo, LocalDateTime orderedAt, String platformName, String customerName,
                               OrderStatus status, int itemCount, CourierInfo courier,
                               BigDecimal revenue, BigDecimal deliveryCharge, BigDecimal cost, BigDecimal profit) {
        public OrderSummary hideFinancials() {
            return new OrderSummary(id, orderNo, orderedAt, platformName, customerName, status, itemCount,
                    courier == null ? null : courier.hideCharges(), revenue,
                    deliveryCharge, null, null);
        }
    }

    /** How many orders, and how much revenue, one status holds within the current filters. */
    public record StatusCount(OrderStatus status, long orders, BigDecimal revenue) {}
}
