package com.DatShop.repository;

import com.DatShop.model.Color;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ColorRepository extends JpaRepository<Color, Long> {

    Optional<Color> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Color> findByActiveTrueOrderByNameAsc();

    List<Color> findAllByOrderByNameAsc();
}
