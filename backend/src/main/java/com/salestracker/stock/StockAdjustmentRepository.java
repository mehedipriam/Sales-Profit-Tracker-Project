package com.salestracker.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    /** productId 0 means every product. */
    @Query("""
            select a from StockAdjustment a
            where a.tenantId = :tenantId
              and (:productId = 0L or a.productId = :productId)
              and a.reason in :reasons
            """)
    Page<StockAdjustment> search(@Param("tenantId") Long tenantId, @Param("productId") Long productId,
                                 @Param("reasons") Collection<StockReason> reasons, Pageable pageable);

    /** [productId, net quantity change] of the automatic rows an order has written so far. */
    @Query("""
            select a.productId, sum(a.quantityChange) from StockAdjustment a
            where a.tenantId = :tenantId and a.orderId = :orderId and a.reason = com.salestracker.stock.StockReason.ORDER
            group by a.productId
            """)
    List<Object[]> netByProductForOrder(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

    Optional<StockAdjustment> findByIdAndTenantId(Long id, Long tenantId);
}
