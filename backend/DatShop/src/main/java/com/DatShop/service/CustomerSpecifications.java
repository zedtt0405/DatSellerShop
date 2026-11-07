package com.DatShop.service;

import com.DatShop.model.RoleName;
import com.DatShop.model.User;
import com.DatShop.model.UserStatus;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    static Specification<User> customers(UserStatus status, String q) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<User> subRoot = subquery.from(User.class);
            var roles = subRoot.join("roles");
            subquery.select(subRoot.get("id")).where(cb.and(
                    cb.equal(subRoot.get("id"), root.get("id")),
                    cb.equal(roles.get("name"), RoleName.CUSTOMER)));
            predicate = cb.and(predicate, cb.exists(subquery));
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("phoneNumber"), "")), like)));
            }
            return predicate;
        };
    }
}
