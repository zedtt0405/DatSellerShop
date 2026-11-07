package com.DatShop.service;

import com.DatShop.dto.admin.BestSellingProductResponse;
import com.DatShop.dto.admin.DashboardSummaryResponse;
import com.DatShop.dto.admin.RevenuePointResponse;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.model.RoleName;
import com.DatShop.repository.BestSellerNameRow;
import com.DatShop.repository.BestSellerTotalsRow;
import com.DatShop.repository.DailyRevenueRow;
import com.DatShop.repository.OrderItemRepository;
import com.DatShop.repository.OrderKpiProjection;
import com.DatShop.repository.OrderRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int DEFAULT_CHART_DAYS = 30;
    static final int MAX_CHART_DAYS = 366;
    static final int DEFAULT_BEST_SELLER_LIMIT = 10;
    static final int MAX_BEST_SELLER_LIMIT = 50;
    private static final LocalDateTime RANGE_START = LocalDate.of(1970, 1, 1).atStartOfDay();
    private static final LocalDateTime RANGE_END = LocalDate.of(9999, 12, 31).atStartOfDay();

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public DashboardService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            UserRepository userRepository,
            ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    public DashboardSummaryResponse getSummary(LocalDate from, LocalDate to) {
        ReportWindow window = summaryWindow(from, to);
        OrderKpiProjection kpi = orderRepository.summarize(
                window.fromInclusive(), window.toExclusive(), OrderReporting.REVENUE_STATUSES);
        return new DashboardSummaryResponse(
                money(kpi == null ? null : kpi.getRevenue()),
                number(kpi == null ? null : kpi.getOrderCount()),
                userRepository.countByRoleName(RoleName.CUSTOMER),
                productRepository.count(),
                number(kpi == null ? null : kpi.getPendingOrderCount()),
                number(kpi == null ? null : kpi.getDeliveredOrderCount()));
    }

    public List<RevenuePointResponse> getRevenueChart(LocalDate from, LocalDate to) {
        ReportWindow window = chartWindow(from, to);
        Map<LocalDate, RevenuePointResponse> byDate = new HashMap<>();
        for (DailyRevenueRow row : orderRepository.sumRevenueByDay(
                OrderReporting.revenueStatusNames(), window.fromInclusive(), window.toExclusive())) {
            LocalDate date = toLocalDate(row.getReportDate());
            byDate.put(date, new RevenuePointResponse(
                    date, money(row.getRevenue()), number(row.getOrderCount())));
        }
        List<RevenuePointResponse> points = new ArrayList<>();
        for (LocalDate day = window.fromDate(); !day.isAfter(window.toDate()); day = day.plusDays(1)) {
            points.add(byDate.getOrDefault(day, new RevenuePointResponse(day, money(BigDecimal.ZERO), 0)));
        }
        return points;
    }

    public List<BestSellingProductResponse> getBestSellingProducts(LocalDate from, LocalDate to, Integer limit) {
        int size = limit == null ? DEFAULT_BEST_SELLER_LIMIT : limit;
        if (size < 1 || size > MAX_BEST_SELLER_LIMIT) {
            throw new BusinessRuleException("limit must be between 1 and " + MAX_BEST_SELLER_LIMIT);
        }
        ReportWindow window = summaryWindow(from, to);
        List<BestSellerTotalsRow> totals = orderItemRepository.bestSellerTotals(
                OrderReporting.REVENUE_STATUSES,
                window.fromInclusive(),
                window.toExclusive(),
                PageRequest.of(0, size));
        if (totals.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = totals.stream().map(BestSellerTotalsRow::getProductId).toList();
        Map<Long, BestSellerNameRow> names = new HashMap<>();
        for (BestSellerNameRow name : orderItemRepository.latestSnapshotNames(
                productIds,
                OrderReporting.REVENUE_STATUSES,
                window.fromInclusive(),
                window.toExclusive())) {
            names.put(name.getProductId(), name);
        }
        List<BestSellingProductResponse> result = new ArrayList<>();
        for (BestSellerTotalsRow row : totals) {
            BestSellerNameRow name = names.get(row.getProductId());
            result.add(new BestSellingProductResponse(
                    row.getProductId(),
                    name == null ? null : name.getProductName(),
                    name == null ? null : name.getProductSlug(),
                    number(row.getQuantitySold()),
                    money(row.getRevenue())));
        }
        return result;
    }

    private static ReportWindow summaryWindow(LocalDate from, LocalDate to) {
        assertOrder(from, to);
        LocalDateTime fromInclusive = from == null ? RANGE_START : from.atStartOfDay();
        LocalDateTime toExclusive = to == null ? RANGE_END : to.plusDays(1).atStartOfDay();
        LocalDate fromDate = fromInclusive.toLocalDate();
        LocalDate toDate = toExclusive.toLocalDate().minusDays(1);
        return new ReportWindow(fromInclusive, toExclusive, fromDate, toDate);
    }

    /**
     * Chart default is the last 30 calendar days including today in the JVM default timezone,
     * matching AuditableEntity LocalDateTime.now() (not the UTC Clock bean).
     */
    private static ReportWindow chartWindow(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate resolvedTo = to == null ? today : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(DEFAULT_CHART_DAYS - 1L) : from;
        assertOrder(resolvedFrom, resolvedTo);
        long days = ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1;
        if (days > MAX_CHART_DAYS) {
            throw new BusinessRuleException("Chart range cannot exceed " + MAX_CHART_DAYS + " days");
        }
        return new ReportWindow(
                resolvedFrom.atStartOfDay(),
                resolvedTo.plusDays(1).atStartOfDay(),
                resolvedFrom,
                resolvedTo);
    }

    private static void assertOrder(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("from must not be after to");
        }
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            throw new IllegalStateException("Missing report date");
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        return LocalDate.parse(value.toString());
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static long number(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private record ReportWindow(
            LocalDateTime fromInclusive, LocalDateTime toExclusive, LocalDate fromDate, LocalDate toDate) {
    }
}
