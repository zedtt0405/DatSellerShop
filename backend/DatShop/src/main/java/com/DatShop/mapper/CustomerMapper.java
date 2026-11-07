package com.DatShop.mapper;

import com.DatShop.dto.account.AddressResponse;
import com.DatShop.dto.admin.CustomerDetailResponse;
import com.DatShop.dto.admin.CustomerSummaryResponse;
import com.DatShop.dto.order.OrderSummaryResponse;
import com.DatShop.model.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class CustomerMapper {

    private CustomerMapper() {
    }

    public static CustomerSummaryResponse toSummary(User user, long orderCount, BigDecimal totalSpent) {
        return new CustomerSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getStatus(),
                user.getCreatedAt(),
                orderCount,
                money(totalSpent));
    }

    public static CustomerDetailResponse toDetail(
            User user,
            List<AddressResponse> addresses,
            long orderCount,
            BigDecimal totalSpent,
            List<OrderSummaryResponse> latestOrders) {
        return new CustomerDetailResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                addresses,
                orderCount,
                money(totalSpent),
                latestOrders);
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
