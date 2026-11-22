package com.DatShop.service;

import com.DatShop.model.ProductReview;
import com.DatShop.model.ReviewStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

final class ReviewSpecifications {

    private ReviewSpecifications() {
    }

    static Specification<ProductReview> management(ReviewStatus status, Integer rating, Long productId, String q) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (rating != null) {
                predicate = cb.and(predicate, cb.equal(root.get("rating"), rating));
            }
            if (productId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("product").get("id"), productId));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                var product = root.join("product", JoinType.INNER);
                var user = root.join("user", JoinType.INNER);
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(product.get("name")), like),
                        cb.like(cb.lower(user.get("fullName")), like),
                        cb.like(cb.lower(user.get("username")), like)));
            }
            return predicate;
        };
    }
}
