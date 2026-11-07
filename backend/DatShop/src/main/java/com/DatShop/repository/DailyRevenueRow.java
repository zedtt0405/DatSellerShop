package com.DatShop.repository;

import java.math.BigDecimal;

public interface DailyRevenueRow {

    Object getReportDate();

    BigDecimal getRevenue();

    Long getOrderCount();
}
