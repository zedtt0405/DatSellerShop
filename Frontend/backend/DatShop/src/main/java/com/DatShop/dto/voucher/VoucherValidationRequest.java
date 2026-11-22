package com.DatShop.dto.voucher;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoucherValidationRequest(@NotBlank @Size(max = 40) String code) {
}
