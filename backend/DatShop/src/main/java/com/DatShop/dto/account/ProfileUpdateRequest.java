package com.DatShop.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Size(max = 20) @Pattern(regexp = "^[0-9+\\-\\s]{8,20}$") String phoneNumber) {
}
