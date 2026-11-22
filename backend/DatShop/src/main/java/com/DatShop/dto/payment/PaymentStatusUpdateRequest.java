package com.DatShop.dto.payment;

import com.DatShop.model.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public record PaymentStatusUpdateRequest(@NotNull PaymentStatus status) {
}
