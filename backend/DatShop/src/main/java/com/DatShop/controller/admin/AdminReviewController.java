package com.DatShop.controller.admin;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.review.AdminReviewResponse;
import com.DatShop.dto.review.ReviewStatusUpdateRequest;
import com.DatShop.exception.ApiError;
import com.DatShop.model.ReviewStatus;
import com.DatShop.service.ReviewManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Reviews")
@RestController
@RequestMapping("/api/admin/reviews")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminReviewController {

    private final ReviewManagementService reviewManagementService;

    public AdminReviewController(ReviewManagementService reviewManagementService) {
        this.reviewManagementService = reviewManagementService;
    }

    @Operation(
            summary = "List product reviews",
            description = "Roles: ADMIN, STAFF. Newest first. Optional filters: status, rating, productId, and q "
                    + "(product name or reviewer name).")
    @GetMapping
    public PageResponse<AdminReviewResponse> list(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviewManagementService.list(status, rating, productId, q, page, size);
    }

    @Operation(summary = "Get a review by id", description = "Roles: ADMIN, STAFF")
    @GetMapping("/{reviewId}")
    public AdminReviewResponse getById(@PathVariable Long reviewId) {
        return reviewManagementService.getById(reviewId);
    }

    @Operation(
            summary = "Publish or hide a review",
            description = "Roles: ADMIN, STAFF. Allowed targets: PUBLISHED, HIDDEN. Hiding a review removes it from "
                    + "the public product review list and from averageRating/reviewCount immediately. Customer GET "
                    + "/api/reviews/me still shows the HIDDEN row. This is not an approval queue.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status or unknown field",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not STAFF or ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Review not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{reviewId}/status")
    public AdminReviewResponse updateStatus(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewStatusUpdateRequest request) {
        return reviewManagementService.updateStatus(reviewId, request);
    }
}
