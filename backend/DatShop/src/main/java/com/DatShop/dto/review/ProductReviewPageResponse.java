package com.DatShop.dto.review;

import java.math.BigDecimal;
import java.util.List;

public record ProductReviewPageResponse(
        List<ReviewResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        BigDecimal averageRating,
        long reviewCount) {
}
