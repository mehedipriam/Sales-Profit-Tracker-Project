package com.salestracker.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

public interface OrderActivityRepository extends JpaRepository<OrderActivity, Long> {

    /** orderId / userId 0 mean any. */
    @Query("""
            select a from OrderActivity a
            where a.tenantId = :tenantId
              and (:orderId = 0L or a.orderId = :orderId)
              and (:userId = 0L or a.userId = :userId)
              and a.action in :actions
              and a.createdAt >= :from and a.createdAt < :to
            """)
    Page<OrderActivity> search(@Param("tenantId") Long tenantId, @Param("orderId") long orderId,
                               @Param("userId") long userId, @Param("actions") Collection<OrderAction> actions,
                               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);
}
