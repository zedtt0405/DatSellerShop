package com.DatShop.dto.catalog;

import java.util.List;

public record ProductDetailResponse(
        ProductResponse product,
        List<ProductImageResponse> images,
        List<ProductVariantResponse> variants) {
}
