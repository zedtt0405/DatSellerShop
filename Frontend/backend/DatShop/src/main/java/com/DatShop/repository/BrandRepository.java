package com.DatShop.repository;

import com.DatShop.model.Brand;
import com.DatShop.model.CatalogStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findBySlug(String slug);

    boolean existsByName(String name);

    boolean existsBySlug(String slug);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsBySlugAndIdNot(String slug, Long id);

    List<Brand> findByStatusOrderByNameAsc(CatalogStatus status);

    List<Brand> findAllByOrderByNameAsc();
}
