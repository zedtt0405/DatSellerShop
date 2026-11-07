package com.DatShop.dto.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductVariantUpdateRequest(
        @NotNull Long productId,
        Long sizeId,
        Long colorId,
        @NotBlank @Size(max = 64) String sku,
        @NotNull @Min(0) Integer stockQuantity,
        @NotNull Boolean active) {
}
