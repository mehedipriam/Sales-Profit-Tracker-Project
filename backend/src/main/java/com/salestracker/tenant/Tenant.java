package com.salestracker.tenant;

import jakarta.persistence.*;

@Entity
@Table(name = "tenants")
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    protected Tenant() {}

    public Tenant(String name) {
        this.name = name;
    }

    public void rename(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
}
