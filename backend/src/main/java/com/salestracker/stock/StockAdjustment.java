package com.salestracker.stock;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_adjustments")
public class StockAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Set on the automatic rows an order writes; cleared by the database if the order is deleted. */
    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockReason reason;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected StockAdjustment() {}

    public StockAdjustment(Long tenantId, Long productId, Long orderId, StockReason reason,
                           int quantityChange, int stockAfter, String note) {
        this.tenantId = tenantId;
        this.productId = productId;
        this.orderId = orderId;
        this.reason = reason;
        this.quantityChange = quantityChange;
        this.stockAfter = stockAfter;
        this.note = note;
        this.createdAt = LocalDateTime.now().withNano(0);
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getOrderId() { return orderId; }
    public StockReason getReason() { return reason; }
    public int getQuantityChange() { return quantityChange; }
    public int getStockAfter() { return stockAfter; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
