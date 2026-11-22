package com.DatShop.dto.admin;

import com.DatShop.dto.account.AddressResponse;
import com.DatShop.dto.order.OrderSummaryResponse;
import com.DatShop.model.UserStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CustomerDetailResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phoneNumber,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<AddressResponse> addresses,
        long orderCount,
        BigDecimal totalSpent,
        List<OrderSummaryResponse> latestOrders) {
}
