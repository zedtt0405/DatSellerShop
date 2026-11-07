package com.DatShop.dto.account;

import java.time.LocalDateTime;
import java.util.List;

public record ProfileResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phoneNumber,
        String status,
        List<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
