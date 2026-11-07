package com.DatShop.dto.cart;

public record CartProductSummary(
        Long id,
        String name,
        String slug,
        String primaryImageUrl) {
}
