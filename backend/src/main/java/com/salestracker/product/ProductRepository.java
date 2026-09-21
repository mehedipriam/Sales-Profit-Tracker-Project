package com.salestracker.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select p from Product p
            where p.tenantId = :tenantId and p.active = true
              and (lower(p.name) like :q escape '\\' or lower(coalesce(p.sku, '')) like :q escape '\\')
              and (:category = '' or p.category = :category)
            """)
    Page<Product> search(@Param("tenantId") Long tenantId, @Param("q") String q,
                         @Param("category") String category, Pageable pageable);

    @Query("select distinct p.category from Product p where p.tenantId = :tenantId and p.active = true "
            + "and p.category is not null order by p.category")
    List<String> categories(@Param("tenantId") Long tenantId);

    Optional<Product> findByIdAndTenantIdAndActiveTrue(Long id, Long tenantId);

    /** Row-locked read for anything that changes stock, so concurrent sales cannot lose an update. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.tenantId = :tenantId")
    Optional<Product> lockByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /** Active products whose stock is at or below their own threshold, lowest stock first. */
    @Query("""
            select p from Product p
            where p.tenantId = :tenantId and p.active = true
              and p.stockQty is not null and p.lowStockThreshold is not null and p.stockQty <= p.lowStockThreshold
            order by p.stockQty, p.name
            """)
    List<Product> lowStock(@Param("tenantId") Long tenantId, Pageable pageable);

    Optional<Product> findByTenantIdAndSku(Long tenantId, String sku);
}
