package com.DatShop.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record VoucherEvaluation(
        boolean valid,
        String message,
        BigDecimal discountAmount) {

    static VoucherEvaluation invalid(String message) {
        return new VoucherEvaluation(false, message, money(BigDecimal.ZERO));
    }

    static VoucherEvaluation valid(BigDecimal discountAmount) {
        return new VoucherEvaluation(true, "Voucher applied successfully", money(discountAmount));
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
