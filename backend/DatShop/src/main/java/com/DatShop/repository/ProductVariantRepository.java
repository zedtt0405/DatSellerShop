package com.DatShop.repository;

import com.DatShop.model.ProductVariant;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findBySku(String sku);

    @EntityGraph(attributePaths = {"product", "product.category", "product.brand", "size", "color"})
    @Query("SELECT v FROM ProductVariant v WHERE v.id = :id")
    Optional<ProductVariant> findWithCatalogById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT v FROM ProductVariant v
            JOIN FETCH v.product p
            JOIN FETCH p.category
            JOIN FETCH p.brand
            LEFT JOIN FETCH v.size
            LEFT JOIN FETCH v.color
            WHERE v.id IN :ids
            ORDER BY v.id ASC
            """)
    List<ProductVariant> lockByIdsOrderByIdAsc(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT v FROM ProductVariant v
            WHERE v.id IN :ids
            ORDER BY v.id ASC
            """)
    List<ProductVariant> lockForRestoreByIdsOrderByIdAsc(@Param("ids") Collection<Long> ids);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    @EntityGraph(attributePaths = {"size", "color"})
    List<ProductVariant> findByProduct_Id(Long productId);

    @EntityGraph(attributePaths = {"size", "color"})
    List<ProductVariant> findByProduct_IdAndActiveTrue(Long productId);

    @Query("""
            SELECT DISTINCT v.product.id
            FROM ProductVariant v
            WHERE v.product.id IN :productIds
              AND v.active = true
              AND v.stockQuantity > 0
            """)
    List<Long> findInStockProductIds(@Param("productIds") Collection<Long> productIds);

    @Query("""
            SELECT COUNT(v)
            FROM ProductVariant v
            WHERE v.product.id = :productId
              AND ((:sizeId IS NULL AND v.size IS NULL) OR v.size.id = :sizeId)
              AND ((:colorId IS NULL AND v.color IS NULL) OR v.color.id = :colorId)
              AND (:excludeId IS NULL OR v.id <> :excludeId)
            """)
    long countMatchingVariant(
            @Param("productId") Long productId,
            @Param("sizeId") Long sizeId,
            @Param("colorId") Long colorId,
            @Param("excludeId") Long excludeId);
}
