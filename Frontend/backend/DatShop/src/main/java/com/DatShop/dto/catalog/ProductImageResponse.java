package com.DatShop.dto.catalog;

public record ProductImageResponse(
        Long id,
        Long productId,
        String imageUrl,
        String altText,
        Boolean isPrimary,
        Integer sortOrder) {
}
