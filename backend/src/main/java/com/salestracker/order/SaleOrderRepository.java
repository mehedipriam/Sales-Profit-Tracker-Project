package com.salestracker.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface SaleOrderRepository extends JpaRepository<SaleOrder, Long> {

    @Query("""
            select o from SaleOrder o
            where o.tenantId = :tenantId
              and (:platformId = 0L or o.platformId = :platformId)
              and o.status in :statuses
            """)
    Page<SaleOrder> search(@Param("tenantId") Long tenantId, @Param("platformId") long platformId,
                           @Param("statuses") Collection<OrderStatus> statuses, Pageable pageable);

    Optional<SaleOrder> findByIdAndTenantId(Long id, Long tenantId);
}
