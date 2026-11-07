package com.DatShop.repository;

import com.DatShop.model.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = {
            "productVariant",
            "productVariant.product",
            "productVariant.product.category",
            "productVariant.product.brand",
            "productVariant.size",
            "productVariant.color"
    })
    List<CartItem> findByCart_IdOrderByCreatedAtAscIdAsc(Long cartId);

    Optional<CartItem> findByIdAndCart_Id(Long id, Long cartId);

    Optional<CartItem> findByCart_IdAndProductVariant_Id(Long cartId, Long productVariantId);
}
