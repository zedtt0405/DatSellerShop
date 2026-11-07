package com.DatShop.dto.admin;

import com.DatShop.model.UserStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerSummaryResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phoneNumber,
        UserStatus status,
        LocalDateTime createdAt,
        long orderCount,
        BigDecimal totalSpent) {
}
