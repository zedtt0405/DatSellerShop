package com.DatShop.dto.order;

public record OrderShippingSnapshot(
        String recipientName,
        String recipientPhone,
        String province,
        String district,
        String ward,
        String addressLine) {
}
