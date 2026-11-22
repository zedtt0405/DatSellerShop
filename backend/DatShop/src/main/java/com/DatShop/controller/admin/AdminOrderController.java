package com.DatShop.controller.admin;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.order.OrderDetailResponse;
import com.DatShop.dto.order.OrderStatusUpdateRequest;
import com.DatShop.dto.order.OrderSummaryResponse;
import com.DatShop.exception.ApiError;
import com.DatShop.model.OrderStatus;
import com.DatShop.service.OrderManagementService;
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

@Tag(name = "Admin Orders")
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminOrderController {

    private final OrderManagementService orderManagementService;

    public AdminOrderController(OrderManagementService orderManagementService) {
        this.orderManagementService = orderManagementService;
    }

    @Operation(
            summary = "List all orders",
            description = "Roles: ADMIN, STAFF. Newest first. Optional status and q (orderNumber) filters.")
    @GetMapping
    public PageResponse<OrderSummaryResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderManagementService.list(status, q, page, size);
    }

    @Operation(summary = "Get any order by order number", description = "Roles: ADMIN, STAFF. Snapshot data is authoritative.")
    @GetMapping("/{orderNumber}")
    public OrderDetailResponse getByOrderNumber(@PathVariable String orderNumber) {
        return orderManagementService.getByOrderNumber(orderNumber);
    }

    @Operation(
            summary = "Update order status",
            description = "Roles: ADMIN, STAFF. Explicit forward transitions only. CANCELLED from "
                    + "PENDING/CONFIRMED/PROCESSING restores stock and voucher usedCount using the same "
                    + "cancellation path as the customer. Already CANCELLED is idempotent 200. "
                    + "SHIPPING and DELIVERED cannot be cancelled. Stock is not deducted on forward advances.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not STAFF or ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Inventory or voucher usage cannot be restored safely",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{orderNumber}/status")
    public OrderDetailResponse updateStatus(
            @PathVariable String orderNumber,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return orderManagementService.updateStatus(orderNumber, request);
    }
}
