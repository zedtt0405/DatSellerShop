package com.DatShop.dto.voucher;

import java.math.BigDecimal;

public record VoucherValidationResponse(
        String code,
        boolean valid,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAfterDiscount,
        String message) {
}
