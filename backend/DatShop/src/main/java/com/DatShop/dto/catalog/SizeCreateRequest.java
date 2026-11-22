package com.DatShop.dto.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SizeCreateRequest(
        @NotBlank @Size(max = 30) String name,
        @NotNull @Min(0) Integer displayOrder,
        @NotNull Boolean active) {
}
