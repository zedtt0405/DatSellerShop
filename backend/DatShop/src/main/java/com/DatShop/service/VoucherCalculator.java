package com.DatShop.service;

import com.DatShop.model.Voucher;
import com.DatShop.model.VoucherType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class VoucherCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public VoucherEvaluation evaluate(Voucher voucher, BigDecimal subtotal, Instant now) {
        if (voucher == null) {
            return VoucherEvaluation.invalid("Voucher is invalid");
        }
        if (!Boolean.TRUE.equals(voucher.getActive())) {
            return VoucherEvaluation.invalid("Voucher is not active");
        }
        if (now.isBefore(voucher.getStartAt())) {
            return VoucherEvaluation.invalid("Voucher is not yet valid");
        }
        if (now.isAfter(voucher.getEndAt())) {
            return VoucherEvaluation.invalid("Voucher has expired");
        }
        Integer usageLimit = voucher.getUsageLimit();
        int usedCount = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
        if (usageLimit != null && usedCount >= usageLimit) {
            return VoucherEvaluation.invalid("Voucher usage limit has been reached");
        }
        BigDecimal order = VoucherEvaluation.money(subtotal == null ? BigDecimal.ZERO : subtotal);
        BigDecimal minOrder = voucher.getMinOrderAmount();
        if (minOrder != null && minOrder.compareTo(BigDecimal.ZERO) > 0 && order.compareTo(minOrder) < 0) {
            return VoucherEvaluation.invalid("Minimum order amount not reached");
        }
        BigDecimal discount = discount(voucher, order);
        return VoucherEvaluation.valid(discount);
    }

    private static BigDecimal discount(Voucher voucher, BigDecimal subtotal) {
        if (voucher.getType() == VoucherType.PERCENT) {
            BigDecimal raw = subtotal
                    .multiply(voucher.getDiscountValue())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal cap = voucher.getMaxDiscountAmount();
            if (cap != null && raw.compareTo(cap) > 0) {
                return cap;
            }
            return raw;
        }
        return voucher.getDiscountValue().min(subtotal);
    }
}
