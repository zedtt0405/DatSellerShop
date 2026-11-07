package com.DatShop.controller;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.review.MyReviewResponse;
import com.DatShop.dto.review.ReviewCreateRequest;
import com.DatShop.dto.review.ReviewUpdateRequest;
import com.DatShop.exception.ApiError;
import com.DatShop.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reviews")
@RestController
@RequestMapping("/api/reviews")
@PreAuthorize("hasRole('CUSTOMER')")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(
            summary = "List the authenticated customer's reviews",
            description = "Roles: CUSTOMER. Includes HIDDEN reviews so the customer can see moderation state. "
                    + "Newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged own reviews"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/me")
    public PageResponse<MyReviewResponse> listMine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviewService.listMine(authentication, page, size);
    }

    @Operation(
            summary = "Create a product review",
            description = "Roles: CUSTOMER. Requires a DELIVERED order containing the product (verified from "
                    + "OrderItem.productId; payment status is not required). One review per user per product. "
                    + "Duplicate POST returns 409; update the existing review with PUT. Rating 1..5. Comment is "
                    + "optional, max 2000 characters. Product availability (ACTIVE/INACTIVE/DRAFT) does not block "
                    + "review of a still-existing product.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Not a delivered purchase, or invalid rating/comment",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "This customer already reviewed the product",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<MyReviewResponse> create(
            Authentication authentication,
            @Valid @RequestBody ReviewCreateRequest request) {
        MyReviewResponse created = reviewService.create(authentication, request);
        return ResponseEntity.created(URI.create("/api/reviews/" + created.id())).body(created);
    }

    @Operation(
            summary = "Update the authenticated customer's review",
            description = "Roles: CUSTOMER. Other customers' reviews return 404. productId, userId, and status "
                    + "cannot be changed. A HIDDEN review stays HIDDEN after the customer edits it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "Invalid rating/comment or unknown field",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Review not found for the current customer",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{reviewId}")
    public MyReviewResponse updateMine(
            Authentication authentication,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest request) {
        return reviewService.updateMine(authentication, reviewId, request);
    }

    @Operation(
            summary = "Delete the authenticated customer's review",
            description = "Roles: CUSTOMER. Hard delete. Other customers' reviews return 404. After deletion the "
                    + "customer may create a new review for the same product if the delivered purchase still exists. "
                    + "Does not change Order, Payment, stock, voucher, cart, or wishlist.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Review not found for the current customer",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMine(Authentication authentication, @PathVariable Long reviewId) {
        reviewService.deleteMine(authentication, reviewId);
    }
}
