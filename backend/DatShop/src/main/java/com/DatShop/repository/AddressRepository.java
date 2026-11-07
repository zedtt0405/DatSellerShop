package com.DatShop.repository;

import com.DatShop.model.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUser_Id(Long userId);

    List<Address> findByUser_IdOrderByCreatedAtAscIdAsc(Long userId);

    Optional<Address> findByIdAndUser_Id(Long id, Long userId);

    List<Address> findByUser_IdAndIsDefaultTrue(Long userId);

    long countByUser_Id(Long userId);
}
