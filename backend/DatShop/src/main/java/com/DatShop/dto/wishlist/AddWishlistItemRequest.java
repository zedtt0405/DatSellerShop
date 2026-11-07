package com.DatShop.dto.wishlist;

import jakarta.validation.constraints.NotNull;

public record AddWishlistItemRequest(@NotNull Long productId) {
}
