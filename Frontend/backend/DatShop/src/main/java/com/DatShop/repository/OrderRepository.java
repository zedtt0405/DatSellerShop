package com.DatShop.repository;

import com.DatShop.model.Order;
import com.DatShop.model.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Page<Order> findByUser_Id(Long userId, Pageable pageable);

    Page<Order> findByUser_IdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByOrderNumberAndUser_Id(String orderNumber, Long userId);

    boolean existsByOrderNumber(String orderNumber);

    long countByUser_Id(Long userId);

    @Query("""
            SELECT o.user.id, COUNT(o), COALESCE(SUM(CASE WHEN o.status IN :spentStatuses THEN o.totalAmount ELSE 0 END), 0)
            FROM ShopOrder o
            WHERE o.user.id IN :userIds
            GROUP BY o.user.id
            """)
    List<Object[]> aggregateByUserIds(
            @Param("userIds") Collection<Long> userIds,
            @Param("spentStatuses") Collection<OrderStatus> spentStatuses);

    @Query("""
            SELECT COUNT(o) AS orderCount,
                   COALESCE(SUM(CASE WHEN o.status IN :revenueStatuses THEN o.totalAmount ELSE 0 END), 0) AS revenue,
                   COALESCE(SUM(CASE WHEN o.status = com.DatShop.model.OrderStatus.PENDING THEN 1 ELSE 0 END), 0) AS pendingOrderCount,
                   COALESCE(SUM(CASE WHEN o.status = com.DatShop.model.OrderStatus.DELIVERED THEN 1 ELSE 0 END), 0) AS deliveredOrderCount
            FROM ShopOrder o
            WHERE o.createdAt >= :fromInclusive AND o.createdAt < :toExclusive
            """)
    OrderKpiProjection summarize(
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("revenueStatuses") Collection<OrderStatus> revenueStatuses);

    /**
     * Daily revenue for charting. Uses MySQL DATE() on LocalDateTime created_at.
     * Isolated here so the rest of the app stays JPQL.
     */
    @Query(
            value = """
                    SELECT DATE(o.created_at) AS reportDate,
                           COALESCE(SUM(o.total_amount), 0) AS revenue,
                           COUNT(*) AS orderCount
                    FROM orders o
                    WHERE o.status IN (:statuses)
                      AND o.created_at >= :fromInclusive
                      AND o.created_at < :toExclusive
                    GROUP BY DATE(o.created_at)
                    """,
            nativeQuery = true)
    List<DailyRevenueRow> sumRevenueByDay(
            @Param("statuses") Collection<String> statuses,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ShopOrder o WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ShopOrder o WHERE o.orderNumber = :orderNumber AND o.user.id = :userId")
    Optional<Order> findByOrderNumberAndUser_IdForUpdate(
            @Param("orderNumber") String orderNumber, @Param("userId") Long userId);
}
