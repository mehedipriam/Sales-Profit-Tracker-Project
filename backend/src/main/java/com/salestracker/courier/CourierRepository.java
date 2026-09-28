package com.salestracker.courier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourierRepository extends JpaRepository<Courier, Long> {
    List<Courier> findByTenantIdAndActiveTrueOrderByName(Long tenantId);
    Optional<Courier> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Courier> findByTenantIdAndName(Long tenantId, String name);
}
