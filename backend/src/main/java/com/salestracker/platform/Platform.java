package com.salestracker.platform;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "platforms")
public class Platform {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "commission_pct", nullable = false)
    private BigDecimal commissionPct = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    protected Platform() {}

    public Platform(Long tenantId, String name) {
        this.tenantId = tenantId;
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getName() { return name; }
    public BigDecimal getCommissionPct() { return commissionPct; }
    public boolean isActive() { return active; }
}
