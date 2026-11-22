package com.DatShop.repository;

import com.DatShop.model.Size;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SizeRepository extends JpaRepository<Size, Long> {

    Optional<Size> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Size> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    List<Size> findAllByOrderByDisplayOrderAscNameAsc();
}
