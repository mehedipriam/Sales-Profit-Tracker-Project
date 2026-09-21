package com.salestracker.product;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    private String sku;
    private String category;

    @Column(name = "cost_price", nullable = false)
    private BigDecimal costPrice;

    @Column(name = "selling_price", nullable = false)
    private BigDecimal sellingPrice;

    @Column(name = "stock_qty")
    private Integer stockQty;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    @Column(nullable = false)
    private boolean active = true;

    protected Product() {}

    public Product(Long tenantId) {
        this.tenantId = tenantId;
    }

    public void apply(String name, String sku, String category, BigDecimal costPrice,
                      BigDecimal sellingPrice, Integer stockQty, Integer lowStockThreshold) {
        this.name = name;
        this.sku = sku;
        this.category = category;
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.stockQty = stockQty;
        this.lowStockThreshold = lowStockThreshold;
    }

    /** Moves tracked stock by a signed amount; the caller writes the matching stock log row. */
    public void addStock(int delta) { this.stockQty = this.stockQty + delta; }

    public void deactivate() { this.active = false; }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public String getCategory() { return category; }
    public BigDecimal getCostPrice() { return costPrice; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public Integer getStockQty() { return stockQty; }
    public Integer getLowStockThreshold() { return lowStockThreshold; }
    public boolean isActive() { return active; }
}
