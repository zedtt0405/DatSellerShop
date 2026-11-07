package com.DatShop.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ColorCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String hexCode,
        @NotNull Boolean active) {
}
