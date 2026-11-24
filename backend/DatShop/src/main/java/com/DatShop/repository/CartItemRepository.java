package com.DatShop.repository;

import com.DatShop.model.CartItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ci FROM CartItem ci
            WHERE ci.cart.id = :cartId
            ORDER BY ci.id ASC
            """)
    List<CartItem> lockByCartIdOrderByIdAsc(@Param("cartId") Long cartId);

    Optional<CartItem> findByIdAndCart_Id(Long id, Long cartId);

    Optional<CartItem> findByCart_IdAndProductVariant_Id(Long cartId, Long productVariantId);
}
