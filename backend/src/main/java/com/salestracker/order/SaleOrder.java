package com.salestracker.order;

import com.salestracker.tenant.TenantOwned;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A sale. Named SaleOrder because ORDER is a reserved word in SQL/HQL; the table is "orders". */
@Entity
@Table(name = "orders")
public class SaleOrder implements TenantOwned {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** ORD-YYMM-NNNN: set once when the order is created and never changed. */
    @Column(name = "order_no", nullable = false, updatable = false)
    private String orderNo;

    @Column(name = "platform_id", nullable = false)
    private Long platformId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /** The platform's commission rate when the sale was recorded; later rate changes don't touch this order. */
    @Column(name = "commission_pct", nullable = false)
    private BigDecimal commissionPct = BigDecimal.ZERO;

    /** What the customer paid for delivery on top of the products; income that offsets the delivery expense. */
    @Column(name = "delivery_charge", nullable = false)
    private BigDecimal deliveryCharge = BigDecimal.ZERO;

    /** The courier carrying the order; null when it is delivered some other way (by hand, pickup...). */
    @Column(name = "courier_id")
    private Long courierId;

    /** The courier's parcel / consignment ID for this order. */
    @Column(name = "consignment_id")
    private String consignmentId;

    @Column(name = "tracking_number")
    private String trackingNumber;

    /** Cash the courier collects from the customer on delivery (0 when the customer paid in advance). */
    @Column(name = "cod_amount")
    private BigDecimal codAmount;

    /** The day the courier paid that cash out to the business; null while the courier still holds it. */
    @Column(name = "courier_paid_on")
    private LocalDate courierPaidOn;

    /** INSIDE or OUTSIDE Dhaka, which decides the courier's delivery fee; null without a courier. */
    @Column(name = "delivery_zone")
    private String deliveryZone;

    /** What the courier charged for this order, worked out when it was saved: the delivery fee and the COD charge. */
    @Column(name = "courier_fee", nullable = false)
    private BigDecimal courierFee = BigDecimal.ZERO;

    @Column(name = "courier_cod_fee", nullable = false)
    private BigDecimal courierCodFee = BigDecimal.ZERO;

    /** The amount typed as earned from the courier charge; null when the charges are worked out from the courier's rates. */
    @Column(name = "courier_earning")
    private BigDecimal courierEarning;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    protected SaleOrder() {}

    public SaleOrder(Long tenantId) {
        this.tenantId = tenantId;
    }

    public void apply(Long platformId, Long customerId, OrderStatus status, BigDecimal commissionPct,
                      BigDecimal deliveryCharge, LocalDateTime orderedAt, String notes) {
        this.platformId = platformId;
        this.customerId = customerId;
        this.status = status;
        this.commissionPct = commissionPct;
        this.deliveryCharge = deliveryCharge;
        this.orderedAt = orderedAt;
        this.notes = notes;
    }

    /** A different courier (or none) means a different parcel, so any recorded payout no longer applies. */
    public void applyCourier(Long courierId, String consignmentId, String trackingNumber, BigDecimal codAmount) {
        if (courierId == null || !courierId.equals(this.courierId)) {
            this.courierPaidOn = null;
        }
        this.courierId = courierId;
        this.consignmentId = courierId == null ? null : consignmentId;
        this.trackingNumber = courierId == null ? null : trackingNumber;
        this.codAmount = courierId == null ? null : codAmount;
    }

    public void applyCourierCharges(String zone, BigDecimal fee, BigDecimal codFee) {
        this.deliveryZone = zone;
        this.courierFee = fee == null ? BigDecimal.ZERO : fee;
        this.courierCodFee = codFee == null ? BigDecimal.ZERO : codFee;
    }

    public void setCourierEarning(BigDecimal courierEarning) { this.courierEarning = courierEarning; }

    public void setCourierPaidOn(LocalDate courierPaidOn) { this.courierPaidOn = courierPaidOn; }

    public void setStatus(OrderStatus status) { this.status = status; }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public void assignOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getTenantId() { return tenantId; }
    public Long getPlatformId() { return platformId; }
    public Long getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getCommissionPct() { return commissionPct; }
    public BigDecimal getDeliveryCharge() { return deliveryCharge; }
    public Long getCourierId() { return courierId; }
    public String getConsignmentId() { return consignmentId; }
    public String getTrackingNumber() { return trackingNumber; }
    /** What the courier's tracking link is built from: the tracking code, or else the consignment ID. */
    public String getTrackingRef() { return trackingNumber != null ? trackingNumber : consignmentId; }
    public BigDecimal getCodAmount() { return codAmount; }
    public LocalDate getCourierPaidOn() { return courierPaidOn; }
    public String getDeliveryZone() { return deliveryZone; }
    public BigDecimal getCourierEarning() { return courierEarning; }
    public BigDecimal getCourierFee() { return courierFee; }
    public BigDecimal getCourierCodFee() { return courierCodFee; }
    /** Everything the courier keeps out of this order's cash. */
    public BigDecimal getCourierCharges() { return courierFee.add(courierCodFee); }
    public LocalDateTime getOrderedAt() { return orderedAt; }
    public String getNotes() { return notes; }
    public List<OrderItem> getItems() { return items; }
}
