package com.DatShop.controller.admin;

import com.DatShop.dto.catalog.ColorCreateRequest;
import com.DatShop.dto.catalog.ColorResponse;
import com.DatShop.dto.catalog.ColorUpdateRequest;
import com.DatShop.service.ColorService;
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

@Tag(name = "Admin Colors")
@RestController
@RequestMapping("/api/admin/colors")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminColorController {

    private final ColorService colorService;

    public AdminColorController(ColorService colorService) {
        this.colorService = colorService;
    }

    @Operation(summary = "List all colors including inactive", description = "Roles: ADMIN, STAFF")
    @GetMapping
    public List<ColorResponse> listAll() {
        return colorService.listAll();
    }

    @Operation(summary = "Create a color", description = "Roles: ADMIN")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ColorResponse> create(@Valid @RequestBody ColorCreateRequest request) {
        ColorResponse created = colorService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/colors/" + created.id())).body(created);
    }

    @Operation(summary = "Update a color", description = "Roles: ADMIN")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ColorResponse update(@PathVariable Long id, @Valid @RequestBody ColorUpdateRequest request) {
        return colorService.update(id, request);
    }
}
