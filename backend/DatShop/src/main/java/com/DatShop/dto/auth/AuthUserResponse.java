package com.DatShop.dto.auth;

import java.util.List;

public record AuthUserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        List<String> roles
) {
}
