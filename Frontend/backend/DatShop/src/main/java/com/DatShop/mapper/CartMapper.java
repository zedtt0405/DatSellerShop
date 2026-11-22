package com.DatShop.mapper;

import com.DatShop.dto.cart.CartItemResponse;
import com.DatShop.dto.cart.CartProductSummary;
import com.DatShop.dto.cart.CartResponse;
import com.DatShop.model.Cart;
import com.DatShop.model.CartItem;
import com.DatShop.model.Product;
import com.DatShop.model.ProductVariant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class CartMapper {

    private CartMapper() {
    }

    public static CartResponse toResponse(Cart cart, List<CartItemResponse> items) {
        int totalQuantity = items.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::lineSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new CartResponse(cart.getId(), items, items.size(), totalQuantity, subtotal);
    }

    public static CartResponse empty(Long cartId) {
        return new CartResponse(cartId, List.of(), 0, 0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    public static CartResponse unpersistedEmpty() {
        return empty(null);
    }

    public static CartItemResponse toItemResponse(
            CartItem item, String primaryImageUrl, boolean available) {
        ProductVariant variant = item.getProductVariant();
        Product product = variant.getProduct();
        BigDecimal unitPrice = CatalogMapper.effectivePrice(product.getPrice(), product.getSalePrice())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal lineSubtotal = unitPrice
                .multiply(BigDecimal.valueOf(item.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        return new CartItemResponse(
                item.getId(),
                variant.getId(),
                variant.getSku(),
                new CartProductSummary(
                        product.getId(),
                        product.getName(),
                        product.getSlug(),
                        primaryImageUrl),
                CatalogMapper.toSizeSummary(variant.getSize()),
                CatalogMapper.toColorSummary(variant.getColor()),
                item.getQuantity(),
                unitPrice,
                lineSubtotal,
                variant.getStockQuantity(),
                available);
    }
}
