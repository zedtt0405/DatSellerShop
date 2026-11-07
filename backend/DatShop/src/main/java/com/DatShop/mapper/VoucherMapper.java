package com.DatShop.mapper;

import com.DatShop.dto.voucher.VoucherResponse;
import com.DatShop.model.Voucher;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class VoucherMapper {

    private VoucherMapper() {
    }

    public static VoucherResponse toResponse(Voucher voucher) {
        return new VoucherResponse(
                voucher.getId(),
                voucher.getCode(),
                voucher.getType(),
                money(voucher.getDiscountValue()),
                money(voucher.getMinOrderAmount()),
                money(voucher.getMaxDiscountAmount()),
                voucher.getUsageLimit(),
                voucher.getUsedCount(),
                voucher.getStartAt(),
                voucher.getEndAt(),
                voucher.getActive(),
                voucher.getCreatedAt(),
                voucher.getUpdatedAt());
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
