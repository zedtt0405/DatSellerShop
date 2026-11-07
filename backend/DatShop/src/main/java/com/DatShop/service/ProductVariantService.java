package com.DatShop.service;

import com.DatShop.dto.catalog.ProductVariantCreateRequest;
import com.DatShop.dto.catalog.ProductVariantResponse;
import com.DatShop.dto.catalog.ProductVariantUpdateRequest;
import com.DatShop.dto.catalog.StockUpdateRequest;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.DuplicateResourceException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.CatalogMapper;
import com.DatShop.model.Color;
import com.DatShop.model.Product;
import com.DatShop.model.ProductVariant;
import com.DatShop.model.Size;
import com.DatShop.repository.ColorRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.ProductVariantRepository;
import com.DatShop.repository.SizeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final SizeRepository sizeRepository;
    private final ColorRepository colorRepository;
    private final RequestValidator requestValidator;

    public ProductVariantService(
            ProductVariantRepository productVariantRepository,
            ProductRepository productRepository,
            SizeRepository sizeRepository,
            ColorRepository colorRepository,
            RequestValidator requestValidator) {
        this.productVariantRepository = productVariantRepository;
        this.productRepository = productRepository;
        this.sizeRepository = sizeRepository;
        this.colorRepository = colorRepository;
        this.requestValidator = requestValidator;
    }

    @Transactional
    public ProductVariantResponse create(ProductVariantCreateRequest request) {
        return create(request.productId(), request);
    }

    @Transactional
    public ProductVariantResponse create(Long productId, ProductVariantCreateRequest request) {
        ProductVariantCreateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        assertProductIdMatch(productId, normalized.productId());
        assertStock(normalized.stockQuantity());
        Product product = lockProduct(productId);
        Size size = requireSize(normalized.sizeId());
        Color color = requireColor(normalized.colorId());
        assertSkuAvailable(normalized.sku(), null);
        assertNoSemanticDuplicate(product.getId(), idOf(size), idOf(color), null);
        ProductVariant variant = new ProductVariant();
        apply(variant, product, size, color, normalized.sku(), normalized.stockQuantity(), normalized.active());
        return CatalogMapper.toVariantResponse(productVariantRepository.save(variant));
    }

    @Transactional
    public ProductVariantResponse update(Long id, ProductVariantUpdateRequest request) {
        return update(request.productId(), id, request);
    }

    @Transactional
    public ProductVariantResponse update(Long productId, Long variantId, ProductVariantUpdateRequest request) {
        ProductVariantUpdateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        assertProductIdMatch(productId, normalized.productId());
        assertStock(normalized.stockQuantity());
        lockProduct(productId);
        ProductVariant variant = requireOwnedVariant(productId, variantId);
        if (!variant.getProduct().getId().equals(normalized.productId())) {
            throw new BusinessRuleException("Product variant cannot be moved to another product");
        }
        Size size = requireSize(normalized.sizeId());
        Color color = requireColor(normalized.colorId());
        assertSkuAvailable(normalized.sku(), variantId);
        assertNoSemanticDuplicate(productId, idOf(size), idOf(color), variantId);
        apply(variant, variant.getProduct(), size, color, normalized.sku(), normalized.stockQuantity(),
                normalized.active());
        return CatalogMapper.toVariantResponse(variant);
    }

    @Transactional
    public ProductVariantResponse updateStock(Long productId, Long variantId, StockUpdateRequest request) {
        requestValidator.validate(request);
        assertStock(request.stockQuantity());
        lockProduct(productId);
        ProductVariant variant = requireOwnedVariant(productId, variantId);
        variant.setStockQuantity(request.stockQuantity());
        return CatalogMapper.toVariantResponse(variant);
    }

    public List<ProductVariantResponse> listByProduct(Long productId) {
        requireProduct(productId);
        return productVariantRepository.findByProduct_Id(productId).stream()
                .map(CatalogMapper::toVariantResponse)
                .toList();
    }

    private void assertSkuAvailable(String sku, Long excludeId) {
        boolean taken = excludeId == null
                ? productVariantRepository.existsBySku(sku)
                : productVariantRepository.existsBySkuAndIdNot(sku, excludeId);
        if (taken) {
            throw new DuplicateResourceException("SKU already exists");
        }
    }

    private void assertNoSemanticDuplicate(Long productId, Long sizeId, Long colorId, Long excludeId) {
        if (productVariantRepository.countMatchingVariant(productId, sizeId, colorId, excludeId) > 0) {
            throw new DuplicateResourceException("Product variant already exists for this size and color");
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

    private ProductVariant requireOwnedVariant(Long productId, Long variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
        if (!variant.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Product variant not found");
        }
        return variant;
    }

    private static void assertProductIdMatch(Long pathProductId, Long requestProductId) {
        if (pathProductId == null || requestProductId == null || !pathProductId.equals(requestProductId)) {
            throw new BusinessRuleException("productId in the request must match the URL");
        }
    }

    private Size requireSize(Long id) {
        if (id == null) {
            return null;
        }
        return sizeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Size not found"));
    }

    private Color requireColor(Long id) {
        if (id == null) {
            return null;
        }
        return colorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Color not found"));
    }

    private static void assertStock(Integer stockQuantity) {
        if (stockQuantity != null && stockQuantity < 0) {
            throw new BusinessRuleException("stockQuantity must not be negative");
        }
    }

    private static Long idOf(Size size) {
        return size == null ? null : size.getId();
    }

    private static Long idOf(Color color) {
        return color == null ? null : color.getId();
    }

    private static void apply(
            ProductVariant variant,
            Product product,
            Size size,
            Color color,
            String sku,
            Integer stockQuantity,
            Boolean active) {
        variant.setProduct(product);
        variant.setSize(size);
        variant.setColor(color);
        variant.setSku(sku);
        variant.setStockQuantity(stockQuantity);
        variant.setActive(active);
    }

    private static ProductVariantCreateRequest normalize(ProductVariantCreateRequest request) {
        if (request == null) {
            return null;
        }
        return new ProductVariantCreateRequest(
                request.productId(),
                request.sizeId(),
                request.colorId(),
                Text.trim(request.sku()),
                request.stockQuantity(),
                request.active());
    }

    private static ProductVariantUpdateRequest normalize(ProductVariantUpdateRequest request) {
        if (request == null) {
            return null;
        }
        return new ProductVariantUpdateRequest(
                request.productId(),
                request.sizeId(),
                request.colorId(),
                Text.trim(request.sku()),
                request.stockQuantity(),
                request.active());
    }
}
