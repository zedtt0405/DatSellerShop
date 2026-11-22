package com.DatShop.repository;

import java.math.BigDecimal;

public interface BestSellerTotalsRow {

    Long getProductId();

    Long getQuantitySold();

    BigDecimal getRevenue();
}
