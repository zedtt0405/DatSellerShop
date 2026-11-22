package com.DatShop.service;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.payment.PaymentResponse;
import com.DatShop.dto.payment.PaymentStatusUpdateRequest;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.PaymentMapper;
import com.DatShop.model.Order;
import com.DatShop.model.OrderStatus;
import com.DatShop.model.Payment;
import com.DatShop.model.PaymentMethod;
import com.DatShop.model.PaymentStatus;
import com.DatShop.repository.PaymentRepository;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentManagementService {

    private static final String NOT_FOUND = "Payment not found";
    private static final String INVALID_TRANSITION = "Invalid payment status transition";
    private static final String COD_NOT_DELIVERED = "COD can be marked PAID only after the order is delivered";
    private static final String QR_ORDER_CLOSED = "BANK_QR cannot be marked PAID for a cancelled or returned order";

    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public PaymentManagementService(PaymentRepository paymentRepository, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    public PageResponse<PaymentResponse> list(PaymentStatus status, PaymentMethod method, String q, int page, int size) {
        Page<Payment> result = paymentRepository.findAll(
                PaymentSpecifications.management(status, method, q),
                ProductQuerySupport.pageable(page, size));
        return ProductQuerySupport.pageResponse(result, result.getContent().stream()
                .map(PaymentMapper::toResponse)
                .toList());
    }

    public PaymentResponse getByReference(String paymentReference) {
        Payment payment = paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        return PaymentMapper.toResponse(payment);
    }

    /**
     * Admin/Staff mutation locks Payment only, then reads Order state.
     * It does not lock Order, so it cannot invert the cancellation lock order
     * (Order → Payment → variants → voucher).
     */
    @Transactional
    public PaymentResponse updateStatus(String paymentReference, PaymentStatusUpdateRequest request) {
        Payment payment = paymentRepository.findByPaymentReferenceForUpdate(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        PaymentStatus target = request.status();
        if (target == PaymentStatus.PAID && payment.getStatus() == PaymentStatus.PAID) {
            return PaymentMapper.toResponse(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING
                || (target != PaymentStatus.PAID && target != PaymentStatus.FAILED)) {
            throw new BusinessRuleException(INVALID_TRANSITION);
        }
        if (target == PaymentStatus.PAID) {
            assertPayable(payment);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(clock.instant());
            payment.setFailureReason(null);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }
        return PaymentMapper.toResponse(payment);
    }

    private static void assertPayable(Payment payment) {
        Order order = payment.getOrder();
        if (payment.getMethod() == PaymentMethod.COD) {
            if (order.getStatus() != OrderStatus.DELIVERED) {
                throw new BusinessRuleException(COD_NOT_DELIVERED);
            }
            return;
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.RETURNED) {
            throw new BusinessRuleException(QR_ORDER_CLOSED);
        }
    }
}
