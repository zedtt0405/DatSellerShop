package com.DatShop.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @NotNull Long addressId,
        @Size(max = 40) String voucherCode,
        @Size(max = 500) String customerNote) {
}
