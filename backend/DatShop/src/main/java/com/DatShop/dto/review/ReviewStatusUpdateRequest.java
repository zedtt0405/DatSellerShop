package com.DatShop.dto.review;

import com.DatShop.model.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewStatusUpdateRequest(@NotNull ReviewStatus status) {
}
