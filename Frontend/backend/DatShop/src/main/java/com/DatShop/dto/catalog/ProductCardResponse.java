package com.DatShop.dto.catalog;

import java.math.BigDecimal;

public record ProductCardResponse(
        Long id,
        String name,
        String slug,
        BigDecimal price,
        BigDecimal salePrice,
        BigDecimal effectivePrice,
        CategorySummary category,
        BrandSummary brand,
        String primaryImageUrl,
        Boolean featured,
        Boolean inStock) {
}
