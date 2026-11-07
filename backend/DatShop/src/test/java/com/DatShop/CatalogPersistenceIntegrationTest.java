package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.DatShop.model.Brand;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Category;
import com.DatShop.model.Color;
import com.DatShop.model.Product;
import com.DatShop.model.ProductImage;
import com.DatShop.model.ProductStatus;
import com.DatShop.model.ProductVariant;
import com.DatShop.model.Size;
import com.DatShop.repository.BrandRepository;
import com.DatShop.repository.CategoryRepository;
import com.DatShop.repository.ColorRepository;
import com.DatShop.repository.ProductImageRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.ProductVariantRepository;
import com.DatShop.repository.SizeRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CatalogPersistenceIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private SizeRepository sizeRepository;

    @Autowired
    private ColorRepository colorRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Test
    void persistsRootAndChildCategory() {
        String suffix = suffix();
        Category men = categoryRepository.save(category("Men " + suffix, "men-" + suffix));
        Category shirts = category("Shirts " + suffix, "shirts-" + suffix);
        shirts.setParentCategory(men);
        categoryRepository.save(shirts);

        Category loaded = categoryRepository.findBySlug("shirts-" + suffix).orElseThrow();
        assertThat(loaded.getParentCategory().getSlug()).isEqualTo("men-" + suffix);
        assertThat(loaded.getStatus()).isEqualTo(CatalogStatus.ACTIVE);
        assertThat(categoryRepository.existsBySlug("men-" + suffix)).isTrue();
    }

    @Test
    void persistsBrandBySlugAndName() {
        String suffix = suffix();
        Brand brand = brand("Nike " + suffix, "nike-" + suffix);
        brandRepository.save(brand);

        Brand loaded = brandRepository.findBySlug("nike-" + suffix).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Nike " + suffix);
        assertThat(brandRepository.existsByName("Nike " + suffix)).isTrue();
        assertThat(loaded.getStatus()).isEqualTo(CatalogStatus.ACTIVE);
    }

    @Test
    void persistsProductWithCategoryBrandPriceAndStatus() {
        String suffix = suffix();
        Category category = categoryRepository.save(category("Tops " + suffix, "tops-" + suffix));
        Brand brand = brandRepository.save(brand("Uniqlo " + suffix, "uniqlo-" + suffix));

        Product product = product("Oxford Shirt " + suffix, "oxford-" + suffix, category, brand);
        product.setPrice(new BigDecimal("890000.00"));
        product.setSalePrice(new BigDecimal("750000.50"));
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);

        Product loaded = productRepository.findBySlug("oxford-" + suffix).orElseThrow();
        assertThat(loaded.getCategory().getId()).isEqualTo(category.getId());
        assertThat(loaded.getBrand().getId()).isEqualTo(brand.getId());
        assertThat(loaded.getPrice()).isEqualByComparingTo("890000.00");
        assertThat(loaded.getSalePrice()).isEqualByComparingTo("750000.50");
        assertThat(loaded.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(productRepository.existsBySlug("oxford-" + suffix)).isTrue();
    }

    @Test
    void persistsSizeAndColor() {
        String suffix = suffix();
        Size size = new Size();
        size.setName("M-" + suffix);
        size.setDisplayOrder(3);
        sizeRepository.save(size);

        Color color = new Color();
        color.setName("Black-" + suffix);
        color.setHexCode("#000000");
        colorRepository.save(color);

        assertThat(sizeRepository.findByName("M-" + suffix)).isPresent();
        assertThat(sizeRepository.existsByName("M-" + suffix)).isTrue();
        assertThat(colorRepository.findByName("Black-" + suffix).orElseThrow().getHexCode()).isEqualTo("#000000");
        assertThat(colorRepository.existsByName("Black-" + suffix)).isTrue();
    }

    @Test
    void persistsVariantStockAndImagesInSortOrder() {
        String suffix = suffix();
        Product product = savedProduct(suffix);
        Size size = sizeRepository.save(size("M-" + suffix));
        Color color = colorRepository.save(color("Black-" + suffix));

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSize(size);
        variant.setColor(color);
        variant.setSku("SKU-" + suffix);
        variant.setStockQuantity(12);
        productVariantRepository.save(variant);

        ProductImage second = image(product, "https://cdn.example/b-" + suffix + ".jpg", 2);
        ProductImage first = image(product, "https://cdn.example/a-" + suffix + ".jpg", 1);
        first.setIsPrimary(true);
        productImageRepository.save(second);
        productImageRepository.save(first);

        ProductVariant loaded = productVariantRepository.findBySku("SKU-" + suffix).orElseThrow();
        assertThat(loaded.getProduct().getId()).isEqualTo(product.getId());
        assertThat(loaded.getSize().getName()).isEqualTo("M-" + suffix);
        assertThat(loaded.getColor().getName()).isEqualTo("Black-" + suffix);
        assertThat(loaded.getStockQuantity()).isEqualTo(12);
        assertThat(productVariantRepository.existsBySku("SKU-" + suffix)).isTrue();
        assertThat(productVariantRepository.findByProduct_Id(product.getId())).hasSize(1);

        assertThat(productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(product.getId()))
                .extracting(ProductImage::getSortOrder)
                .containsExactly(1, 2);
    }

    @Test
    void rejectsDuplicateSku() {
        String suffix = suffix();
        Product product = savedProduct(suffix);
        productVariantRepository.saveAndFlush(variant(product, "SKU-DUP-" + suffix, 3));

        ProductVariant duplicate = variant(product, "SKU-DUP-" + suffix, 1);
        assertThatThrownBy(() -> productVariantRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Product savedProduct(String suffix) {
        Category category = categoryRepository.save(category("Cat " + suffix, "cat-" + suffix));
        Brand brand = brandRepository.save(brand("Brand " + suffix, "brand-" + suffix));
        return productRepository.save(product("Product " + suffix, "product-" + suffix, category, brand));
    }

    private static Category category(String name, String slug) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        category.setStatus(CatalogStatus.ACTIVE);
        return category;
    }

    private static Brand brand(String name, String slug) {
        Brand brand = new Brand();
        brand.setName(name);
        brand.setSlug(slug);
        brand.setStatus(CatalogStatus.ACTIVE);
        return brand;
    }

    private static Product product(String name, String slug, Category category, Brand brand) {
        Product product = new Product();
        product.setName(name);
        product.setSlug(slug);
        product.setPrice(new BigDecimal("100000.00"));
        product.setCategory(category);
        product.setBrand(brand);
        product.setStatus(ProductStatus.DRAFT);
        return product;
    }

    private static Size size(String name) {
        Size size = new Size();
        size.setName(name);
        size.setDisplayOrder(1);
        return size;
    }

    private static Color color(String name) {
        Color color = new Color();
        color.setName(name);
        color.setHexCode("#111111");
        return color;
    }

    private static ProductVariant variant(Product product, String sku, int stock) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setStockQuantity(stock);
        return variant;
    }

    private static ProductImage image(Product product, String url, int sortOrder) {
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(url);
        image.setSortOrder(sortOrder);
        return image;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
