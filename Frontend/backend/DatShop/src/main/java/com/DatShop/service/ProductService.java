package com.DatShop.service;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.catalog.ProductCardResponse;
import com.DatShop.dto.catalog.ProductCreateRequest;
import com.DatShop.dto.catalog.ProductDetailResponse;
import com.DatShop.dto.catalog.ProductImageCreateRequest;
import com.DatShop.dto.catalog.ProductImageResponse;
import com.DatShop.dto.catalog.ProductResponse;
import com.DatShop.dto.catalog.ProductSearchCriteria;
import com.DatShop.dto.catalog.ProductUpdateRequest;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.DuplicateResourceException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.CatalogMapper;
import com.DatShop.model.Brand;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Category;
import com.DatShop.model.Product;
import com.DatShop.model.ProductImage;
import com.DatShop.model.ProductStatus;
import com.DatShop.model.ProductVariant;
import com.DatShop.repository.BrandRepository;
import com.DatShop.repository.CategoryRepository;
import com.DatShop.repository.ProductImageRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository productVariantRepository;
    private final RequestValidator requestValidator;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository,
            ProductImageRepository productImageRepository,
            ProductVariantRepository productVariantRepository,
            RequestValidator requestValidator) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.productImageRepository = productImageRepository;
        this.productVariantRepository = productVariantRepository;
        this.requestValidator = requestValidator;
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        ProductCreateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        if (productRepository.existsBySlug(normalized.slug())) {
            throw new DuplicateResourceException("Product slug already exists");
        }
        Category category = requireCategory(normalized.categoryId());
        Brand brand = requireBrand(normalized.brandId());
        assertSalePrice(normalized.price(), normalized.salePrice());
        assertActiveReferences(normalized.status(), category, brand);
        Product product = new Product();
        apply(product, normalized, category, brand);
        return CatalogMapper.toProductResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        ProductUpdateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        Product product = requireProduct(id);
        if (productRepository.existsBySlugAndIdNot(normalized.slug(), id)) {
            throw new DuplicateResourceException("Product slug already exists");
        }
        Category category = requireCategory(normalized.categoryId());
        Brand brand = requireBrand(normalized.brandId());
        assertSalePrice(normalized.price(), normalized.salePrice());
        assertActiveReferences(normalized.status(), category, brand);
        apply(product, normalized.name(), normalized.slug(), normalized.description(), normalized.price(),
                normalized.salePrice(), category, brand, normalized.status(), normalized.featured());
        return CatalogMapper.toProductResponse(product);
    }

    public ProductDetailResponse getDetail(Long id) {
        Product product = requireProduct(id);
        return CatalogMapper.toProductDetail(
                product,
                productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(id),
                productVariantRepository.findByProduct_Id(id));
    }

    public PageResponse<ProductResponse> listManagement(ProductStatus status, String q, int page, int size) {
        Page<Product> result = productRepository.findAll(
                ProductSpecifications.management(status, Text.emptyToNull(q)),
                ProductQuerySupport.pageable(page, size));
        return ProductQuerySupport.pageResponse(
                result, result.getContent().stream().map(CatalogMapper::toProductResponse).toList());
    }

    public PageResponse<ProductCardResponse> listPublic(ProductSearchCriteria criteria) {
        ProductSearchCriteria normalized = ProductQuerySupport.normalize(criteria);
        Page<Product> page = productRepository.findAll(
                ProductSpecifications.publicCatalog(normalized),
                ProductQuerySupport.pageable(normalized));
        List<Long> productIds = page.getContent().stream().map(Product::getId).toList();
        Map<Long, String> primaryImages = primaryImageUrls(productIds);
        Set<Long> inStockIds = inStockIds(productIds);
        List<ProductCardResponse> content = page.getContent().stream()
                .map(product -> CatalogMapper.toProductCard(
                        product,
                        primaryImages.get(product.getId()),
                        inStockIds.contains(product.getId())))
                .toList();
        return ProductQuerySupport.pageResponse(page, content);
    }

    public ProductDetailResponse getPublicBySlug(String slug) {
        String normalized = Text.emptyToNull(slug);
        Product product = productRepository.findBySlug(normalized)
                .filter(CatalogAvailability::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        List<ProductImage> images = productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(product.getId());
        List<ProductVariant> variants = productVariantRepository.findByProduct_IdAndActiveTrue(product.getId());
        return CatalogMapper.toProductDetail(product, images, variants);
    }

    private Map<Long, String> primaryImageUrls(List<Long> productIds) {
        return PrimaryImages.urlsByProductId(productImageRepository, productIds);
    }

    private Set<Long> inStockIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(productVariantRepository.findInStockProductIds(productIds));
    }

    @Transactional
    public ProductImageResponse addImage(Long productId, ProductImageCreateRequest request) {
        ProductImageCreateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        assertSortOrder(normalized.sortOrder());
        Product product = lockProduct(productId);
        boolean primary = !productImageRepository.existsByProduct_Id(productId) || normalized.isPrimary();
        if (primary) {
            demotePrimaryImages(productId);
        }
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(normalized.imageUrl());
        image.setAltText(normalized.altText());
        image.setIsPrimary(primary);
        image.setSortOrder(normalized.sortOrder());
        return CatalogMapper.toImageResponse(productImageRepository.save(image));
    }

    @Transactional
    public ProductImageResponse updateImage(Long imageId, ProductImageCreateRequest request) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found"));
        return updateImage(image.getProduct().getId(), imageId, request);
    }

    @Transactional
    public ProductImageResponse updateImage(Long productId, Long imageId, ProductImageCreateRequest request) {
        ProductImageCreateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        assertSortOrder(normalized.sortOrder());
        lockProduct(productId);
        ProductImage image = requireOwnedImage(productId, imageId);
        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        if (normalized.isPrimary()) {
            demotePrimaryImages(productId);
            image.setIsPrimary(true);
        } else {
            image.setIsPrimary(false);
        }
        image.setImageUrl(normalized.imageUrl());
        image.setAltText(normalized.altText());
        image.setSortOrder(normalized.sortOrder());
        if (wasPrimary && !normalized.isPrimary()) {
            promoteAnotherPrimary(productId, imageId);
        }
        return CatalogMapper.toImageResponse(image);
    }

    @Transactional
    public void deleteImage(Long productId, Long imageId) {
        lockProduct(productId);
        ProductImage image = requireOwnedImage(productId, imageId);
        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        productImageRepository.delete(image);
        productImageRepository.flush();
        if (wasPrimary) {
            promoteAnotherPrimary(productId, null);
        }
    }

    public List<ProductImageResponse> listImages(Long productId) {
        requireProduct(productId);
        return productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(productId).stream()
                .map(CatalogMapper::toImageResponse)
                .toList();
    }

    private void demotePrimaryImages(Long productId) {
        for (ProductImage image : productImageRepository.findByProduct_IdAndIsPrimaryTrue(productId)) {
            image.setIsPrimary(false);
        }
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private Product lockProduct(Long id) {
        return productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private ProductImage requireOwnedImage(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found"));
        if (!image.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Product image not found");
        }
        return image;
    }

    private void promoteAnotherPrimary(Long productId, Long excludedImageId) {
        for (ProductImage candidate : productImageRepository.findByProduct_IdOrderBySortOrderAscIdAsc(productId)) {
            if (excludedImageId != null && excludedImageId.equals(candidate.getId())) {
                continue;
            }
            candidate.setIsPrimary(true);
            return;
        }
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private Brand requireBrand(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    }

    private static void assertSalePrice(BigDecimal price, BigDecimal salePrice) {
        if (salePrice != null && salePrice.compareTo(price) > 0) {
            throw new BusinessRuleException("salePrice must not be greater than price");
        }
    }

    private static void assertActiveReferences(ProductStatus status, Category category, Brand brand) {
        if (status != ProductStatus.ACTIVE) {
            return;
        }
        if (category.getStatus() != CatalogStatus.ACTIVE) {
            throw new BusinessRuleException("Active product requires an active category");
        }
        if (brand.getStatus() != CatalogStatus.ACTIVE) {
            throw new BusinessRuleException("Active product requires an active brand");
        }
    }

    private static void assertSortOrder(Integer sortOrder) {
        if (sortOrder != null && sortOrder < 0) {
            throw new BusinessRuleException("sortOrder must not be negative");
        }
    }

    private static void apply(Product product, ProductCreateRequest request, Category category, Brand brand) {
        apply(product, request.name(), request.slug(), request.description(), request.price(), request.salePrice(),
                category, brand, request.status(), request.featured());
    }

    private static void apply(
            Product product,
            String name,
            String slug,
            String description,
            BigDecimal price,
            BigDecimal salePrice,
            Category category,
            Brand brand,
            ProductStatus status,
            Boolean featured) {
        product.setName(name);
        product.setSlug(slug);
        product.setDescription(description);
        product.setPrice(price);
        product.setSalePrice(salePrice);
        product.setCategory(category);
        product.setBrand(brand);
        product.setStatus(status);
        product.setFeatured(featured);
    }

    private static ProductCreateRequest normalize(ProductCreateRequest request) {
        if (request == null) {
            return null;
        }
        return new ProductCreateRequest(
                Text.trim(request.name()),
                Text.trim(request.slug()),
                Text.emptyToNull(request.description()),
                request.price(),
                request.salePrice(),
                request.categoryId(),
                request.brandId(),
                request.status(),
                request.featured());
    }

    private static ProductUpdateRequest normalize(ProductUpdateRequest request) {
        if (request == null) {
            return null;
        }
        return new ProductUpdateRequest(
                Text.trim(request.name()),
                Text.trim(request.slug()),
                Text.emptyToNull(request.description()),
                request.price(),
                request.salePrice(),
                request.categoryId(),
                request.brandId(),
                request.status(),
                request.featured());
    }

    private static ProductImageCreateRequest normalize(ProductImageCreateRequest request) {
        if (request == null) {
            return null;
        }
        return new ProductImageCreateRequest(
                Text.trim(request.imageUrl()),
                Text.emptyToNull(request.altText()),
                request.isPrimary(),
                request.sortOrder());
    }
}
