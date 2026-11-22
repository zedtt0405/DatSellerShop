package com.DatShop.dto.account;

import java.time.LocalDateTime;

public record AddressResponse(
        Long id,
        String recipientName,
        String phoneNumber,
        String province,
        String district,
        String ward,
        String addressLine,
        Boolean isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
