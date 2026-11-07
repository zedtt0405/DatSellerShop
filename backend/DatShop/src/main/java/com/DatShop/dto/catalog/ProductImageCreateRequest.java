package com.DatShop.dto.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductImageCreateRequest(
        @NotBlank @Size(max = 500) String imageUrl,
        @Size(max = 200) String altText,
        @NotNull Boolean isPrimary,
        @NotNull @Min(0) Integer sortOrder) {
}
