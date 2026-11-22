package com.DatShop.controller;

import com.DatShop.dto.wishlist.AddWishlistItemRequest;
import com.DatShop.dto.wishlist.WishlistResponse;
import com.DatShop.dto.wishlist.WishlistStatusResponse;
import com.DatShop.exception.ApiError;
import com.DatShop.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Wishlist")
@RestController
@RequestMapping("/api/wishlist")
@PreAuthorize("hasRole('CUSTOMER')")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @Operation(
            summary = "Get the authenticated customer's wishlist",
            description = "Roles: CUSTOMER. Read-only: does not create a wishlist row if none exists. "
                    + "Prices, images and availability come from the current catalog. "
                    + "Wishlist stores products, not variants, and does not reserve stock.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current wishlist, or empty with id=null"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public WishlistResponse getWishlist(Authentication authentication) {
        return wishlistService.getWishlist(authentication);
    }

    @Operation(
            summary = "Add a product to the wishlist",
            description = "Roles: CUSTOMER. Idempotent: adding an existing product returns 200 without a duplicate row. "
                    + "Out-of-stock public products may be wishlisted. DRAFT/INACTIVE products cannot.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated wishlist"),
            @ApiResponse(responseCode = "400", description = "Product exists but is not publicly wishlisted",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/items")
    public WishlistResponse addItem(
            Authentication authentication,
            @Valid @RequestBody AddWishlistItemRequest request) {
        return wishlistService.addProduct(authentication, request);
    }

    @Operation(
            summary = "Whether the current customer has wishlisted a product",
            description = "Roles: CUSTOMER. Read-only and does not create a wishlist. "
                    + "Returns wishlisted=false when the product is missing or not in the wishlist.")
    @GetMapping("/items/{productId}/status")
    public WishlistStatusResponse status(Authentication authentication, @PathVariable Long productId) {
        return wishlistService.isWishlisted(authentication, productId);
    }

    @Operation(
            summary = "Remove a product from the wishlist",
            description = "Roles: CUSTOMER. Idempotent 204 even if the product is not in the wishlist. "
                    + "Does not delete the Product.")
    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(Authentication authentication, @PathVariable Long productId) {
        wishlistService.removeProduct(authentication, productId);
    }

    @Operation(
            summary = "Clear all wishlist items",
            description = "Roles: CUSTOMER. Always 204, including when the wishlist is missing or already empty.")
    @DeleteMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(Authentication authentication) {
        wishlistService.clear(authentication);
    }
}
