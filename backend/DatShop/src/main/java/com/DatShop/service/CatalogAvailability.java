package com.DatShop.service;

import com.DatShop.exception.BusinessRuleException;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Color;
import com.DatShop.model.Product;
import com.DatShop.model.ProductStatus;
import com.DatShop.model.ProductVariant;
import com.DatShop.model.Size;

final class CatalogAvailability {

    static final String UNAVAILABLE = "This product is not available";
    static final String OVER_STOCK = "Requested quantity exceeds available stock";

    private CatalogAvailability() {
    }

    static boolean isPubliclyVisible(Product product) {
        return product != null
                && product.getStatus() == ProductStatus.ACTIVE
                && product.getCategory() != null
                && product.getCategory().getStatus() == CatalogStatus.ACTIVE
                && product.getBrand() != null
                && product.getBrand().getStatus() == CatalogStatus.ACTIVE;
    }

    static boolean isPurchasable(ProductVariant variant) {
        return Boolean.TRUE.equals(variant.getActive()) && isCatalogActive(variant);
    }

    static boolean isLineAvailable(ProductVariant variant, int quantity) {
        Integer stock = variant.getStockQuantity();
        return Boolean.TRUE.equals(variant.getActive())
                && stock != null
                && stock > 0
                && stock >= quantity
                && isCatalogActive(variant);
    }

    static boolean isCatalogActive(ProductVariant variant) {
        Product product = variant.getProduct();
        if (!isPubliclyVisible(product)) {
            return false;
        }
        Size size = variant.getSize();
        if (size != null && !Boolean.TRUE.equals(size.getActive())) {
            return false;
        }
        Color color = variant.getColor();
        return color == null || Boolean.TRUE.equals(color.getActive());
    }

    static void assertPurchasableLine(ProductVariant variant, int quantity) {
        if (quantity < 1 || !isPurchasable(variant)) {
            throw new BusinessRuleException(UNAVAILABLE);
        }
        Integer stock = variant.getStockQuantity();
        if (stock == null || quantity > stock) {
            throw new BusinessRuleException(OVER_STOCK);
        }
    }
}
