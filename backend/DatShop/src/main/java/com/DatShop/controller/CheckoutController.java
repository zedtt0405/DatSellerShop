package com.DatShop.controller;

import com.DatShop.dto.order.CheckoutRequest;
import com.DatShop.dto.order.OrderDetailResponse;
import com.DatShop.exception.ApiError;
import com.DatShop.service.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Checkout")
@RestController
@RequestMapping("/api/checkout")
@PreAuthorize("hasRole('CUSTOMER')")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @Operation(
            summary = "Place an order from the current cart",
            description = "Roles: CUSTOMER. Server-computed prices, stock deduction, optional voucher consumption, "
                    + "and cart clearing happen in one transaction. shippingFee is 0.00 until a shipping-rate "
                    + "module exists. Lock order: User, then ProductVariants by id ASC, then Voucher if supplied. "
                    + "Does not accept financial fields from the client. Status is always PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created"),
            @ApiResponse(responseCode = "400", description = "Empty cart, unavailable item, stock, or voucher",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Address not found for the current customer",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<OrderDetailResponse> checkout(
            Authentication authentication,
            @Valid @RequestBody CheckoutRequest request) {
        OrderDetailResponse created = checkoutService.checkout(authentication, request);
        return ResponseEntity.created(URI.create("/api/orders/" + created.orderNumber())).body(created);
    }
}
