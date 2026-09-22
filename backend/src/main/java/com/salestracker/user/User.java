package com.salestracker.user;

import com.salestracker.tenant.TenantOwned;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User implements TenantOwned {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    protected User() {}

    public User(Long tenantId, String email, String passwordHash, String fullName, Role role) {
        this.tenantId = tenantId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public void rename(String fullName) { this.fullName = fullName; }
    public void changePassword(String passwordHash) { this.passwordHash = passwordHash; }
    public void deactivate() { this.active = false; }

    public Long getId() { return id; }
    @Override public Long getTenantId() { return tenantId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
}
