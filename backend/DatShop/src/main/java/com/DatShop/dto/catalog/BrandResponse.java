package com.DatShop.dto.catalog;

import com.DatShop.model.CatalogStatus;
import java.time.LocalDateTime;

public record BrandResponse(
        Long id,
        String name,
        String slug,
        String description,
        String logoUrl,
        CatalogStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
