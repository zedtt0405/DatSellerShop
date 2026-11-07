package com.DatShop.dto.review;

import com.DatShop.model.ReviewStatus;
import java.time.LocalDateTime;

public record MyReviewResponse(
        Long id,
        ReviewProductSummary product,
        Integer rating,
        String comment,
        ReviewStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
