package com.DatShop.dto.wishlist;

import java.util.List;

public record WishlistResponse(
        Long id,
        List<WishlistItemResponse> items,
        Integer itemCount) {
}
