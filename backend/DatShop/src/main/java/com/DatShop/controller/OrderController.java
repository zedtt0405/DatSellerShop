package com.DatShop.controller;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.order.OrderDetailResponse;
import com.DatShop.dto.order.OrderSummaryResponse;
import com.DatShop.exception.ApiError;
import com.DatShop.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders")
@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasRole('CUSTOMER')")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(
            summary = "List the authenticated customer's orders",
            description = "Roles: CUSTOMER. Newest first. page default 0, size default 20, max 100.")
    @GetMapping
    public PageResponse<OrderSummaryResponse> listMine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.listMine(authentication, page, size);
    }

    @Operation(
            summary = "Get one order owned by the authenticated customer",
            description = "Roles: CUSTOMER. Returns 404 if the order number is unknown or belongs to another customer. "
                    + "Item prices and shipping fields are historical snapshots.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order detail"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Order not found for the current customer",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{orderNumber}")
    public OrderDetailResponse getMine(Authentication authentication, @PathVariable String orderNumber) {
        return orderService.getMine(authentication, orderNumber);
    }

    @Operation(
            summary = "Cancel an order owned by the authenticated customer",
            description = "Roles: CUSTOMER. Allowed only for PENDING or CONFIRMED. Already CANCELLED returns 200 "
                    + "without restoring stock or voucher usage again. PROCESSING/SHIPPING/DELIVERED return 400. "
                    + "Another customer's order returns 404. Restores inventory and voucher usedCount atomically.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled, or already cancelled"),
            @ApiResponse(responseCode = "400", description = "Order cannot be cancelled in the current status",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not a CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Order not found for the current customer",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Paid order requires refund handling, or inventory cannot be restored",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{orderNumber}/cancel")
    public OrderDetailResponse cancelMine(Authentication authentication, @PathVariable String orderNumber) {
        return orderService.cancelMine(authentication, orderNumber);
    }
}
