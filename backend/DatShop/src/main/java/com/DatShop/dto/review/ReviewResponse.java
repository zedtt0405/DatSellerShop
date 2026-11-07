package com.DatShop.dto.review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Integer rating,
        String comment,
        String reviewerName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
