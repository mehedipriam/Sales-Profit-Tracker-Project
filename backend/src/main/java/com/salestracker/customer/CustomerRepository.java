package com.salestracker.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("""
            select c from Customer c
            where c.tenantId = :tenantId
              and (lower(c.name) like :q escape '\\'
                   or lower(coalesce(c.phone, '')) like :q escape '\\'
                   or lower(coalesce(c.address, '')) like :q escape '\\')
              and (:platformId = 0L or c.sourcePlatformId = :platformId)
            """)
    Page<Customer> search(@Param("tenantId") Long tenantId, @Param("q") String q,
                          @Param("platformId") long platformId, Pageable pageable);

    Optional<Customer> findByIdAndTenantId(Long id, Long tenantId);
}
