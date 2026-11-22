package com.DatShop.dto.catalog;

import com.DatShop.model.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        String slug,
        String description,
        BigDecimal price,
        BigDecimal salePrice,
        BigDecimal effectivePrice,
        CategorySummary category,
        BrandSummary brand,
        ProductStatus status,
        Boolean featured,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
