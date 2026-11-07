package com.DatShop.service;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.order.OrderDetailResponse;
import com.DatShop.dto.order.OrderStatusUpdateRequest;
import com.DatShop.dto.order.OrderSummaryResponse;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.OrderMapper;
import com.DatShop.model.Order;
import com.DatShop.model.OrderStatus;
import com.DatShop.repository.OrderItemRepository;
import com.DatShop.repository.OrderRepository;
import com.DatShop.repository.PaymentRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderManagementService {

    private static final String NOT_FOUND = "Order not found";
    private static final String INVALID_TRANSITION = "Invalid order status transition";
    private static final String QR_UNPAID = "BANK_QR payment must be paid before the order can be confirmed";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderCancellationService orderCancellationService;
    private final PaymentRepository paymentRepository;

    public OrderManagementService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderCancellationService orderCancellationService,
            PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderCancellationService = orderCancellationService;
        this.paymentRepository = paymentRepository;
    }

    public PageResponse<OrderSummaryResponse> list(OrderStatus status, String q, int page, int size) {
        Page<Order> result = orderRepository.findAll(
                OrderSpecifications.management(status, q),
                ProductQuerySupport.pageable(page, size));
        Map<Long, Integer> itemCounts = itemCounts(result.getContent());
        return ProductQuerySupport.pageResponse(result, result.getContent().stream()
                .map(order -> OrderMapper.toSummary(order, itemCounts.getOrDefault(order.getId(), 0)))
                .toList());
    }

    public OrderDetailResponse getByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        return OrderMapper.toDetail(order, orderItemRepository.findByOrder_IdOrderByIdAsc(order.getId()));
    }

    @Transactional
    public OrderDetailResponse updateStatus(String orderNumber, OrderStatusUpdateRequest request) {
        Order order = orderRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        OrderStatus target = request.status();
        if (target == OrderStatus.CANCELLED) {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                return OrderMapper.toDetail(order, orderItemRepository.findByOrder_IdOrderByIdAsc(order.getId()));
            }
            if (!OrderStatusTransitions.staffCanCancel(order.getStatus())) {
                throw new BusinessRuleException(INVALID_TRANSITION);
            }
            return orderCancellationService.restoreAndCancel(order);
        }
        if (!OrderStatusTransitions.allows(order.getStatus(), target)) {
            throw new BusinessRuleException(INVALID_TRANSITION);
        }
        if (order.getStatus() == OrderStatus.PENDING && target == OrderStatus.CONFIRMED
                && !PaymentService.allowsConfirm(paymentRepository.findByOrder_Id(order.getId()).orElse(null))) {
            throw new BusinessRuleException(QR_UNPAID);
        }
        order.setStatus(target);
        return OrderMapper.toDetail(order, orderItemRepository.findByOrder_IdOrderByIdAsc(order.getId()));
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
