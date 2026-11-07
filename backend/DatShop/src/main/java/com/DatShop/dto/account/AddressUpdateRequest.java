package com.DatShop.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressUpdateRequest(
        @NotBlank @Size(max = 100) String recipientName,
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[0-9+\\-\\s]{8,20}$") String phoneNumber,
        @NotBlank @Size(max = 100) String province,
        @NotBlank @Size(max = 100) String district,
        @NotBlank @Size(max = 100) String ward,
        @NotBlank @Size(max = 255) String addressLine,
        @NotNull Boolean isDefault) {
}
