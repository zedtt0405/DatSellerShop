package com.DatShop.dto.payment;

import com.DatShop.model.PaymentMethod;
import com.DatShop.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record PaymentResponse(
        String paymentReference,
        String orderNumber,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal amount,
        String qrContent,
        String providerReference,
        Instant paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
