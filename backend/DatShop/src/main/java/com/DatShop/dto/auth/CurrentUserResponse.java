package com.DatShop.dto.auth;

import java.util.List;

public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phoneNumber,
        String status,
        List<String> roles
) {
}
