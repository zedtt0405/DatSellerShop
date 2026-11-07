package com.DatShop.dto.admin;

import java.math.BigDecimal;

public record DashboardSummaryResponse(
        BigDecimal revenue,
        long orderCount,
        long customerCount,
        long productCount,
        long pendingOrderCount,
        long deliveredOrderCount) {
}
