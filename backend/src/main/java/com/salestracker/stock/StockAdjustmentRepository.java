package com.salestracker.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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
              and (a.productId in (select p.id from Product p
                                   where p.tenantId = :tenantId and lower(p.name) like :q escape '\\')
                   or lower(coalesce(a.note, '')) like :q escape '\\')
            """)
    Page<StockAdjustment> search(@Param("tenantId") Long tenantId, @Param("productId") Long productId,
                                 @Param("reasons") Collection<StockReason> reasons, @Param("q") String q,
                                 Pageable pageable);

    /** [productId, net quantity change] of the automatic rows an order has written so far. */
    @Query("""
            select a.productId, sum(a.quantityChange) from StockAdjustment a
            where a.tenantId = :tenantId and a.orderId = :orderId and a.reason = com.salestracker.stock.StockReason.ORDER
            group by a.productId
            """)
    List<Object[]> netByProductForOrder(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

    /** One row: [units added, units taken out] since the given time. Opening stock is not counted as added. */
    @Query("""
            select coalesce(sum(case when a.quantityChange > 0 then a.quantityChange else 0 end), 0),
                   coalesce(sum(case when a.quantityChange < 0 then -a.quantityChange else 0 end), 0)
            from StockAdjustment a
            where a.tenantId = :tenantId and a.createdAt >= :since
              and a.reason <> com.salestracker.stock.StockReason.INITIAL
            """)
    List<Object[]> movedSince(@Param("tenantId") Long tenantId, @Param("since") LocalDateTime since);

    Optional<StockAdjustment> findByIdAndTenantId(Long id, Long tenantId);
}
