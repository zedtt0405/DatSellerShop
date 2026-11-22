package com.DatShop.service;

import com.DatShop.dto.payment.PaymentCreateRequest;
import com.DatShop.dto.payment.PaymentResponse;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.ConflictException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.PaymentMapper;
import com.DatShop.model.Order;
import com.DatShop.model.OrderStatus;
import com.DatShop.model.Payment;
import com.DatShop.model.PaymentMethod;
import com.DatShop.model.PaymentStatus;
import com.DatShop.model.User;
import com.DatShop.repository.OrderRepository;
import com.DatShop.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    static final String PAID_CANCEL_BLOCKED = "Paid order requires refund handling";
    private static final String ORDER_NOT_FOUND = "Order not found";
    private static final String PAYMENT_NOT_FOUND = "Payment not found";
    private static final String CANNOT_CREATE = "Payment cannot be created for this order";
    private static final String METHOD_CONFLICT = "Payment method cannot be changed";
    private static final Set<OrderStatus> COD_CREATE_STATUSES = Set.of(
            OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.SHIPPING);
    private static final Set<OrderStatus> BANK_QR_CREATE_STATUSES = Set.of(
            OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING);

    private final CurrentUserService currentUserService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public PaymentService(
            CurrentUserService currentUserService,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository) {
        this.currentUserService = currentUserService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    public PaymentResponse getMine(Authentication authentication, String orderNumber) {
        User user = currentUserService.requireUser(authentication);
        Payment payment = paymentRepository.findByOrder_OrderNumberAndOrder_User_Id(orderNumber, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(PAYMENT_NOT_FOUND));
        return PaymentMapper.toResponse(payment);
    }

    @Transactional
    public CreatedPayment createMine(Authentication authentication, String orderNumber, PaymentCreateRequest request) {
        User user = currentUserService.requireUser(authentication);
        Order order = orderRepository.findByOrderNumberAndUser_IdForUpdate(orderNumber, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ORDER_NOT_FOUND));
        Payment existing = paymentRepository.findByOrder_Id(order.getId()).orElse(null);
        if (existing != null) {
            if (existing.getMethod() != request.method()) {
                throw new ConflictException(METHOD_CONFLICT);
            }
            return new CreatedPayment(PaymentMapper.toResponse(existing), false);
        }
        assertCreatable(order, request.method());
        Payment payment = new Payment();
        payment.setPaymentReference(newPaymentReference());
        payment.setOrder(order);
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(money(order.getTotalAmount()));
        if (request.method() == PaymentMethod.BANK_QR) {
            payment.setQrContent(qrContent(order.getOrderNumber(), payment.getPaymentReference(), payment.getAmount()));
        }
        paymentRepository.save(payment);
        return new CreatedPayment(PaymentMapper.toResponse(payment), true);
    }

    static boolean allowsConfirm(Payment payment) {
        if (payment == null) {
            return true;
        }
        if (payment.getMethod() == PaymentMethod.COD) {
            return true;
        }
        return payment.getMethod() == PaymentMethod.BANK_QR && payment.getStatus() == PaymentStatus.PAID;
    }

    private static void assertCreatable(Order order, PaymentMethod method) {
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.CANCELLED || status == OrderStatus.RETURNED || status == OrderStatus.DELIVERED) {
            throw new BusinessRuleException(CANNOT_CREATE);
        }
        Set<OrderStatus> allowed = method == PaymentMethod.COD ? COD_CREATE_STATUSES : BANK_QR_CREATE_STATUSES;
        if (!allowed.contains(status)) {
            throw new BusinessRuleException(CANNOT_CREATE);
        }
    }

    private String newPaymentReference() {
        String candidate;
        do {
            candidate = "PAY-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        } while (paymentRepository.existsByPaymentReference(candidate));
        return candidate;
    }

    static String qrContent(String orderNumber, String paymentReference, BigDecimal amount) {
        return "DATSHOP;ORDER=" + orderNumber + ";PAYMENT=" + paymentReference + ";AMOUNT=" + money(amount);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record CreatedPayment(PaymentResponse payment, boolean created) {
    }
}
