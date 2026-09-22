package com.salestracker.platform;

import com.salestracker.tenant.TenantOwned;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "platforms")
public class Platform implements TenantOwned {
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

    public void apply(String name, BigDecimal commissionPct) {
        this.name = name;
        this.commissionPct = commissionPct;
    }

    public void deactivate() { this.active = false; }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getName() { return name; }
    public BigDecimal getCommissionPct() { return commissionPct; }
    public boolean isActive() { return active; }
}
