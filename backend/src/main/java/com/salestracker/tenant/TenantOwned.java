package com.salestracker.tenant;

/** Implemented by every entity that has a tenant_id column, so TenantScopedRepositoryImpl can check
 * ownership generically instead of needing per-entity code. Tenant itself is the one exception - it IS
 * the tenant identity, not owned by one. */
public interface TenantOwned {
    Long getTenantId();
}
