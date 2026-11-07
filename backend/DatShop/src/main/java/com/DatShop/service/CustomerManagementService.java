package com.DatShop.service;

import com.DatShop.dto.account.AddressResponse;
import com.DatShop.dto.admin.CustomerDetailResponse;
import com.DatShop.dto.admin.CustomerStatusUpdateRequest;
import com.DatShop.dto.admin.CustomerSummaryResponse;
import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.order.OrderSummaryResponse;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.AddressMapper;
import com.DatShop.mapper.CustomerMapper;
import com.DatShop.mapper.OrderMapper;
import com.DatShop.model.Order;
import com.DatShop.model.OrderStatus;
import com.DatShop.model.RoleName;
import com.DatShop.model.User;
import com.DatShop.model.UserStatus;
import com.DatShop.repository.AddressRepository;
import com.DatShop.repository.OrderItemRepository;
import com.DatShop.repository.OrderRepository;
import com.DatShop.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerManagementService {

    static final String NOT_FOUND = "Customer not found";
    /**
     * totalSpent uses {@link OrderReporting#REVENUE_STATUSES}: CONFIRMED, PROCESSING,
     * SHIPPING, DELIVERED. PENDING/CANCELLED/RETURNED are excluded.
     */
    private static final int LATEST_ORDERS = 5;

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public CustomerManagementService(
            UserRepository userRepository,
            AddressRepository addressRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    public PageResponse<CustomerSummaryResponse> list(UserStatus status, String q, int page, int size) {
        Page<User> result = userRepository.findAll(
                CustomerSpecifications.customers(status, q),
                ProductQuerySupport.pageable(page, size));
        Map<Long, OrderMetrics> metrics = metrics(result.getContent().stream().map(User::getId).toList());
        return ProductQuerySupport.pageResponse(result, result.getContent().stream()
                .map(user -> {
                    OrderMetrics row = metrics.getOrDefault(user.getId(), OrderMetrics.EMPTY);
                    return CustomerMapper.toSummary(user, row.orderCount(), row.totalSpent());
                })
                .toList());
    }

    public CustomerDetailResponse getById(Long customerId) {
        User user = requireCustomer(customerId);
        List<AddressResponse> addresses = addressRepository.findByUser_IdOrderByCreatedAtAscIdAsc(user.getId())
                .stream()
                .map(AddressMapper::toResponse)
                .toList();
        OrderMetrics metrics = metrics(List.of(user.getId())).getOrDefault(user.getId(), OrderMetrics.EMPTY);
        Page<Order> latest = orderRepository.findByUser_Id(user.getId(), ProductQuerySupport.pageable(0, LATEST_ORDERS));
        Map<Long, Integer> itemCounts = itemCounts(latest.getContent());
        List<OrderSummaryResponse> latestOrders = latest.getContent().stream()
                .map(order -> OrderMapper.toSummary(order, itemCounts.getOrDefault(order.getId(), 0)))
                .toList();
        return CustomerMapper.toDetail(user, addresses, metrics.orderCount(), metrics.totalSpent(), latestOrders);
    }

    public PageResponse<OrderSummaryResponse> listOrders(Long customerId, OrderStatus status, int page, int size) {
        User user = requireCustomer(customerId);
        Page<Order> result = status == null
                ? orderRepository.findByUser_Id(user.getId(), ProductQuerySupport.pageable(page, size))
                : orderRepository.findByUser_IdAndStatus(user.getId(), status, ProductQuerySupport.pageable(page, size));
        Map<Long, Integer> itemCounts = itemCounts(result.getContent());
        return ProductQuerySupport.pageResponse(result, result.getContent().stream()
                .map(order -> OrderMapper.toSummary(order, itemCounts.getOrDefault(order.getId(), 0)))
                .toList());
    }

    @Transactional
    public CustomerDetailResponse updateStatus(Long customerId, CustomerStatusUpdateRequest request) {
        User user = userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (!userRepository.existsByIdAndRoleName(user.getId(), RoleName.CUSTOMER)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        user.setStatus(request.status());
        return getById(user.getId());
    }

    private User requireCustomer(Long customerId) {
        User user = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (!userRepository.existsByIdAndRoleName(user.getId(), RoleName.CUSTOMER)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return user;
    }

    private Map<Long, OrderMetrics> metrics(Collection<Long> userIds) {
        Map<Long, OrderMetrics> metrics = new HashMap<>();
        if (userIds.isEmpty()) {
            return metrics;
        }
        for (Object[] row : orderRepository.aggregateByUserIds(userIds, OrderReporting.REVENUE_STATUSES)) {
            metrics.put((Long) row[0], new OrderMetrics(
                    ((Number) row[1]).longValue(),
                    money(row[2])));
        }
        return metrics;
    }

    private Map<Long, Integer> itemCounts(List<Order> orders) {
        Map<Long, Integer> counts = new HashMap<>();
        if (orders.isEmpty()) {
            return counts;
        }
        List<Long> ids = orders.stream().map(Order::getId).toList();
        for (Object[] row : orderItemRepository.countByOrderIds(ids)) {
            counts.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    private static BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private record OrderMetrics(long orderCount, BigDecimal totalSpent) {
        private static final OrderMetrics EMPTY = new OrderMetrics(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }
}
