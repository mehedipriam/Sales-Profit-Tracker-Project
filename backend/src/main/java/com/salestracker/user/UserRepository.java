package com.salestracker.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByIdAndActiveTrue(Long id);
    List<User> findByTenantIdOrderByFullName(Long tenantId);
    Optional<User> findByIdAndTenantId(Long id, Long tenantId);
}
