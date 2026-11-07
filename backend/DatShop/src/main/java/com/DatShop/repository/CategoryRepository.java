package com.DatShop.repository;

import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    List<Category> findByStatusOrderByNameAsc(CatalogStatus status);

    List<Category> findAllByOrderByNameAsc();
}
