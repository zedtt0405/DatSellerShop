package com.DatShop.controller;

import com.DatShop.dto.voucher.VoucherValidationRequest;
import com.DatShop.dto.voucher.VoucherValidationResponse;
import com.DatShop.exception.ApiError;
import com.DatShop.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Vouchers")
@RestController
@RequestMapping("/api/vouchers")
@PreAuthorize("hasRole('CUSTOMER')")
public class VoucherController {

    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @Operation(
            summary = "Preview a voucher against the current cart",
            description = "Roles: CUSTOMER. Computes discount from the authenticated customer's current cart "
                    + "using live catalog prices. Does not attach the voucher to the cart, does not increment "
                    + "usedCount, and does not change stock. Inclusive validity: now >= startAt and now <= endAt. "
                    + "Unknown or malformed codes return 200 with valid=false. "
                    + "FIXED vouchers ignore maxDiscountAmount and require it to be null when created.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Validation preview"),
            @ApiResponse(responseCode = "400", description = "Malformed request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/validate")
    public VoucherValidationResponse validate(
            Authentication authentication,
            @Valid @RequestBody VoucherValidationRequest request) {
        return voucherService.preview(authentication, request);
    }
}
