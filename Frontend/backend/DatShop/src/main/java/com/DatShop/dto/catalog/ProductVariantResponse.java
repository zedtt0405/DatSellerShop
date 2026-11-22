package com.DatShop.dto.catalog;

import java.time.LocalDateTime;

public record ProductVariantResponse(
        Long id,
        Long productId,
        String sku,
        Integer stockQuantity,
        Boolean active,
        SizeSummary size,
        ColorSummary color,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
