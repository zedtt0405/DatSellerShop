package com.DatShop.dto.voucher;

import com.DatShop.model.VoucherType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record VoucherResponse(
        Long id,
        String code,
        VoucherType type,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Integer usageLimit,
        Integer usedCount,
        Instant startAt,
        Instant endAt,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
