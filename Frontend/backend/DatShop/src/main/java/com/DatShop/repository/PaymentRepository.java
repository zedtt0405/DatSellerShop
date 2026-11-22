package com.DatShop.repository;

import com.DatShop.model.Payment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByOrder_Id(Long orderId);

    @EntityGraph(attributePaths = "order")
    Optional<Payment> findByPaymentReference(String paymentReference);

    @EntityGraph(attributePaths = "order")
    Optional<Payment> findByOrder_OrderNumberAndOrder_User_Id(String orderNumber, Long userId);

    boolean existsByPaymentReference(String paymentReference);

    @EntityGraph(attributePaths = "order")
    @Override
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findByOrder_IdForUpdate(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p JOIN FETCH p.order WHERE p.paymentReference = :paymentReference")
    Optional<Payment> findByPaymentReferenceForUpdate(@Param("paymentReference") String paymentReference);
}
