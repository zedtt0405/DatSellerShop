package com.DatShop.service;

import com.DatShop.dto.catalog.ProductSearchCriteria;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Product;
import com.DatShop.model.ProductStatus;
import com.DatShop.model.ProductVariant;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class ProductSpecifications {

    private ProductSpecifications() {
    }

    static Specification<Product> management(ProductStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (hasText(q)) {
                String pattern = contains(q);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("slug")), pattern, '\\')));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static Specification<Product> publicCatalog(ProductSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            predicates.add(cb.equal(root.get("category").get("status"), CatalogStatus.ACTIVE));
            predicates.add(cb.equal(root.get("brand").get("status"), CatalogStatus.ACTIVE));

            if (hasText(criteria.q())) {
                String pattern = contains(criteria.q());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("slug")), pattern, '\\')));
            }
            if (hasText(criteria.category())) {
                predicates.add(cb.equal(root.get("category").get("slug"), criteria.category()));
            }
            if (hasText(criteria.brand())) {
                predicates.add(cb.equal(root.get("brand").get("slug"), criteria.brand()));
            }
            if (hasText(criteria.sizeName())) {
                predicates.add(existsActiveVariant(root, query, cb, sizePredicate(cb, criteria.sizeName())));
            }
            if (hasText(criteria.color())) {
                predicates.add(existsActiveVariant(root, query, cb, colorPredicate(cb, criteria.color())));
            }
            if (Boolean.TRUE.equals(criteria.featured())) {
                predicates.add(cb.isTrue(root.get("featured")));
            } else if (Boolean.FALSE.equals(criteria.featured())) {
                predicates.add(cb.isFalse(root.get("featured")));
            }
            if (Boolean.TRUE.equals(criteria.inStock())) {
                predicates.add(existsActiveVariant(root, query, cb, (variant, builder) -> builder.greaterThan(
                        variant.get("stockQuantity"), 0)));
            } else if (Boolean.FALSE.equals(criteria.inStock())) {
                predicates.add(cb.not(existsActiveVariant(root, query, cb, (variant, builder) -> builder.greaterThan(
                        variant.get("stockQuantity"), 0))));
            }
            Expression<BigDecimal> payable = cb.coalesce(root.get("salePrice"), root.get("price"));
            if (criteria.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(payable, criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(payable, criteria.maxPrice()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate existsActiveVariant(
            Root<Product> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            VariantPredicate extra) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<ProductVariant> variant = subquery.from(ProductVariant.class);
        subquery.select(cb.literal(1));
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(variant.get("product"), root));
        predicates.add(cb.isTrue(variant.get("active")));
        predicates.add(extra.apply(variant, cb));
        subquery.where(predicates.toArray(Predicate[]::new));
        return cb.exists(subquery);
    }

    private static VariantPredicate sizePredicate(CriteriaBuilder ignored, String sizeName) {
        String expected = sizeName.toLowerCase(Locale.ROOT);
        return (variant, cb) -> cb.equal(
                cb.lower(variant.join("size", JoinType.INNER).get("name")), expected);
    }

    private static VariantPredicate colorPredicate(CriteriaBuilder ignored, String colorName) {
        String expected = colorName.toLowerCase(Locale.ROOT);
        return (variant, cb) -> cb.equal(
                cb.lower(variant.join("color", JoinType.INNER).get("name")), expected);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String contains(String raw) {
        return "%" + raw.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                + "%";
    }

    @FunctionalInterface
    private interface VariantPredicate {
        Predicate apply(Root<ProductVariant> variant, CriteriaBuilder cb);
    }
}
