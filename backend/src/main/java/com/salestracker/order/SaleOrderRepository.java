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

    /** Per-day money for orders in one status (the trend chart rolls days up into months when needed). */
    @Query("""
            select new com.salestracker.order.DayTotals(
                cast(o.orderedAt as LocalDate), count(distinct o.id),
                sum(i.soldPrice * i.quantity), sum(i.costPriceSnapshot * i.quantity))
            from SaleOrder o left join o.items i
            where o.tenantId = :tenantId
              and o.status = :status
              and (:platformId = 0L or o.platformId = :platformId)
              and o.orderedAt >= :from and o.orderedAt < :to
            group by cast(o.orderedAt as LocalDate)
            order by cast(o.orderedAt as LocalDate)
            """)
    List<DayTotals> totalsByDay(@Param("tenantId") Long tenantId, @Param("status") OrderStatus status,
                                @Param("platformId") long platformId,
                                @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Per-product units and money for orders in one status. */
    @Query("""
            select new com.salestracker.order.ProductTotals(
                i.productId, sum(i.quantity),
                sum(i.soldPrice * i.quantity), sum(i.costPriceSnapshot * i.quantity))
            from OrderItem i join i.order o
            where o.tenantId = :tenantId
              and o.status = :status
              and (:platformId = 0L or o.platformId = :platformId)
              and o.orderedAt >= :from and o.orderedAt < :to
            group by i.productId
            """)
    List<ProductTotals> totalsByProduct(@Param("tenantId") Long tenantId, @Param("status") OrderStatus status,
                                        @Param("platformId") long platformId,
                                        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Delivery charges live on the order, not its items, so they are summed without the items join (which would
    // count an order's charge once per line).

    @Query("""
            select new com.salestracker.order.StatusDelivery(o.status, sum(o.deliveryCharge))
            from SaleOrder o
            where o.tenantId = :tenantId
              and (:platformId = 0L or o.platformId = :platformId)
              and o.orderedAt >= :from and o.orderedAt < :to
            group by o.status
            """)
    List<StatusDelivery> deliveryByStatus(@Param("tenantId") Long tenantId, @Param("platformId") long platformId,
                                          @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select new com.salestracker.order.PlatformDelivery(o.platformId, sum(o.deliveryCharge))
            from SaleOrder o
            where o.tenantId = :tenantId
              and o.status = :status
              and (:platformId = 0L or o.platformId = :platformId)
              and o.orderedAt >= :from and o.orderedAt < :to
            group by o.platformId
            """)
    List<PlatformDelivery> deliveryByPlatform(@Param("tenantId") Long tenantId, @Param("status") OrderStatus status,
                                              @Param("platformId") long platformId,
                                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select new com.salestracker.order.DayDelivery(cast(o.orderedAt as LocalDate), sum(o.deliveryCharge))
            from SaleOrder o
            where o.tenantId = :tenantId
              and o.status = :status
              and (:platformId = 0L or o.platformId = :platformId)
              and o.orderedAt >= :from and o.orderedAt < :to
            group by cast(o.orderedAt as LocalDate)
            """)
    List<DayDelivery> deliveryByDay(@Param("tenantId") Long tenantId, @Param("status") OrderStatus status,
                                    @Param("platformId") long platformId,
                                    @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ---- courier payouts: cash a courier collected (or will collect) and hasn't paid out yet ----

    /** Unpaid courier cash per courier and status (PENDING = still on the way, PAID = delivered, cash collected). */
    @Query("""
            select new com.salestracker.order.CourierTotals(o.courierId, o.status, count(o), sum(o.codAmount))
            from SaleOrder o
            where o.tenantId = :tenantId
              and o.courierId is not null and o.courierPaidOn is null and o.codAmount > 0
              and o.status in :statuses
            group by o.courierId, o.status
            """)
    List<CourierTotals> unpaidCourierTotals(@Param("tenantId") Long tenantId,
                                            @Param("statuses") Collection<OrderStatus> statuses);

    /** Delivered orders whose cash the courier still holds, oldest first; courierId 0 means every courier. */
    @Query("""
            select o from SaleOrder o
            where o.tenantId = :tenantId
              and o.courierId is not null and o.courierPaidOn is null and o.codAmount > 0
              and o.status = com.salestracker.order.OrderStatus.PAID
              and (:courierId = 0L or o.courierId = :courierId)
            order by o.orderedAt, o.id
            """)
    List<SaleOrder> payoutsDue(@Param("tenantId") Long tenantId, @Param("courierId") long courierId, Pageable pageable);

    /** Orders a courier has paid out, latest payout first. */
    @Query("""
            select o from SaleOrder o
            where o.tenantId = :tenantId
              and o.courierPaidOn is not null
              and (:courierId = 0L or o.courierId = :courierId)
            order by o.courierPaidOn desc, o.id desc
            """)
    List<SaleOrder> recentPayouts(@Param("tenantId") Long tenantId, @Param("courierId") long courierId, Pageable pageable);

    List<SaleOrder> findByTenantIdAndIdIn(Long tenantId, Collection<Long> ids);

    Optional<SaleOrder> findByIdAndTenantId(Long id, Long tenantId);
}
