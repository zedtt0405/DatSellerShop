package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.DatShop.dto.catalog.BrandCreateRequest;
import com.DatShop.dto.catalog.BrandResponse;
import com.DatShop.dto.catalog.CategoryCreateRequest;
import com.DatShop.dto.catalog.CategoryResponse;
import com.DatShop.dto.catalog.CategoryUpdateRequest;
import com.DatShop.dto.catalog.ColorCreateRequest;
import com.DatShop.dto.catalog.ColorResponse;
import com.DatShop.dto.catalog.ProductCreateRequest;
import com.DatShop.dto.catalog.ProductDetailResponse;
import com.DatShop.dto.catalog.ProductImageCreateRequest;
import com.DatShop.dto.catalog.ProductImageResponse;
import com.DatShop.dto.catalog.ProductResponse;
import com.DatShop.dto.catalog.ProductVariantCreateRequest;
import com.DatShop.dto.catalog.ProductVariantResponse;
import com.DatShop.dto.catalog.ProductVariantUpdateRequest;
import com.DatShop.dto.catalog.SizeCreateRequest;
import com.DatShop.dto.catalog.SizeResponse;
import com.DatShop.exception.ApiError;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.DuplicateResourceException;
import com.DatShop.exception.GlobalExceptionHandler;
import com.DatShop.exception.ResourceNotFoundException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CatalogServiceIntegrationTest {

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

    @Autowired
    private GlobalExceptionHandler exceptionHandler;

    @Test
    void createsRootAndChildCategoryWithSharedName() {
        String suffix = suffix();
        CategoryResponse men = categoryService.create(categoryRequest("Shirts " + suffix, "shirts-a-" + suffix, null));
        CategoryResponse shirts = categoryService.create(
                categoryRequest("Shirts " + suffix, "shirts-b-" + suffix, men.id()));

        assertThat(men.parentCategoryId()).isNull();
        assertThat(shirts.parentCategoryId()).isEqualTo(men.id());
        assertThat(shirts.status()).isEqualTo(CatalogStatus.ACTIVE);
        assertThat(shirts.createdAt()).isNotNull();
        assertThat(shirts.updatedAt()).isNotNull();
        assertThat(categoryService.getById(shirts.id()).name()).isEqualTo("Shirts " + suffix);
    }

    @Test
    void rejectsDuplicateCategorySlug() {
        String suffix = suffix();
        categoryService.create(categoryRequest("Men " + suffix, "men-" + suffix, null));

        assertThatThrownBy(() -> categoryService.create(categoryRequest("Women " + suffix, "men-" + suffix, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsCategoryAsItsOwnParent() {
        String suffix = suffix();
        CategoryResponse category = categoryService.create(categoryRequest("Men " + suffix, "men-" + suffix, null));

        assertThatThrownBy(() -> categoryService.update(category.id(), categoryUpdate(category, category.id())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("own parent");
    }

    @Test
    void rejectsCategoryParentCycle() {
        String suffix = suffix();
        CategoryResponse first = categoryService.create(categoryRequest("A " + suffix, "a-" + suffix, null));
        CategoryResponse second = categoryService.create(categoryRequest("B " + suffix, "b-" + suffix, first.id()));
        CategoryResponse third = categoryService.create(categoryRequest("C " + suffix, "c-" + suffix, second.id()));

        assertThatThrownBy(() -> categoryService.update(first.id(), categoryUpdate(first, third.id())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void createsBrandAndRejectsDuplicateNameAndSlug() {
        String suffix = suffix();
        BrandResponse created = brandService.create(new BrandCreateRequest(
                "Nike " + suffix, "nike-" + suffix, "Sport", "https://cdn.example/nike.png", CatalogStatus.ACTIVE));

        assertThat(created.logoUrl()).isEqualTo("https://cdn.example/nike.png");
        assertThat(brandService.getById(created.id()).slug()).isEqualTo("nike-" + suffix);

        assertThatThrownBy(() -> brandService.create(new BrandCreateRequest(
                "Nike " + suffix, "nike-other-" + suffix, null, null, CatalogStatus.ACTIVE)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsDuplicateBrandSlug() {
        String suffix = suffix();
        brandService.create(new BrandCreateRequest(
                "Adidas " + suffix, "adidas-" + suffix, null, null, CatalogStatus.ACTIVE));

        assertThatThrownBy(() -> brandService.create(new BrandCreateRequest(
                "Adidas Other " + suffix, "adidas-" + suffix, null, null, CatalogStatus.ACTIVE)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("slug");
    }

    @Test
    void createsProductWithPriceCategoryAndBrand() {
        String suffix = suffix();
        Long categoryId = categoryService.create(categoryRequest("Tops " + suffix, "tops-" + suffix, null)).id();
        Long brandId = brandService.create(new BrandCreateRequest(
                "Uniqlo " + suffix, "uniqlo-" + suffix, null, null, CatalogStatus.ACTIVE)).id();

        ProductResponse product = product(suffix, "oxford-" + suffix, categoryId, brandId, "890000.00", "750000.50",
                ProductStatus.ACTIVE);
        ProductResponse samePrice = product(suffix + "b", "oxford-b-" + suffix, categoryId, brandId, "100.00", "100.00",
                ProductStatus.ACTIVE);
        ProductResponse noSale = product(suffix + "c", "oxford-c-" + suffix, categoryId, brandId, "100.00", null,
                ProductStatus.DRAFT);

        assertThat(product.category().id()).isEqualTo(categoryId);
        assertThat(product.brand().id()).isEqualTo(brandId);
        assertThat(product.price()).isEqualByComparingTo("890000.00");
        assertThat(product.salePrice()).isEqualByComparingTo("750000.50");
        assertThat(product.effectivePrice()).isEqualByComparingTo("750000.50");
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(samePrice.effectivePrice()).isEqualByComparingTo("100.00");
        assertThat(noSale.salePrice()).isNull();
        assertThat(noSale.effectivePrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsSalePriceGreaterThanPrice() {
        String suffix = suffix();
        Long categoryId = categoryService.create(categoryRequest("Tops " + suffix, "tops-" + suffix, null)).id();
        Long brandId = activeBrand(suffix);

        assertThatThrownBy(() -> product(suffix, "shirt-" + suffix, categoryId, brandId, "100.00", "100.01",
                ProductStatus.ACTIVE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("salePrice");
    }

    @Test
    void rejectsMissingCategory() {
        Long brandId = activeBrand(suffix());

        assertThatThrownBy(() -> product("x", "missing-cat-" + suffix(), -1L, brandId, "10.00", null,
                ProductStatus.DRAFT))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
    }

    @Test
    void rejectsMissingBrand() {
        String suffix = suffix();
        Long categoryId = categoryService.create(categoryRequest("Tops " + suffix, "tops-" + suffix, null)).id();

        assertThatThrownBy(() -> product(suffix, "missing-brand-" + suffix, categoryId, -1L, "10.00", null,
                ProductStatus.DRAFT))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Brand");
    }

    @Test
    void rejectsDuplicateProductSlug() {
        String suffix = suffix();
        Long categoryId = categoryService.create(categoryRequest("Tops " + suffix, "tops-" + suffix, null)).id();
        Long brandId = activeBrand(suffix);
        product(suffix, "shirt-" + suffix, categoryId, brandId, "10.00", null, ProductStatus.DRAFT);

        assertThatThrownBy(() -> product(suffix + "b", "shirt-" + suffix, categoryId, brandId, "10.00", null,
                ProductStatus.DRAFT))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsActiveProductWithInactiveCategory() {
        String suffix = suffix();
        Long categoryId = categoryService.create(new CategoryCreateRequest(
                "Old " + suffix, "old-" + suffix, null, CatalogStatus.INACTIVE, null)).id();
        Long brandId = activeBrand(suffix);

        assertThatThrownBy(() -> product(suffix, "shirt-" + suffix, categoryId, brandId, "10.00", null,
                ProductStatus.ACTIVE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("category");
    }

    @Test
    void allowsDraftProductWithInactiveCategory() {
        String suffix = suffix();
        Long categoryId = categoryService.create(new CategoryCreateRequest(
                "Old " + suffix, "old-" + suffix, null, CatalogStatus.INACTIVE, null)).id();
        Long brandId = activeBrand(suffix);

        ProductResponse draft = product(suffix, "draft-" + suffix, categoryId, brandId, "10.00", null,
                ProductStatus.DRAFT);

        assertThat(draft.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(draft.category().id()).isEqualTo(categoryId);
    }

    @Test
    void rejectsDuplicateSize() {
        String suffix = suffix();
        SizeResponse size = sizeService.create(new SizeCreateRequest("M-" + suffix, 3, true));
        assertThat(size.displayOrder()).isEqualTo(3);

        assertThatThrownBy(() -> sizeService.create(new SizeCreateRequest("M-" + suffix, 1, true)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsDuplicateColor() {
        String suffix = suffix();
        ColorResponse color = colorService.create(new ColorCreateRequest("Black-" + suffix, "#0000ff", true));
        assertThat(color.hexCode()).isEqualTo("#0000FF");

        assertThatThrownBy(() -> colorService.create(new ColorCreateRequest("Black-" + suffix, "#111111", true)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsInvalidHex() {
        assertThatThrownBy(() -> colorService.create(new ColorCreateRequest("Red-" + suffix(), "red", true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("hexCode");
    }

    @Test
    void createsVariantAndKeepsOnePrimaryImage() {
        String suffix = suffix();
        ProductResponse product = savedProduct(suffix);
        SizeResponse medium = sizeService.create(new SizeCreateRequest("M-" + suffix, 2, true));
        SizeResponse large = sizeService.create(new SizeCreateRequest("L-" + suffix, 3, true));
        ColorResponse black = colorService.create(new ColorCreateRequest("Black-" + suffix, "#000000", true));

        ProductVariantResponse created = productVariantService.create(variantRequest(
                product.id(), medium.id(), black.id(), "SKU-" + suffix, 12));
        ProductVariantResponse updated = productVariantService.update(created.id(), new ProductVariantUpdateRequest(
                product.id(), medium.id(), black.id(), created.sku(), 9, true));
        productVariantService.create(variantRequest(product.id(), large.id(), black.id(), "SKU-L-" + suffix, 4));
        ProductVariantResponse plain = productVariantService.create(variantRequest(
                product.id(), null, null, "SKU-PLAIN-" + suffix, 0));

        assertThat(updated.stockQuantity()).isEqualTo(9);
        assertThat(updated.size().name()).isEqualTo("M-" + suffix);
        assertThat(updated.color().hexCode()).isEqualTo("#000000");
        assertThat(plain.size()).isNull();
        assertThat(plain.color()).isNull();
        assertThat(productVariantService.listByProduct(product.id())).hasSize(3);

        ProductImageResponse first = productService.addImage(product.id(), image(suffix, "a", false, 2));
        assertThat(first.isPrimary()).isTrue();
        ProductImageResponse second = productService.addImage(product.id(), image(suffix, "b", true, 1));
        assertThat(second.isPrimary()).isTrue();
        ProductImageResponse promoted = productService.updateImage(first.id(), image(suffix, "a", true, 2));

        assertThat(promoted.isPrimary()).isTrue();
        assertThat(productService.listImages(product.id()))
                .extracting(ProductImageResponse::sortOrder)
                .containsExactly(1, 2);
        assertThat(productService.listImages(product.id()))
                .extracting(ProductImageResponse::isPrimary)
                .containsExactly(false, true);

        ProductDetailResponse detail = productService.getDetail(product.id());
        assertThat(detail.images()).hasSize(2);
        assertThat(detail.variants()).hasSize(3);
        assertThat(detail.images().stream().filter(ProductImageResponse::isPrimary)).hasSize(1);
    }

    @Test
    void rejectsDuplicateSku() {
        String suffix = suffix();
        ProductResponse product = savedProduct(suffix);
        productVariantService.create(variantRequest(product.id(), null, null, "SKU-DUP-" + suffix, 3));

        assertThatThrownBy(() -> productVariantService.create(
                variantRequest(product.id(), null, null, "SKU-DUP-" + suffix, 1)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("SKU");
    }

    @Test
    void rejectsNegativeStock() {
        ProductResponse product = savedProduct(suffix());

        assertThatThrownBy(() -> productVariantService.create(
                variantRequest(product.id(), null, null, "SKU-" + suffix(), -1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("stockQuantity");
    }

    @Test
    void rejectsSemanticDuplicateVariant() {
        String suffix = suffix();
        ProductResponse product = savedProduct(suffix);
        SizeResponse medium = sizeService.create(new SizeCreateRequest("M-" + suffix, 1, true));
        ColorResponse black = colorService.create(new ColorCreateRequest("Black-" + suffix, "#000000", true));
        productVariantService.create(variantRequest(product.id(), medium.id(), black.id(), "SKU-A-" + suffix, 1));

        assertThatThrownBy(() -> productVariantService.create(
                variantRequest(product.id(), medium.id(), black.id(), "SKU-B-" + suffix, 1)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("size and color");
    }

    @Test
    void rejectsDuplicateVariantWhenSizeAndColorAreNull() {
        String suffix = suffix();
        ProductResponse product = savedProduct(suffix);
        productVariantService.create(variantRequest(product.id(), null, null, "SKU-A-" + suffix, 1));

        assertThatThrownBy(() -> productVariantService.create(
                variantRequest(product.id(), null, null, "SKU-B-" + suffix, 1)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsDuplicateVariantWhenSizeIsNullAndColorMatches() {
        String suffix = suffix();
        ProductResponse product = savedProduct(suffix);
        ColorResponse black = colorService.create(new ColorCreateRequest("Black-" + suffix, "#000000", true));
        productVariantService.create(variantRequest(product.id(), null, black.id(), "SKU-A-" + suffix, 1));

        assertThatThrownBy(() -> productVariantService.create(
                variantRequest(product.id(), null, black.id(), "SKU-B-" + suffix, 1)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void mapsCatalogExceptionsToHttpStatusWithoutStackTrace() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalog/categories/1");

        ResponseEntity<ApiError> notFound = exceptionHandler.handleNotFound(
                new ResourceNotFoundException("Category not found"), request);
        ResponseEntity<ApiError> conflict = exceptionHandler.handleDuplicateResource(
                new DuplicateResourceException("Category slug already exists"), request);
        ResponseEntity<ApiError> invalid = exceptionHandler.handleBusinessRule(
                new BusinessRuleException("salePrice must not be greater than price"), request);

        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notFound.getBody().message()).isEqualTo("Category not found");
        assertThat(conflict.getBody().message()).isEqualTo("Category slug already exists");
        assertThat(invalid.getBody().message()).isEqualTo("salePrice must not be greater than price");
        assertThat(notFound.getBody().message()).doesNotContain("Exception");
        assertThat(invalid.getBody().path()).isEqualTo("/api/catalog/categories/1");
    }

    private ProductResponse savedProduct(String suffix) {
        Long categoryId = categoryService.create(categoryRequest("Tops " + suffix, "tops-" + suffix, null)).id();
        return product(suffix, "shirt-" + suffix, categoryId, activeBrand(suffix), "100000.00", null, ProductStatus.DRAFT);
    }

    private Long activeBrand(String suffix) {
        return brandService.create(new BrandCreateRequest(
                "Brand " + suffix, "brand-" + suffix, null, null, CatalogStatus.ACTIVE)).id();
    }

    private ProductResponse product(
            String suffix,
            String slug,
            Long categoryId,
            Long brandId,
            String price,
            String salePrice,
            ProductStatus status) {
        return productService.create(new ProductCreateRequest(
                "Shirt " + suffix,
                slug,
                "Cotton",
                new BigDecimal(price),
                salePrice == null ? null : new BigDecimal(salePrice),
                categoryId,
                brandId,
                status,
                false));
    }

    private static CategoryCreateRequest categoryRequest(String name, String slug, Long parentId) {
        return new CategoryCreateRequest(name, slug, null, CatalogStatus.ACTIVE, parentId);
    }

    private static CategoryUpdateRequest categoryUpdate(CategoryResponse category, Long parentId) {
        return new CategoryUpdateRequest(
                category.name(), category.slug(), category.description(), category.status(), parentId);
    }

    private static ProductVariantCreateRequest variantRequest(
            Long productId, Long sizeId, Long colorId, String sku, int stock) {
        return new ProductVariantCreateRequest(productId, sizeId, colorId, sku, stock, true);
    }

    private static ProductImageCreateRequest image(String suffix, String name, boolean primary, int sortOrder) {
        return new ProductImageCreateRequest(
                "https://cdn.example/" + name + "-" + suffix + ".jpg", name, primary, sortOrder);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
