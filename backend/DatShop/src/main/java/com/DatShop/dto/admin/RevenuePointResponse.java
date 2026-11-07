package com.DatShop.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenuePointResponse(
        LocalDate date,
        BigDecimal revenue,
        long orderCount) {
}
