package com.DatShop.controller;

import com.DatShop.dto.review.ProductReviewPageResponse;
import com.DatShop.exception.ApiError;
import com.DatShop.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reviews")
@SecurityRequirements
@RestController
@RequestMapping("/api/products/{slug}/reviews")
public class ProductReviewPublicController {

    private final ReviewService reviewService;

    public ProductReviewPublicController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(
            summary = "List published reviews for a product",
            description = "No authentication. The product must be publicly visible: Product.status, category.status, "
                    + "and brand.status are all ACTIVE. Missing, DRAFT, INACTIVE, or inactive category/brand slugs "
                    + "return 404 Product not found. Returns only PUBLISHED reviews. HIDDEN reviews are omitted and "
                    + "do not contribute to averageRating or reviewCount. averageRating uses scale 2 (HALF_UP) over "
                    + "all published ratings, not just the current page. Default sort is createdAt DESC. Allowed sort "
                    + "fields: createdAt, rating.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged published reviews with rating summary"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameter",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Product is missing or not publicly visible",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ProductReviewPageResponse listPublic(
            @PathVariable String slug,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, maximum 100, default 10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Whitelist: createdAt, rating. Example: createdAt,desc")
            @RequestParam(required = false) String sort) {
        return reviewService.listPublic(slug, page, size, sort);
    }
}
