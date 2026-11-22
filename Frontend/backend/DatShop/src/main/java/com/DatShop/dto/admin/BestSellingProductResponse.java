package com.DatShop.dto.admin;

import java.math.BigDecimal;

public record BestSellingProductResponse(
        Long productId,
        String productName,
        String productSlug,
        long quantitySold,
        BigDecimal revenue) {
}
