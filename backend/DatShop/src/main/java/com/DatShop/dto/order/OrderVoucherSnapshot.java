package com.DatShop.dto.order;

import com.DatShop.model.VoucherType;
import java.math.BigDecimal;

public record OrderVoucherSnapshot(
        String code,
        VoucherType type,
        BigDecimal discountValue,
        BigDecimal discountAmount) {
}
