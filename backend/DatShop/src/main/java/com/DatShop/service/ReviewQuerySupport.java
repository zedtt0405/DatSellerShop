package com.DatShop.service;

import com.DatShop.exception.BusinessRuleException;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class ReviewQuerySupport {

    private static final Set<String> PUBLIC_SORT_FIELDS = Set.of("createdAt", "rating");

    private ReviewQuerySupport() {
    }

    static Pageable publicPageable(int page, int size, String sort) {
        if (page < 0) {
            throw new BusinessRuleException("page must not be negative");
        }
        if (size < 1 || size > ProductQuerySupport.MAX_PAGE_SIZE) {
            throw new BusinessRuleException("size must be between 1 and " + ProductQuerySupport.MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, publicSort(sort));
    }

    private static Sort publicSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        }
        String[] parts = raw.split(",", 2);
        String field = parts[0].trim();
        if (!PUBLIC_SORT_FIELDS.contains(field)) {
            throw new BusinessRuleException("Invalid sort field. Allowed: createdAt, rating");
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2 && !parts[1].isBlank()) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BusinessRuleException("Invalid sort direction. Allowed: asc, desc");
            }
        }
        return Sort.by(direction, field).and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
