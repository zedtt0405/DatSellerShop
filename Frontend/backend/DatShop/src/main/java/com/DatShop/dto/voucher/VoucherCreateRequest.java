package com.DatShop.dto.voucher;

import com.DatShop.model.VoucherType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record VoucherCreateRequest(
        @NotBlank @Size(min = 3, max = 40) String code,
        @NotNull VoucherType type,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal discountValue,
        @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal minOrderAmount,
        @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal maxDiscountAmount,
        @Min(1) Integer usageLimit,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotNull Boolean active) {
}
