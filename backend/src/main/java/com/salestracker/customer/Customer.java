package com.salestracker.customer;

import jakarta.persistence.*;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String address;

    @Column(name = "source_platform_id")
    private Long sourcePlatformId;

    @Column(columnDefinition = "text")
    private String notes;

    protected Customer() {}

    public Customer(Long tenantId) {
        this.tenantId = tenantId;
    }

    public void apply(String name, String phone, String address, Long sourcePlatformId, String notes) {
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.sourcePlatformId = sourcePlatformId;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public Long getSourcePlatformId() { return sourcePlatformId; }
    public String getNotes() { return notes; }
}
