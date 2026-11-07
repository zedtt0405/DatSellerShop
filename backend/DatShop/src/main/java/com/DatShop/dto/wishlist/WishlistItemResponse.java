package com.DatShop.dto.wishlist;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WishlistItemResponse(
        Long id,
        Long productId,
        String name,
        String slug,
        BigDecimal price,
        BigDecimal salePrice,
        BigDecimal effectivePrice,
        String primaryImageUrl,
        Boolean available,
        Boolean inStock,
        LocalDateTime createdAt) {
}
