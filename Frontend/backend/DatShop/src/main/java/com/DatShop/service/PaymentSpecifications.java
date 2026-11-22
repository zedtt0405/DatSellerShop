package com.DatShop.service;

import com.DatShop.model.Payment;
import com.DatShop.model.PaymentMethod;
import com.DatShop.model.PaymentStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    static Specification<Payment> management(PaymentStatus status, PaymentMethod method, String q) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (method != null) {
                predicate = cb.and(predicate, cb.equal(root.get("method"), method));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toUpperCase() + "%";
                var order = root.join("order", JoinType.INNER);
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.upper(root.get("paymentReference")), like),
                        cb.like(cb.upper(order.get("orderNumber")), like)));
            }
            return predicate;
        };
    }
}
