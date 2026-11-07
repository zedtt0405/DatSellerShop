package com.DatShop;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.dto.catalog.BrandCreateRequest;
import com.DatShop.dto.catalog.BrandUpdateRequest;
import com.DatShop.dto.catalog.CategoryCreateRequest;
import com.DatShop.dto.catalog.CategoryResponse;
import com.DatShop.dto.catalog.CategoryUpdateRequest;
import com.DatShop.dto.catalog.ColorCreateRequest;
import com.DatShop.dto.catalog.ProductCreateRequest;
import com.DatShop.dto.catalog.ProductImageCreateRequest;
import com.DatShop.dto.catalog.ProductResponse;
import com.DatShop.dto.catalog.ProductVariantCreateRequest;
import com.DatShop.dto.catalog.SizeCreateRequest;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.ProductStatus;
import com.DatShop.service.BrandService;
import com.DatShop.service.CategoryService;
import com.DatShop.service.ColorService;
import com.DatShop.service.ProductService;
import com.DatShop.service.ProductVariantService;
import com.DatShop.service.SizeService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private SizeService sizeService;

    @Autowired
    private ColorService colorService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductVariantService productVariantService;

    @Test
    void publicCatalogGetsAreAccessibleWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isOk());
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/brands")).andExpect(status().isOk());
        mockMvc.perform(get("/api/sizes")).andExpect(status().isOk());
        mockMvc.perform(get("/api/colors")).andExpect(status().isOk());
    }

    @Test
    void listsOnlyPubliclyVisibleActiveProducts() throws Exception {
        String suffix = suffix();
        Long activeCategory = activeCategory("visible-cat-" + suffix);
        Long activeBrand = activeBrand("visible-brand-" + suffix);

        ProductResponse visible = product("Visible " + suffix, "visible-" + suffix, activeCategory, activeBrand,
                "100000.00", null, ProductStatus.ACTIVE, true);
        product("Draft " + suffix, "draft-" + suffix, activeCategory, activeBrand, "100000.00", null,
                ProductStatus.DRAFT, false);
        product("Inactive " + suffix, "inactive-" + suffix, activeCategory, activeBrand, "100000.00", null,
                ProductStatus.INACTIVE, false);

        mockMvc.perform(get("/api/products").param("q", suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(visible.slug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem("draft-" + suffix))))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem("inactive-" + suffix))));
    }

    @Test
    void hidesActiveProductWhenCategoryLaterBecomesInactive() throws Exception {
        String suffix = suffix();
        CategoryResponse category = categoryService.create(new CategoryCreateRequest(
                "Later " + suffix, "later-" + suffix, null, CatalogStatus.ACTIVE, null));
        ProductResponse product = product("Later " + suffix, "later-product-" + suffix, category.id(),
                activeBrand("later-brand-" + suffix), "100000.00", null, ProductStatus.ACTIVE, false);

        categoryService.update(category.id(), new CategoryUpdateRequest(
                category.name(), category.slug(), null, CatalogStatus.INACTIVE, null));

        mockMvc.perform(get("/api/products").param("q", suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(product.slug()))));
        mockMvc.perform(get("/api/products/" + product.slug()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found"));

        String brandSuffix = suffix();
        Long brandCategory = activeCategory("brand-cat-" + brandSuffix);
        var liveBrand = brandService.create(new BrandCreateRequest(
                "Live " + brandSuffix, "live-" + brandSuffix, null, null, CatalogStatus.ACTIVE));
        ProductResponse brandProduct = product("Brand later " + brandSuffix, "brand-later-" + brandSuffix,
                brandCategory, liveBrand.id(), "100000.00", null, ProductStatus.ACTIVE, false);
        brandService.update(liveBrand.id(), new BrandUpdateRequest(
                liveBrand.name(), liveBrand.slug(), null, null, CatalogStatus.INACTIVE));

        mockMvc.perform(get("/api/products").param("q", brandSuffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(brandProduct.slug()))));
        mockMvc.perform(get("/api/products/" + brandProduct.slug()))
                .andExpect(status().isNotFound());
    }

    @Test
    void productDetailReturnsActiveDataAndHidesUnpublished() throws Exception {
        String suffix = suffix();
        Long categoryId = activeCategory("detail-cat-" + suffix);
        Long brandId = activeBrand("detail-brand-" + suffix);
        ProductResponse active = product("Detail " + suffix, "detail-" + suffix, categoryId, brandId,
                "200000.00", "150000.00", ProductStatus.ACTIVE, false);
        ProductResponse draft = product("Hidden " + suffix, "hidden-detail-" + suffix, categoryId, brandId,
                "200000.00", null, ProductStatus.DRAFT, false);

        Long medium = sizeService.create(new SizeCreateRequest("M-" + suffix, 1, true)).id();
        Long large = sizeService.create(new SizeCreateRequest("L-" + suffix, 2, true)).id();
        Long black = colorService.create(new ColorCreateRequest("Black-" + suffix, "#000000", true)).id();
        productVariantService.create(new ProductVariantCreateRequest(
                active.id(), medium, black, "SKU-A-" + suffix, 7, true));
        productVariantService.create(new ProductVariantCreateRequest(
                active.id(), large, black, "SKU-B-" + suffix, 3, false));
        productService.addImage(active.id(), new ProductImageCreateRequest(
                "https://cdn.example/b-" + suffix + ".jpg", "second", false, 2));
        productService.addImage(active.id(), new ProductImageCreateRequest(
                "https://cdn.example/a-" + suffix + ".jpg", "first", true, 1));

        mockMvc.perform(get("/api/products/" + active.slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.slug").value(active.slug()))
                .andExpect(jsonPath("$.product.status").value("ACTIVE"))
                .andExpect(jsonPath("$.product.effectivePrice").value(150000.00))
                .andExpect(jsonPath("$.images[0].sortOrder").value(1))
                .andExpect(jsonPath("$.images[1].sortOrder").value(2))
                .andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].sku").value("SKU-A-" + suffix))
                .andExpect(jsonPath("$.variants[0].stockQuantity").value(7))
                .andExpect(jsonPath("$.variants[*].sku", not(hasItem("SKU-B-" + suffix))));

        mockMvc.perform(get("/api/products/" + draft.slug()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products/missing-" + suffix))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchIsCaseInsensitiveOnName() throws Exception {
        String suffix = suffix();
        Long categoryId = activeCategory("search-cat-" + suffix);
        Long brandId = activeBrand("search-brand-" + suffix);
        product("Nike Runner " + suffix, "nike-runner-" + suffix, categoryId, brandId,
                "500000.00", null, ProductStatus.ACTIVE, false);

        mockMvc.perform(get("/api/products").param("q", "nIkE-rUnNeR-" + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem("nike-runner-" + suffix)));
        mockMvc.perform(get("/api/products").param("q", "   "))
                .andExpect(status().isOk());
    }

    @Test
    void filtersByCategoryBrandSizeColorFeaturedStockAndPrice() throws Exception {
        String suffix = suffix();
        Long men = activeCategory("men-" + suffix);
        Long shirts = categoryService.create(new CategoryCreateRequest(
                "Shirts " + suffix, "shirts-" + suffix, null, CatalogStatus.ACTIVE, men)).id();
        Long nike = activeBrand("nike-" + suffix);
        Long otherBrand = activeBrand("other-" + suffix);
        Long medium = sizeService.create(new SizeCreateRequest("M-" + suffix, 1, true)).id();
        Long large = sizeService.create(new SizeCreateRequest("L-" + suffix, 2, true)).id();
        Long black = colorService.create(new ColorCreateRequest("Black-" + suffix, "#000000", true)).id();
        Long red = colorService.create(new ColorCreateRequest("Red-" + suffix, "#FF0000", true)).id();

        ProductResponse shirt = product("Oxford " + suffix, "oxford-" + suffix, shirts, nike,
                "200000.00", "100000.00", ProductStatus.ACTIVE, true);
        ProductResponse other = product("Tee " + suffix, "tee-" + suffix, men, otherBrand,
                "150000.00", null, ProductStatus.ACTIVE, false);
        productVariantService.create(new ProductVariantCreateRequest(
                shirt.id(), medium, black, "SKU-OX-" + suffix, 4, true));
        productVariantService.create(new ProductVariantCreateRequest(
                other.id(), large, red, "SKU-TEE-" + suffix, 0, true));

        mockMvc.perform(get("/api/products").param("category", "shirts-" + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(shirt.slug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(other.slug()))));
        mockMvc.perform(get("/api/products").param("brand", "nike-" + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(shirt.slug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(other.slug()))));
        mockMvc.perform(get("/api/products").param("sizeName", "M-" + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(shirt.slug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(other.slug()))));
        mockMvc.perform(get("/api/products").param("color", "black-" + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(shirt.slug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(other.slug()))));
        mockMvc.perform(get("/api/products").param("featured", "true").param("q", suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(shirt.slug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(other.slug()))));
        mockMvc.perform(get("/api/products").param("inStock", "true").param("q", suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(shirt.slug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(other.slug()))));
        mockMvc.perform(get("/api/products").param("inStock", "false").param("q", suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(other.slug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(shirt.slug()))));
        mockMvc.perform(get("/api/products")
                        .param("minPrice", "120000")
                        .param("maxPrice", "180000")
                        .param("q", suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", hasItem(other.slug())))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(shirt.slug()))));
    }

    @Test
    void paginatesAndRejectsInvalidQueryInput() throws Exception {
        String suffix = suffix();
        Long categoryId = activeCategory("page-cat-" + suffix);
        Long brandId = activeBrand("page-brand-" + suffix);
        product("Alpha " + suffix, "alpha-" + suffix, categoryId, brandId, "10000.00", null, ProductStatus.ACTIVE, false);
        product("Beta " + suffix, "beta-" + suffix, categoryId, brandId, "20000.00", null, ProductStatus.ACTIVE, false);
        product("Zeta " + suffix, "zeta-" + suffix, categoryId, brandId, "30000.00", null, ProductStatus.ACTIVE, false);

        mockMvc.perform(get("/api/products").param("q", suffix).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get("/api/products").param("q", suffix).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("size")));
        mockMvc.perform(get("/api/products").param("minPrice", "20").param("maxPrice", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("minPrice")));
        mockMvc.perform(get("/api/products").param("q", suffix).param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", contains("alpha-" + suffix, "beta-" + suffix, "zeta-" + suffix)));
        mockMvc.perform(get("/api/products").param("sort", "email,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort field")));
    }

    @Test
    void listsActiveLookupCatalogWithoutJwt() throws Exception {
        String suffix = suffix();
        categoryService.create(new CategoryCreateRequest(
                "Live " + suffix, "live-" + suffix, null, CatalogStatus.ACTIVE, null));
        categoryService.create(new CategoryCreateRequest(
                "Dead " + suffix, "dead-" + suffix, null, CatalogStatus.INACTIVE, null));
        brandService.create(new BrandCreateRequest(
                "LiveBrand " + suffix, "live-brand-" + suffix, null, null, CatalogStatus.ACTIVE));
        brandService.create(new BrandCreateRequest(
                "DeadBrand " + suffix, "dead-brand-" + suffix, null, null, CatalogStatus.INACTIVE));
        sizeService.create(new SizeCreateRequest("S-" + suffix, 1, true));
        sizeService.create(new SizeCreateRequest("X-" + suffix, 2, false));
        colorService.create(new ColorCreateRequest("White-" + suffix, "#FFFFFF", true));
        colorService.create(new ColorCreateRequest("Hidden-" + suffix, "#111111", false));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("live-" + suffix)))
                .andExpect(jsonPath("$[*].slug", not(hasItem("dead-" + suffix))));
        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("live-brand-" + suffix)))
                .andExpect(jsonPath("$[*].slug", not(hasItem("dead-brand-" + suffix))));
        mockMvc.perform(get("/api/sizes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("S-" + suffix)))
                .andExpect(jsonPath("$[*].name", not(hasItem("X-" + suffix))));
        mockMvc.perform(get("/api/colors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("White-" + suffix)))
                .andExpect(jsonPath("$[*].name", not(hasItem("Hidden-" + suffix))));
    }

    @Test
    void postProductsWithoutJwtRemainsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private Long activeCategory(String slug) {
        return category(slug, CatalogStatus.ACTIVE);
    }

    private Long category(String slug, CatalogStatus status) {
        return categoryService.create(new CategoryCreateRequest(slug, slug, null, status, null)).id();
    }

    private Long activeBrand(String slug) {
        return brand(slug, CatalogStatus.ACTIVE);
    }

    private Long brand(String slug, CatalogStatus status) {
        return brandService.create(new BrandCreateRequest(slug, slug, null, null, status)).id();
    }

    private ProductResponse product(
            String name,
            String slug,
            Long categoryId,
            Long brandId,
            String price,
            String salePrice,
            ProductStatus status,
            boolean featured) {
        return productService.create(new ProductCreateRequest(
                name,
                slug,
                "Cotton",
                new BigDecimal(price),
                salePrice == null ? null : new BigDecimal(salePrice),
                categoryId,
                brandId,
                status,
                featured));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
