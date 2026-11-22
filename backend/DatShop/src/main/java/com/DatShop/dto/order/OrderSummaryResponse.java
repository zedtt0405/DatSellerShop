package com.DatShop.dto.order;

import com.DatShop.model.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryResponse(
        String orderNumber,
        OrderStatus status,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        int itemCount,
        LocalDateTime createdAt) {
}
