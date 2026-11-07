package com.DatShop.service;

import com.DatShop.model.Order;
import com.DatShop.model.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

final class OrderSpecifications {

    private OrderSpecifications() {
    }

    static Specification<Order> management(OrderStatus status, String q) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                predicate = cb.and(predicate, cb.like(
                        cb.upper(root.get("orderNumber")),
                        "%" + q.trim().toUpperCase() + "%"));
            }
            return predicate;
        };
    }
}
