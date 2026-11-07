package com.DatShop.dto.review;

import com.DatShop.model.ReviewStatus;
import java.time.LocalDateTime;

public record AdminReviewResponse(
        Long id,
        Integer rating,
        String comment,
        ReviewStatus status,
        ReviewProductSummary product,
        ReviewerSummary reviewer,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
