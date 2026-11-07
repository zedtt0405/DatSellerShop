package com.DatShop.service;

import com.DatShop.model.Voucher;
import com.DatShop.model.VoucherType;
import org.springframework.data.jpa.domain.Specification;

final class VoucherSpecifications {

    private VoucherSpecifications() {
    }

    static Specification<Voucher> management(Boolean active, VoucherType type, String q) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (active != null) {
                predicate = cb.and(predicate, cb.equal(root.get("active"), active));
            }
            if (type != null) {
                predicate = cb.and(predicate, cb.equal(root.get("type"), type));
            }
            if (q != null && !q.isBlank()) {
                predicate = cb.and(predicate, cb.like(cb.upper(root.get("code")), "%" + q.trim().toUpperCase() + "%"));
            }
            return predicate;
        };
    }
}
