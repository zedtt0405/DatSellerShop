package com.DatShop.dto.order;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        Long variantId,
        String productName,
        String productSlug,
        String sku,
        String sizeName,
        String colorName,
        String productImageUrl,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal) {
}
