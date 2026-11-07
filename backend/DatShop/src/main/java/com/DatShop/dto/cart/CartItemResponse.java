package com.DatShop.dto.cart;

import com.DatShop.dto.catalog.ColorSummary;
import com.DatShop.dto.catalog.SizeSummary;
import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long variantId,
        String sku,
        CartProductSummary product,
        SizeSummary size,
        ColorSummary color,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineSubtotal,
        Integer stockQuantity,
        Boolean available) {
}
