package com.DatShop.service;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.order.OrderDetailResponse;
import com.DatShop.dto.order.OrderSummaryResponse;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.OrderMapper;
import com.DatShop.model.Order;
import com.DatShop.model.OrderStatus;
import com.DatShop.model.User;
import com.DatShop.repository.OrderItemRepository;
import com.DatShop.repository.OrderRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final String NOT_FOUND = "Order not found";
    private static final String CANNOT_CANCEL = "Order cannot be cancelled";

    private final CurrentUserService currentUserService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderCancellationService orderCancellationService;

    public OrderService(
            CurrentUserService currentUserService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderCancellationService orderCancellationService) {
        this.currentUserService = currentUserService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderCancellationService = orderCancellationService;
    }

    public PageResponse<OrderSummaryResponse> listMine(Authentication authentication, int page, int size) {
        User user = currentUserService.requireUser(authentication);
        Page<Order> result = orderRepository.findByUser_Id(user.getId(), ProductQuerySupport.pageable(page, size));
        Map<Long, Integer> itemCounts = itemCounts(result.getContent());
        return ProductQuerySupport.pageResponse(result, result.getContent().stream()
                .map(order -> OrderMapper.toSummary(order, itemCounts.getOrDefault(order.getId(), 0)))
                .toList());
    }

    public OrderDetailResponse getMine(Authentication authentication, String orderNumber) {
        User user = currentUserService.requireUser(authentication);
        Order order = orderRepository.findByOrderNumberAndUser_Id(orderNumber, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        return OrderMapper.toDetail(order, orderItemRepository.findByOrder_IdOrderByIdAsc(order.getId()));
    }

    @Transactional
    public OrderDetailResponse cancelMine(Authentication authentication, String orderNumber) {
        User user = currentUserService.requireUser(authentication);
        Order order = orderRepository.findByOrderNumberAndUser_IdForUpdate(orderNumber, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderMapper.toDetail(order, orderItemRepository.findByOrder_IdOrderByIdAsc(order.getId()));
        }
        if (!OrderStatusTransitions.customerCanCancel(order.getStatus())) {
            throw new BusinessRuleException(CANNOT_CANCEL);
        }
        return orderCancellationService.restoreAndCancel(order);
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
}
