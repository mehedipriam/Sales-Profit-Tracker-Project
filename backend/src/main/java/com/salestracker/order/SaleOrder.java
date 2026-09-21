package com.salestracker.order;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A sale. Named SaleOrder because ORDER is a reserved word in SQL/HQL; the table is "orders". */
@Entity
@Table(name = "orders")
public class SaleOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

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
                      LocalDateTime orderedAt, String notes) {
        this.platformId = platformId;
        this.customerId = customerId;
        this.status = status;
        this.commissionPct = commissionPct;
        this.orderedAt = orderedAt;
        this.notes = notes;
    }

    public void setStatus(OrderStatus status) { this.status = status; }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getPlatformId() { return platformId; }
    public Long getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getCommissionPct() { return commissionPct; }
    public LocalDateTime getOrderedAt() { return orderedAt; }
    public String getNotes() { return notes; }
    public List<OrderItem> getItems() { return items; }
}
