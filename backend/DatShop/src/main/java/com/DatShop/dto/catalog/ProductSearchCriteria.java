package com.DatShop.dto.catalog;

import java.math.BigDecimal;

public record ProductSearchCriteria(
        String q,
        String category,
        String brand,
        String sizeName,
        String color,
        Boolean inStock,
        Boolean featured,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        int page,
        int size,
        String sort) {
}
