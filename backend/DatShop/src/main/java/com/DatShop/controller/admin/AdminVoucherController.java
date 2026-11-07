package com.DatShop.controller.admin;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.voucher.VoucherCreateRequest;
import com.DatShop.dto.voucher.VoucherResponse;
import com.DatShop.dto.voucher.VoucherUpdateRequest;
import com.DatShop.exception.ApiError;
import com.DatShop.model.VoucherType;
import com.DatShop.service.VoucherService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Vouchers")
@RestController
@RequestMapping("/api/admin/vouchers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVoucherController {

    private final VoucherService voucherService;

    public AdminVoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @Operation(summary = "List vouchers", description = "Roles: ADMIN. Sorted by createdAt DESC.")
    @GetMapping
    public PageResponse<VoucherResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) VoucherType type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return voucherService.list(active, type, q, page, size);
    }

    @Operation(summary = "Get a voucher", description = "Roles: ADMIN")
    @GetMapping("/{id}")
    public VoucherResponse getById(@PathVariable Long id) {
        return voucherService.getById(id);
    }

    @Operation(summary = "Create a voucher", description = "Roles: ADMIN. Code is stored uppercase. usedCount starts at 0.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Validation or business rule",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate code",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<VoucherResponse> create(@Valid @RequestBody VoucherCreateRequest request) {
        VoucherResponse created = voucherService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/vouchers/" + created.id())).body(created);
    }

    @Operation(
            summary = "Update a voucher",
            description = "Roles: ADMIN. Full replace. usedCount cannot be set from the request body.")
    @PutMapping("/{id}")
    public VoucherResponse update(@PathVariable Long id, @Valid @RequestBody VoucherUpdateRequest request) {
        return voucherService.update(id, request);
    }
}
