package com.DatShop.service;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.catalog.ProductSearchCriteria;
import com.DatShop.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class ProductQuerySupport {

    static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> SORT_FIELDS = Set.of("name", "price", "createdAt");
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "name", "name",
            "price", "price",
            "createdAt", "createdAt");

    private ProductQuerySupport() {
    }

    static ProductSearchCriteria normalize(ProductSearchCriteria criteria) {
        int page = criteria.page();
        int size = criteria.size();
        if (page < 0) {
            throw new BusinessRuleException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessRuleException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        assertPrices(criteria.minPrice(), criteria.maxPrice());
        return new ProductSearchCriteria(
                Text.emptyToNull(criteria.q()),
                Text.emptyToNull(criteria.category()),
                Text.emptyToNull(criteria.brand()),
                Text.emptyToNull(criteria.sizeName()),
                Text.emptyToNull(criteria.color()),
                criteria.inStock(),
                criteria.featured(),
                criteria.minPrice(),
                criteria.maxPrice(),
                page,
                size,
                Text.emptyToNull(criteria.sort()));
    }

    static Pageable pageable(ProductSearchCriteria criteria) {
        return PageRequest.of(criteria.page(), criteria.size(), sort(criteria.sort()));
    }

    static Pageable pageable(int page, int size) {
        if (page < 0) {
            throw new BusinessRuleException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessRuleException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    static <T> PageResponse<T> pageResponse(Page<?> page, List<T> content) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private static Sort sort(String raw) {
        if (raw == null || raw.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = raw.split(",", 2);
        String field = parts[0].trim();
        if (!SORT_FIELDS.contains(field)) {
            throw new BusinessRuleException("Invalid sort field. Allowed: name, price, createdAt");
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2 && !parts[1].isBlank()) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BusinessRuleException("Invalid sort direction. Allowed: asc, desc");
            }
        }
        return Sort.by(direction, SORT_COLUMNS.get(field));
    }

    private static void assertPrices(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("minPrice must not be negative");
        }
        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("maxPrice must not be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessRuleException("minPrice must not be greater than maxPrice");
        }
    }
}
