package com.DatShop.mapper;

import com.DatShop.dto.wishlist.WishlistItemResponse;
import com.DatShop.dto.wishlist.WishlistResponse;
import com.DatShop.model.Product;
import com.DatShop.model.Wishlist;
import com.DatShop.model.WishlistItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class WishlistMapper {

    private WishlistMapper() {
    }

    public static WishlistResponse toResponse(Wishlist wishlist, List<WishlistItemResponse> items) {
        return new WishlistResponse(wishlist.getId(), items, items.size());
    }

    public static WishlistResponse empty(Long wishlistId) {
        return new WishlistResponse(wishlistId, List.of(), 0);
    }

    public static WishlistResponse unpersistedEmpty() {
        return empty(null);
    }

    public static WishlistItemResponse toItemResponse(
            WishlistItem item, String primaryImageUrl, boolean available, boolean inStock) {
        Product product = item.getProduct();
        BigDecimal price = scale(product.getPrice());
        BigDecimal salePrice = product.getSalePrice() == null ? null : scale(product.getSalePrice());
        return new WishlistItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getSlug(),
                price,
                salePrice,
                scale(CatalogMapper.effectivePrice(product.getPrice(), product.getSalePrice())),
                primaryImageUrl,
                available,
                inStock,
                item.getCreatedAt());
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
