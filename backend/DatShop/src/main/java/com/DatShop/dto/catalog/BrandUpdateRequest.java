package com.DatShop.dto.catalog;

import com.DatShop.model.CatalogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BrandUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 120) String slug,
        @Size(max = 500) String description,
        @Size(max = 500) String logoUrl,
        @NotNull CatalogStatus status) {
}
