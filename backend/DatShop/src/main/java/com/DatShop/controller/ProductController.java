package com.DatShop.controller;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.catalog.ProductCardResponse;
import com.DatShop.dto.catalog.ProductDetailResponse;
import com.DatShop.dto.catalog.ProductSearchCriteria;
import com.DatShop.exception.ApiError;
import com.DatShop.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Products")
@SecurityRequirements
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "List publicly visible products")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged product cards"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameter",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public PageResponse<ProductCardResponse> list(
            @Parameter(description = "Case-insensitive search against product name and slug")
            @RequestParam(required = false) String q,
            @Parameter(description = "Exact category slug. Does not include child categories.")
            @RequestParam(required = false) String category,
            @Parameter(description = "Exact brand slug")
            @RequestParam(required = false) String brand,
            @Parameter(description = "Clothing/shoe size name such as M or 42. Named sizeName because size is the pagination page size.")
            @RequestParam(required = false) String sizeName,
            @Parameter(description = "Color name such as Black")
            @RequestParam(required = false) String color,
            @Parameter(description = "true = at least one active variant with stock > 0; false = no such variant")
            @RequestParam(required = false) Boolean inStock,
            @Parameter(description = "true or false featured flag")
            @RequestParam(required = false) Boolean featured,
            @Parameter(description = "Minimum customer payable price (salePrice if present, otherwise price)")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum customer payable price (salePrice if present, otherwise price)")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, maximum 100")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Whitelist: name, price, createdAt. Example: createdAt,desc. price sorts Product.price, not sale price.")
            @RequestParam(required = false) String sort) {
        return productService.listPublic(new ProductSearchCriteria(
                q,
                category,
                brand,
                sizeName,
                color,
                inStock,
                featured,
                minPrice,
                maxPrice,
                page,
                size,
                sort));
    }

    @Operation(summary = "Get a publicly visible product by slug")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product detail"),
            @ApiResponse(responseCode = "404", description = "Product is missing or not publicly visible",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{slug}")
    public ProductDetailResponse getBySlug(@PathVariable String slug) {
        return productService.getPublicBySlug(slug);
    }
}
