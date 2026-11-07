package com.DatShop.mapper;

import com.DatShop.dto.payment.PaymentResponse;
import com.DatShop.model.Payment;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentReference(),
                payment.getOrder().getOrderNumber(),
                payment.getMethod(),
                payment.getStatus(),
                money(payment.getAmount()),
                payment.getQrContent(),
                payment.getProviderReference(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
