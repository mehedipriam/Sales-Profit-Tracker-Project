package com.salestracker.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformRepository extends JpaRepository<Platform, Long> {
    List<Platform> findByTenantIdAndActiveTrueOrderByName(Long tenantId);
    boolean existsByIdAndTenantId(Long id, Long tenantId);
    Optional<Platform> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Platform> findByTenantIdAndName(Long tenantId, String name);
}
