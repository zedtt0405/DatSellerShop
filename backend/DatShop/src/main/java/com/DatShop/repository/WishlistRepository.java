package com.DatShop.repository;

import com.DatShop.model.Wishlist;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    Optional<Wishlist> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);
}
