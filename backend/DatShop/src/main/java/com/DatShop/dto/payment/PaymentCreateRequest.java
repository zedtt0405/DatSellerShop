package com.DatShop.dto.payment;

import com.DatShop.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record PaymentCreateRequest(@NotNull PaymentMethod method) {
}
