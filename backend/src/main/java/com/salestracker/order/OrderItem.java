package com.salestracker.order;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private SaleOrder order;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    /** Product cost at the time of sale; later cost changes never alter this. */
    @Column(name = "cost_price_snapshot", nullable = false)
    private BigDecimal costPriceSnapshot;

    @Column(name = "sold_price", nullable = false)
    private BigDecimal soldPrice;

    protected OrderItem() {}

    public OrderItem(SaleOrder order, Long tenantId, Long productId, int quantity,
                     BigDecimal costPriceSnapshot, BigDecimal soldPrice) {
        this.order = order;
        this.tenantId = tenantId;
        this.productId = productId;
        this.quantity = quantity;
        this.costPriceSnapshot = costPriceSnapshot;
        this.soldPrice = soldPrice;
    }

    public BigDecimal lineRevenue() { return soldPrice.multiply(BigDecimal.valueOf(quantity)); }
    public BigDecimal lineCost() { return costPriceSnapshot.multiply(BigDecimal.valueOf(quantity)); }
    /** (sold price - cost price) x quantity */
    public BigDecimal lineProfit() { return lineRevenue().subtract(lineCost()); }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getCostPriceSnapshot() { return costPriceSnapshot; }
    public BigDecimal getSoldPrice() { return soldPrice; }
}
