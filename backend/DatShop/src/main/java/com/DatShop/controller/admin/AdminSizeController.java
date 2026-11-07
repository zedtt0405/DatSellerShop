package com.DatShop.controller.admin;

import com.DatShop.dto.catalog.SizeCreateRequest;
import com.DatShop.dto.catalog.SizeResponse;
import com.DatShop.dto.catalog.SizeUpdateRequest;
import com.DatShop.service.SizeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Sizes")
@RestController
@RequestMapping("/api/admin/sizes")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminSizeController {

    private final SizeService sizeService;

    public AdminSizeController(SizeService sizeService) {
        this.sizeService = sizeService;
    }

    @Operation(summary = "List all sizes including inactive", description = "Roles: ADMIN, STAFF")
    @GetMapping
    public List<SizeResponse> listAll() {
        return sizeService.listAll();
    }

    @Operation(summary = "Create a size", description = "Roles: ADMIN")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<SizeResponse> create(@Valid @RequestBody SizeCreateRequest request) {
        SizeResponse created = sizeService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/sizes/" + created.id())).body(created);
    }

    @Operation(summary = "Update a size", description = "Roles: ADMIN")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public SizeResponse update(@PathVariable Long id, @Valid @RequestBody SizeUpdateRequest request) {
        return sizeService.update(id, request);
    }
}
