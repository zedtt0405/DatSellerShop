package com.DatShop.dto.catalog;

import com.DatShop.model.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 220) String slug,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal price,
        @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal salePrice,
        @NotNull Long categoryId,
        @NotNull Long brandId,
        @NotNull ProductStatus status,
        @NotNull Boolean featured) {
}
