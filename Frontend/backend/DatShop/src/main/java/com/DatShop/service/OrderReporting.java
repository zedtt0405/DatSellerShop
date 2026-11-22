package com.DatShop.service;

import com.DatShop.model.OrderStatus;
import java.util.List;
import java.util.Set;

/**
 * Shared operational-revenue definition used by Customer Management and Dashboard.
 * Payment.status is not part of revenue; COD may be fulfilled before collection.
 */
final class OrderReporting {

    static final Set<OrderStatus> REVENUE_STATUSES = Set.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.SHIPPING,
            OrderStatus.DELIVERED);

    private OrderReporting() {
    }

    static List<String> revenueStatusNames() {
        return REVENUE_STATUSES.stream().map(Enum::name).toList();
    }
}
