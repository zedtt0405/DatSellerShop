package com.DatShop.repository;

import com.DatShop.model.WishlistItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    @EntityGraph(attributePaths = {"product", "product.category", "product.brand"})
    List<WishlistItem> findByWishlist_IdOrderByCreatedAtDescIdDesc(Long wishlistId);

    Optional<WishlistItem> findByWishlist_IdAndProduct_Id(Long wishlistId, Long productId);

    boolean existsByWishlist_IdAndProduct_Id(Long wishlistId, Long productId);
}
