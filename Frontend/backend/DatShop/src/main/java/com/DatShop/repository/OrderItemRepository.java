package com.DatShop.repository;

import com.DatShop.model.OrderItem;
import com.DatShop.model.OrderStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder_IdOrderByIdAsc(Long orderId);

    @Query("""
            SELECT i.order.id, COUNT(i)
            FROM OrderItem i
            WHERE i.order.id IN :orderIds
            GROUP BY i.order.id
            """)
    List<Object[]> countByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    @Query("""
            SELECT COUNT(i) > 0
            FROM OrderItem i
            JOIN i.order o
            WHERE i.productId = :productId
              AND o.user.id = :userId
              AND o.status = :status
            """)
    boolean existsPurchasedProduct(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("status") OrderStatus status);

    @Query("""
            SELECT i.productId AS productId, SUM(i.quantity) AS quantitySold, SUM(i.lineTotal) AS revenue
            FROM OrderItem i
            JOIN i.order o
            WHERE o.status IN :statuses
              AND o.createdAt >= :fromInclusive
              AND o.createdAt < :toExclusive
            GROUP BY i.productId
            ORDER BY SUM(i.quantity) DESC, SUM(i.lineTotal) DESC, i.productId ASC
            """)
    List<BestSellerTotalsRow> bestSellerTotals(
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable);

    @Query("""
            SELECT i.productId AS productId, i.productName AS productName, i.productSlug AS productSlug
            FROM OrderItem i
            WHERE i.id IN (
                SELECT MAX(i2.id)
                FROM OrderItem i2
                JOIN i2.order o2
                WHERE i2.productId IN :productIds
                  AND o2.status IN :statuses
                  AND o2.createdAt >= :fromInclusive
                  AND o2.createdAt < :toExclusive
                GROUP BY i2.productId
            )
            """)
    List<BestSellerNameRow> latestSnapshotNames(
            @Param("productIds") Collection<Long> productIds,
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive);
}
