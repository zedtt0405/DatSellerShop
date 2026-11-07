package com.DatShop.repository;

import com.DatShop.model.ProductReview;
import com.DatShop.model.ReviewStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long>, JpaSpecificationExecutor<ProductReview> {

    boolean existsByUser_IdAndProduct_Id(Long userId, Long productId);

    @EntityGraph(attributePaths = {"product", "user"})
    Optional<ProductReview> findByIdAndUser_Id(Long id, Long userId);

    @EntityGraph(attributePaths = {"product", "user"})
    @Query("SELECT r FROM ProductReview r WHERE r.id = :id")
    Optional<ProductReview> findDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = "product")
    Page<ProductReview> findByUser_Id(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<ProductReview> findByProduct_IdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "product"})
    @Override
    Page<ProductReview> findAll(Specification<ProductReview> spec, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(r.rating), 0)
            FROM ProductReview r
            WHERE r.product.id = :productId AND r.status = :status
            """)
    Number sumRatingByProductIdAndStatus(@Param("productId") Long productId, @Param("status") ReviewStatus status);
}
