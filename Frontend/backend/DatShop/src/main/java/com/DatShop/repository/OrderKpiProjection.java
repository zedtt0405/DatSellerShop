package com.DatShop.repository;

import java.math.BigDecimal;

public interface OrderKpiProjection {

    Long getOrderCount();

    BigDecimal getRevenue();

    Long getPendingOrderCount();

    Long getDeliveredOrderCount();
}
