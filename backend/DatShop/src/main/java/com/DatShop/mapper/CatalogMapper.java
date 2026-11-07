package com.DatShop.mapper;

import com.DatShop.dto.catalog.BrandResponse;
import com.DatShop.dto.catalog.BrandSummary;
import com.DatShop.dto.catalog.CategoryResponse;
import com.DatShop.dto.catalog.CategorySummary;
import com.DatShop.dto.catalog.ColorResponse;
import com.DatShop.dto.catalog.ColorSummary;
import com.DatShop.dto.catalog.ProductCardResponse;
import com.DatShop.dto.catalog.ProductDetailResponse;
import com.DatShop.dto.catalog.ProductImageResponse;
import com.DatShop.dto.catalog.ProductResponse;
import com.DatShop.dto.catalog.ProductVariantResponse;
import com.DatShop.dto.catalog.SizeResponse;
import com.DatShop.dto.catalog.SizeSummary;
import com.DatShop.model.Brand;
import com.DatShop.model.Category;
import com.DatShop.model.Color;
import com.DatShop.model.Product;
import com.DatShop.model.ProductImage;
import com.DatShop.model.ProductVariant;
import com.DatShop.model.Size;
import java.math.BigDecimal;
import java.util.List;

public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static CategoryResponse toCategoryResponse(Category category) {
        Category parent = category.getParentCategory();
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getStatus(),
                parent == null ? null : parent.getId(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }

    public static CategorySummary toCategorySummary(Category category) {
        return new CategorySummary(category.getId(), category.getName(), category.getSlug());
    }

    public static BrandResponse toBrandResponse(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getSlug(),
                brand.getDescription(),
                brand.getLogoUrl(),
                brand.getStatus(),
                brand.getCreatedAt(),
                brand.getUpdatedAt());
    }

    public static BrandSummary toBrandSummary(Brand brand) {
        return new BrandSummary(brand.getId(), brand.getName(), brand.getSlug());
    }

    public static SizeResponse toSizeResponse(Size size) {
        return new SizeResponse(size.getId(), size.getName(), size.getDisplayOrder(), size.getActive());
    }

    public static ColorResponse toColorResponse(Color color) {
        return new ColorResponse(color.getId(), color.getName(), color.getHexCode(), color.getActive());
    }

    public static ProductResponse toProductResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getPrice(),
                product.getSalePrice(),
                effectivePrice(product.getPrice(), product.getSalePrice()),
                toCategorySummary(product.getCategory()),
                toBrandSummary(product.getBrand()),
                product.getStatus(),
                product.getFeatured(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public static ProductCardResponse toProductCard(Product product, String primaryImageUrl, boolean inStock) {
        return new ProductCardResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getPrice(),
                product.getSalePrice(),
                effectivePrice(product.getPrice(), product.getSalePrice()),
                toCategorySummary(product.getCategory()),
                toBrandSummary(product.getBrand()),
                primaryImageUrl,
                product.getFeatured(),
                inStock);
    }

    public static ProductImageResponse toImageResponse(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getProduct().getId(),
                image.getImageUrl(),
                image.getAltText(),
                image.getIsPrimary(),
                image.getSortOrder());
    }

    public static ProductVariantResponse toVariantResponse(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getId(),
                variant.getProduct().getId(),
                variant.getSku(),
                variant.getStockQuantity(),
                variant.getActive(),
                toSizeSummary(variant.getSize()),
                toColorSummary(variant.getColor()),
                variant.getCreatedAt(),
                variant.getUpdatedAt());
    }

    public static ProductDetailResponse toProductDetail(
            Product product, List<ProductImage> images, List<ProductVariant> variants) {
        return new ProductDetailResponse(
                toProductResponse(product),
                images.stream().map(CatalogMapper::toImageResponse).toList(),
                variants.stream().map(CatalogMapper::toVariantResponse).toList());
    }

    public static BigDecimal effectivePrice(BigDecimal price, BigDecimal salePrice) {
        return salePrice == null ? price : salePrice;
    }

    static SizeSummary toSizeSummary(Size size) {
        if (size == null) {
            return null;
        }
        return new SizeSummary(size.getId(), size.getName());
    }

    static ColorSummary toColorSummary(Color color) {
        if (color == null) {
            return null;
        }
        return new ColorSummary(color.getId(), color.getName(), color.getHexCode());
    }
}
