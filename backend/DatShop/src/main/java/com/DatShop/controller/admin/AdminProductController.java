package com.DatShop.controller.admin;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.catalog.ProductCreateRequest;
import com.DatShop.dto.catalog.ProductDetailResponse;
import com.DatShop.dto.catalog.ProductImageCreateRequest;
import com.DatShop.dto.catalog.ProductImageResponse;
import com.DatShop.dto.catalog.ProductResponse;
import com.DatShop.dto.catalog.ProductUpdateRequest;
import com.DatShop.dto.catalog.ProductVariantCreateRequest;
import com.DatShop.dto.catalog.ProductVariantResponse;
import com.DatShop.dto.catalog.ProductVariantUpdateRequest;
import com.DatShop.dto.catalog.StockUpdateRequest;
import com.DatShop.exception.ApiError;
import com.DatShop.model.ProductStatus;
import com.DatShop.service.ProductService;
import com.DatShop.service.ProductVariantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Products")
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminProductController {

    private final ProductService productService;
    private final ProductVariantService productVariantService;

    public AdminProductController(ProductService productService, ProductVariantService productVariantService) {
        this.productService = productService;
        this.productVariantService = productVariantService;
    }

    @Operation(summary = "List all products including draft and inactive", description = "Roles: ADMIN, STAFF")
    @GetMapping
    public PageResponse<ProductResponse> list(
            @Parameter(description = "Optional product status filter")
            @RequestParam(required = false) ProductStatus status,
            @Parameter(description = "Optional case-insensitive name/slug search")
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.listManagement(status, q, page, size);
    }

    @Operation(summary = "Get product management detail", description = "Roles: ADMIN, STAFF. Includes inactive variants.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product detail"),
            @ApiResponse(responseCode = "404", description = "Not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ProductDetailResponse getById(@PathVariable Long id) {
        return productService.getDetail(id);
    }

    @Operation(summary = "Create a product", description = "Roles: ADMIN, STAFF")
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/products/" + created.id())).body(created);
    }

    @Operation(summary = "Update a product", description = "Roles: ADMIN, STAFF")
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        return productService.update(id, request);
    }

    @Operation(summary = "List product images", description = "Roles: ADMIN, STAFF")
    @GetMapping("/{productId}/images")
    public List<ProductImageResponse> listImages(@PathVariable Long productId) {
        return productService.listImages(productId);
    }

    @Operation(summary = "Add a product image URL", description = "Roles: ADMIN, STAFF")
    @PostMapping("/{productId}/images")
    public ResponseEntity<ProductImageResponse> addImage(
            @PathVariable Long productId,
            @Valid @RequestBody ProductImageCreateRequest request) {
        ProductImageResponse created = productService.addImage(productId, request);
        return ResponseEntity.created(URI.create(
                "/api/admin/products/" + productId + "/images/" + created.id())).body(created);
    }

    @Operation(summary = "Update a product image", description = "Roles: ADMIN, STAFF")
    @PutMapping("/{productId}/images/{imageId}")
    public ProductImageResponse updateImage(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @Valid @RequestBody ProductImageCreateRequest request) {
        return productService.updateImage(productId, imageId, request);
    }

    @Operation(summary = "Delete a product image", description = "Roles: ADMIN, STAFF")
    @DeleteMapping("/{productId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable Long productId, @PathVariable Long imageId) {
        productService.deleteImage(productId, imageId);
    }

    @Operation(summary = "List product variants including inactive", description = "Roles: ADMIN, STAFF")
    @GetMapping("/{productId}/variants")
    public List<ProductVariantResponse> listVariants(@PathVariable Long productId) {
        return productVariantService.listByProduct(productId);
    }

    @Operation(summary = "Create a product variant", description = "Roles: ADMIN, STAFF")
    @PostMapping("/{productId}/variants")
    public ResponseEntity<ProductVariantResponse> createVariant(
            @PathVariable Long productId,
            @Valid @RequestBody ProductVariantCreateRequest request) {
        ProductVariantResponse created = productVariantService.create(productId, request);
        return ResponseEntity.created(URI.create(
                "/api/admin/products/" + productId + "/variants/" + created.id())).body(created);
    }

    @Operation(summary = "Update a product variant", description = "Roles: ADMIN, STAFF. Product ownership is immutable.")
    @PutMapping("/{productId}/variants/{variantId}")
    public ProductVariantResponse updateVariant(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody ProductVariantUpdateRequest request) {
        return productVariantService.update(productId, variantId, request);
    }

    @Operation(summary = "Update variant stock only", description = "Roles: ADMIN, STAFF")
    @PatchMapping("/{productId}/variants/{variantId}/stock")
    public ProductVariantResponse updateStock(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody StockUpdateRequest request) {
        return productVariantService.updateStock(productId, variantId, request);
    }
}
