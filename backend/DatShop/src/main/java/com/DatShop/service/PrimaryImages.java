package com.DatShop.service;

import com.DatShop.repository.ProductImageRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

final class PrimaryImages {

    private PrimaryImages() {
    }

    static Map<Long, String> urlsByProductId(ProductImageRepository repository, Collection<Long> productIds) {
        Map<Long, String> urls = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return urls;
        }
        for (Object[] row : repository.findPrimaryImageUrlsByProductIds(productIds)) {
            urls.putIfAbsent((Long) row[0], (String) row[1]);
        }
        return urls;
    }
}
