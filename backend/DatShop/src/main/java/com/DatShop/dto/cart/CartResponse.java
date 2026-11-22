package com.DatShop.dto.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long id,
        List<CartItemResponse> items,
        Integer itemCount,
        Integer totalQuantity,
        BigDecimal subtotal) {
}
