package com.salestracker.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    /** Customers whose phone, ignoring dashes, spaces, brackets and a +, ends with the given digits; oldest first. */
    @Query("""
            select c from Customer c
            where c.tenantId = :tenantId and c.phone is not null
              and replace(replace(replace(replace(replace(c.phone, '-', ''), ' ', ''), '+', ''), '(', ''), ')', '')
                  like concat('%', :suffix)
            order by c.id
            """)
    List<Customer> findByPhoneEndingWith(@Param("tenantId") Long tenantId, @Param("suffix") String suffix);
}
