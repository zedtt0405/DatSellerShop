package com.DatShop.controller.admin;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.payment.PaymentResponse;
import com.DatShop.dto.payment.PaymentStatusUpdateRequest;
import com.DatShop.exception.ApiError;
import com.DatShop.model.PaymentMethod;
import com.DatShop.model.PaymentStatus;
import com.DatShop.service.PaymentManagementService;
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

@Tag(name = "Admin Payments")
@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminPaymentController {

    private final PaymentManagementService paymentManagementService;

    public AdminPaymentController(PaymentManagementService paymentManagementService) {
        this.paymentManagementService = paymentManagementService;
    }

    @Operation(
            summary = "List payments",
            description = "Roles: ADMIN, STAFF. Newest first. Optional status, method, and q "
                    + "(paymentReference or orderNumber).")
    @GetMapping
    public PageResponse<PaymentResponse> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return paymentManagementService.list(status, method, q, page, size);
    }

    @Operation(summary = "Get a payment by reference", description = "Roles: ADMIN, STAFF")
    @GetMapping("/{paymentReference}")
    public PaymentResponse getByReference(@PathVariable String paymentReference) {
        return paymentManagementService.getByReference(paymentReference);
    }

    @Operation(
            summary = "Confirm or fail a payment",
            description = "Roles: ADMIN, STAFF. Allowed targets: PAID, FAILED. PENDING and CANCELLED cannot be "
                    + "set here. BANK_QR is a demo/manual confirmation, not a real payment-gateway callback. "
                    + "COD may be marked PAID only after the order is DELIVERED. BANK_QR may be marked PAID "
                    + "while the order is not CANCELLED/RETURNED. Already PAID is idempotent 200 and does not "
                    + "change paidAt. Does not auto-confirm or auto-deliver the order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "Invalid transition or COD not yet delivered",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not STAFF or ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{paymentReference}/status")
    public PaymentResponse updateStatus(
            @PathVariable String paymentReference,
            @Valid @RequestBody PaymentStatusUpdateRequest request) {
        return paymentManagementService.updateStatus(paymentReference, request);
    }
}
