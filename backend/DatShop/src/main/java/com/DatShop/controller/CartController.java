package com.DatShop.controller;

import com.DatShop.dto.cart.AddCartItemRequest;
import com.DatShop.dto.cart.CartResponse;
import com.DatShop.dto.cart.UpdateCartItemRequest;
import com.DatShop.exception.ApiError;
import com.DatShop.service.CartService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cart")
@RestController
@RequestMapping("/api/cart")
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(
            summary = "Get the authenticated customer's cart",
            description = "Roles: CUSTOMER. Read-only: does not create a cart row if none exists. "
                    + "Prices and subtotals are calculated from the current catalog. "
                    + "Cart does not reserve or deduct stock.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current cart, or an empty cart with id=null"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public CartResponse getCart(Authentication authentication) {
        return cartService.getCart(authentication);
    }

    @Operation(
            summary = "Add a product variant to the cart",
            description = "Roles: CUSTOMER. Always 200 because adding an existing variant increments quantity. "
                    + "Creates the cart row on the first successful add. "
                    + "Stock is validated but not reserved or deducted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated cart"),
            @ApiResponse(responseCode = "400", description = "Validation, unavailable product, or insufficient stock",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Product variant not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/items")
    public CartResponse addItem(
            Authentication authentication,
            @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(authentication, request);
    }

    @Operation(
            summary = "Set the exact quantity of a cart line",
            description = "Roles: CUSTOMER. Quantity must be at least 1. Use DELETE to remove a line. "
                    + "Stock is validated but not reserved or deducted.")
    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(
            Authentication authentication,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(authentication, itemId, request);
    }

    @Operation(summary = "Remove one cart line", description = "Roles: CUSTOMER. 204 empty body. Does not change inventory.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removed"),
            @ApiResponse(responseCode = "404", description = "Cart item not found for the current user",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(Authentication authentication, @PathVariable Long itemId) {
        cartService.removeItem(authentication, itemId);
    }

    @Operation(
            summary = "Clear all cart lines",
            description = "Roles: CUSTOMER. Always 204, including when the cart is missing or already empty. "
                    + "Does not change inventory.")
    @ApiResponse(responseCode = "204", description = "Cleared")
    @DeleteMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(Authentication authentication) {
        cartService.clear(authentication);
    }
}
