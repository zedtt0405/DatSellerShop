package com.DatShop.dto.catalog;

import com.DatShop.model.CatalogStatus;
import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        CatalogStatus status,
        Long parentCategoryId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
