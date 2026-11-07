package com.DatShop.repository;

import com.DatShop.model.ProductImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProduct_IdOrderBySortOrderAscIdAsc(Long productId);

    List<ProductImage> findByProduct_IdAndIsPrimaryTrue(Long productId);

    boolean existsByProduct_Id(Long productId);

    @Query("""
            SELECT i.product.id, i.imageUrl
            FROM ProductImage i
            WHERE i.isPrimary = true AND i.product.id IN :productIds
            """)
    List<Object[]> findPrimaryImageUrlsByProductIds(@Param("productIds") Collection<Long> productIds);
}
