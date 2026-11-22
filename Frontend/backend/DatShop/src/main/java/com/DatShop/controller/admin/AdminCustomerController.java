package com.DatShop.controller.admin;

import com.DatShop.dto.admin.CustomerDetailResponse;
import com.DatShop.dto.admin.CustomerStatusUpdateRequest;
import com.DatShop.dto.admin.CustomerSummaryResponse;
import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.order.OrderSummaryResponse;
import com.DatShop.exception.ApiError;
import com.DatShop.model.OrderStatus;
import com.DatShop.model.UserStatus;
import com.DatShop.service.CustomerManagementService;
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

@Tag(name = "Admin Customers")
@RestController
@RequestMapping("/api/admin/customers")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminCustomerController {

    private final CustomerManagementService customerManagementService;

    public AdminCustomerController(CustomerManagementService customerManagementService) {
        this.customerManagementService = customerManagementService;
    }

    @Operation(
            summary = "List CUSTOMER accounts",
            description = "Roles: ADMIN, STAFF. Only users with RoleName.CUSTOMER. Newest first. Optional status "
                    + "and q (username, email, fullName, phoneNumber). passwordHash is never returned. Role "
                    + "editing and account deletion are not supported. orderCount is all orders; totalSpent sums "
                    + "Order.totalAmount for CONFIRMED, PROCESSING, SHIPPING, and DELIVERED (PENDING/CANCELLED/"
                    + "RETURNED excluded).")
    @GetMapping
    public PageResponse<CustomerSummaryResponse> list(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return customerManagementService.list(status, q, page, size);
    }

    @Operation(
            summary = "Get a CUSTOMER account",
            description = "Roles: ADMIN, STAFF. Includes read-only addresses, orderCount, totalSpent, and the "
                    + "latest 5 orders. Non-CUSTOMER users return 404. passwordHash is never returned.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not STAFF or ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{customerId}")
    public CustomerDetailResponse getById(@PathVariable Long customerId) {
        return customerManagementService.getById(customerId);
    }

    @Operation(
            summary = "List a customer's orders",
            description = "Roles: ADMIN, STAFF. Management view by customer user id, not the authenticated staff "
                    + "account. Newest first. Optional status filter. Reuses order snapshots.")
    @GetMapping("/{customerId}/orders")
    public PageResponse<OrderSummaryResponse> listOrders(
            @PathVariable Long customerId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return customerManagementService.listOrders(customerId, status, page, size);
    }

    @Operation(
            summary = "Change a CUSTOMER account status",
            description = "Roles: ADMIN only. STAFF receives 403. Allowed: ACTIVE, INACTIVE, BLOCKED. Does not "
                    + "change roles, password, orders, or delete the account. BLOCKED/INACTIVE immediately fail "
                    + "subsequent protected requests because the JWT filter reloads User.status from the database. "
                    + "Reactivation to ACTIVE restores login with the existing password.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status or unknown field",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{customerId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerDetailResponse updateStatus(
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerStatusUpdateRequest request) {
        return customerManagementService.updateStatus(customerId, request);
    }
}
