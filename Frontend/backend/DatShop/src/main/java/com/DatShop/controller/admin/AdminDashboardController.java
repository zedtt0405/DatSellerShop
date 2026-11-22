package com.DatShop.controller.admin;

import com.DatShop.dto.admin.BestSellingProductResponse;
import com.DatShop.dto.admin.DashboardSummaryResponse;
import com.DatShop.dto.admin.RevenuePointResponse;
import com.DatShop.exception.ApiError;
import com.DatShop.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Dashboard")
@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    public AdminDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(
            summary = "Dashboard KPI summary",
            description = "Roles: ADMIN, STAFF. Read-only. Revenue is SUM(Order.totalAmount) for CONFIRMED, "
                    + "PROCESSING, SHIPPING, and DELIVERED — the same definition as Customer Management totalSpent. "
                    + "Payment.status is not required (COD may be fulfilled before collection). orderCount is all "
                    + "Orders in range, including PENDING and CANCELLED. customerCount is CUSTOMER-role users "
                    + "(any account status). productCount is all Product rows including DRAFT/INACTIVE. Optional "
                    + "from/to are ISO dates (YYYY-MM-DD) on Order.createdAt LocalDateTime in the JVM default "
                    + "timezone. Missing bounds are open-ended. Default with no params is all-time.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary"),
            @ApiResponse(responseCode = "400", description = "Invalid date range",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated account is not STAFF or ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @Parameter(description = "Inclusive start date YYYY-MM-DD")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Inclusive end date YYYY-MM-DD")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.getSummary(from, to);
    }

    @Operation(
            summary = "Daily revenue chart",
            description = "Roles: ADMIN, STAFF. Each point is one calendar day. revenue and orderCount include only "
                    + "CONFIRMED/PROCESSING/SHIPPING/DELIVERED orders. Days with no eligible orders are zero-filled "
                    + "(revenue 0.00, orderCount 0). Default range is the last 30 calendar days including today. "
                    + "Maximum span is 366 days.")
    @GetMapping("/revenue-chart")
    public List<RevenuePointResponse> revenueChart(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.getRevenueChart(from, to);
    }

    @Operation(
            summary = "Best-selling products",
            description = "Roles: ADMIN, STAFF. Groups OrderItem snapshots by productId (all variants together). "
                    + "quantitySold is SUM(quantity) and revenue is SUM(lineTotal) for CONFIRMED/PROCESSING/"
                    + "SHIPPING/DELIVERED orders. Display name/slug is the latest eligible OrderItem snapshot, not "
                    + "the current Product row. Default limit 10, allowed 1..50. Optional from/to; default all-time.")
    @GetMapping("/best-selling-products")
    public List<BestSellingProductResponse> bestSellingProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return dashboardService.getBestSellingProducts(from, to, limit);
    }
}
