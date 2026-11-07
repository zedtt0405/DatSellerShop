package com.DatShop.controller;

import com.DatShop.dto.payment.PaymentCreateRequest;
import com.DatShop.dto.payment.PaymentResponse;
import com.DatShop.exception.ApiError;
import com.DatShop.service.PaymentService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments")
@RestController
@RequestMapping("/api/orders/{orderNumber}/payment")
@PreAuthorize("hasRole('CUSTOMER')")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(
            summary = "Get payment for the authenticated customer's order",
            description = "Roles: CUSTOMER. Returns 404 if the order is missing, belongs to another customer, "
                    + "or has no payment yet.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Order or payment not found for the current customer",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public PaymentResponse getMine(Authentication authentication, @PathVariable String orderNumber) {
        return paymentService.getMine(authentication, orderNumber);
    }

    @Operation(
            summary = "Create COD or BANK_QR payment for an owned order",
            description = "Roles: CUSTOMER. Amount is always Order.totalAmount. BANK_QR is a demo/manual "
                    + "confirmation flow (not a real payment gateway); qrContent can be rendered as a QR by the "
                    + "frontend. COD may be created through SHIPPING. BANK_QR may be created only before SHIPPING. "
                    + "CANCELLED and DELIVERED cannot create a payment. Repeating the same method returns the "
                    + "existing payment (200). A different method returns 409. v1 does not allow a second payment "
                    + "row after FAILED.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "200", description = "Existing payment for the same method"),
            @ApiResponse(responseCode = "400", description = "Order cannot accept a payment",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Order not found for the current customer",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Payment already exists with a different method",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<PaymentResponse> createMine(
            Authentication authentication,
            @PathVariable String orderNumber,
            @Valid @RequestBody PaymentCreateRequest request) {
        PaymentService.CreatedPayment created = paymentService.createMine(authentication, orderNumber, request);
        if (!created.created()) {
            return ResponseEntity.ok(created.payment());
        }
        return ResponseEntity.created(URI.create("/api/orders/" + orderNumber + "/payment")).body(created.payment());
    }
}
