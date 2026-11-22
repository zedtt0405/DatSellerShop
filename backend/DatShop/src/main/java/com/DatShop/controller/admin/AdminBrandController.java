package com.DatShop.controller.admin;

import com.DatShop.dto.catalog.BrandCreateRequest;
import com.DatShop.dto.catalog.BrandResponse;
import com.DatShop.dto.catalog.BrandUpdateRequest;
import com.DatShop.service.BrandService;
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

@Tag(name = "Admin Brands")
@RestController
@RequestMapping("/api/admin/brands")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminBrandController {

    private final BrandService brandService;

    public AdminBrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @Operation(summary = "List all brands including inactive", description = "Roles: ADMIN, STAFF")
    @GetMapping
    public List<BrandResponse> listAll() {
        return brandService.listAll();
    }

    @Operation(summary = "Create a brand", description = "Roles: ADMIN")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<BrandResponse> create(@Valid @RequestBody BrandCreateRequest request) {
        BrandResponse created = brandService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/brands/" + created.id())).body(created);
    }

    @Operation(summary = "Update a brand", description = "Roles: ADMIN")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public BrandResponse update(@PathVariable Long id, @Valid @RequestBody BrandUpdateRequest request) {
        return brandService.update(id, request);
    }
}
