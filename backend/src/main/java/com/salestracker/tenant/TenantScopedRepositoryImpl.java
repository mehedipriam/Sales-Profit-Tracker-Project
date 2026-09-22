package com.salestracker.tenant;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Backs every Spring Data repository in this app (wired via repositoryBaseClass on SalesTrackerApplication's
 * @EnableJpaRepositories), so tenant scoping is enforced even on the plain methods JpaRepository provides for
 * free - findById, findAll, existsById, deleteById... - not just the hand-written @Query/derived methods every
 * repository in this app already scopes explicitly (an audit across the whole codebase for Phase 7a found
 * every one of them correctly filtered by tenantId; this hardens that from "carefully checked by hand, and it
 * happens to be right today" to "cannot return or touch another tenant's row even from code nobody wrote yet").
 *
 * Only applies to entities implementing TenantOwned (every tenant-owned entity does). When no request is
 * under way (TenantContext unset - registration and login, before any tenant is known, are the only such
 * case in this app), these methods fall back to their normal, unfiltered behavior rather than blocking
 * legitimate internal use that isn't driven by an authenticated HTTP request.
 *
 * save/saveAll are deliberately NOT overridden: every write path in this app builds its entity from
 * already-tenant-validated input (see the individual services), so there is nothing for a generic check here
 * to add: it would either be a no-op or hide a bug better caught at the point the wrong id was accepted.
 */
public class TenantScopedRepositoryImpl<T, ID extends Serializable> extends SimpleJpaRepository<T, ID> {

    public TenantScopedRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
    }

    @Override
    public Optional<T> findById(ID id) {
        return super.findById(id).filter(this::ownedByCurrentTenant);
    }

    @Override
    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    @Override
    public List<T> findAll() {
        return filtered(super.findAll());
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        return filtered(super.findAllById(ids));
    }

    @Override
    public void deleteById(ID id) {
        findById(id).ifPresent(this::delete);
    }

    @Override
    public void delete(T entity) {
        if (ownedByCurrentTenant(entity)) {
            super.delete(entity);
        }
    }

    @Override
    public void deleteAllById(Iterable<? extends ID> ids) {
        // Re-resolve through findAllById so it's filtered, rather than trusting the caller's id list.
        @SuppressWarnings("unchecked")
        List<T> owned = findAllById((Iterable<ID>) ids);
        owned.forEach(super::delete);
    }

    @Override
    public void deleteAll() {
        filtered(super.findAll()).forEach(super::delete);
    }

    private List<T> filtered(List<T> values) {
        if (TenantContext.get() == null) return values;
        return values.stream().filter(this::ownedByCurrentTenant).collect(Collectors.toList());
    }

    private boolean ownedByCurrentTenant(T entity) {
        Long tenantId = TenantContext.get();
        if (tenantId == null || !(entity instanceof TenantOwned owned)) return true;
        return tenantId.equals(owned.getTenantId());
    }
}
