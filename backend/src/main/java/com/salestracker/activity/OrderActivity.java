package com.salestracker.activity;

import com.salestracker.order.OrderStatus;
import com.salestracker.tenant.TenantOwned;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One entry in the activity log: who did what to which order, and when. Never changed once written. */
@Entity
@Table(name = "order_activity")
public class OrderActivity implements TenantOwned {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** No foreign key: a deleted order keeps its history. */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderAction action;

    /** The customer's name at the time, so the entry still reads right after the order is gone. */
    @Column(name = "customer_name", nullable = false)
    private String customerName;

    /** What the customer pays (products + delivery charge) before an edit that changed it; null otherwise. */
    @Column(name = "amount_before")
    private BigDecimal amountBefore;

    /** What the customer pays after the action; for a payout, the cash the courier paid out. */
    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status")
    private OrderStatus toStatus;

    /** For an edit: the OrderChange names it touched, comma-separated. */
    private String changes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected OrderActivity() {}

    OrderActivity(Long tenantId, Long orderId, Long userId, OrderAction action, String customerName,
                  BigDecimal amountBefore, BigDecimal amount, OrderStatus fromStatus, OrderStatus toStatus,
                  String changes) {
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.userId = userId;
        this.action = action;
        this.customerName = customerName;
        this.amountBefore = amountBefore;
        this.amount = amount;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changes = changes;
        this.createdAt = LocalDateTime.now().withNano(0);
    }

    public Long getId() { return id; }
    @Override public Long getTenantId() { return tenantId; }
    public Long getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public OrderAction getAction() { return action; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getAmountBefore() { return amountBefore; }
    public BigDecimal getAmount() { return amount; }
    public OrderStatus getFromStatus() { return fromStatus; }
    public OrderStatus getToStatus() { return toStatus; }
    public String getChanges() { return changes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
