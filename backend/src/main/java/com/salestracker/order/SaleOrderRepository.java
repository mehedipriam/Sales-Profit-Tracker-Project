package com.salestracker.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SaleOrderRepository extends JpaRepository<SaleOrder, Long> {

    @Query("""
            select o from SaleOrder o
            where o.tenantId = :tenantId
              and (:platformId = 0L or o.platformId = :platformId)
              and o.status in :statuses
              and o.orderedAt >= :from and o.orderedAt < :to
            """)
    Page<SaleOrder> search(@Param("tenantId") Long tenantId, @Param("platformId") long platformId,
                           @Param("statuses") Collection<OrderStatus> statuses,
                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    /** Order counts and money per status; platformId 0 means all platforms. */
    @Query("""
            select new com.salestracker.order.StatusTotals(
                o.status, count(distinct o.id),
                sum(i.soldPrice * i.quantity), sum(i.costPriceSnapshot * i.quantity))
            from SaleOrder o left join o.items i
            where o.tenantId = :tenantId
              and (:platformId = 0L or o.platformId = :platformId)
              and o.orderedAt >= :from and o.orderedAt < :to
            group by o.status
            """)
    List<StatusTotals> totalsByStatus(@Param("tenantId") Long tenantId, @Param("platformId") long platformId,
                                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Per-platform money for orders in one status. */
    @Query("""
            select new com.salestracker.order.PlatformTotals(
                o.platformId, count(distinct o.id),
                sum(i.soldPrice * i.quantity), sum(i.costPriceSnapshot * i.quantity))
            from SaleOrder o left join o.items i
            where o.tenantId = :tenantId
              and o.status = :status
              and (:platformId = 0L or o.platformId = :platformId)
              and o.orderedAt >= :from and o.orderedAt < :to
            group by o.platformId
            """)
    List<PlatformTotals> totalsByPlatform(@Param("tenantId") Long tenantId, @Param("status") OrderStatus status,
                                          @Param("platformId") long platformId,
                                          @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    Optional<SaleOrder> findByIdAndTenantId(Long id, Long tenantId);
}
