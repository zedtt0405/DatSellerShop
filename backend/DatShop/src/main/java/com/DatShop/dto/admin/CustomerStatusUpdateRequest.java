package com.DatShop.dto.admin;

import com.DatShop.model.UserStatus;
import jakarta.validation.constraints.NotNull;

public record CustomerStatusUpdateRequest(@NotNull UserStatus status) {
}
