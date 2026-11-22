package com.DatShop.dto.review;

public record ReviewProductSummary(
        Long id,
        String name,
        String slug,
        String primaryImageUrl) {
}
