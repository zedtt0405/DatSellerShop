package com.DatShop.service;

import com.DatShop.dto.order.OrderDetailResponse;
import com.DatShop.exception.ConflictException;
import com.DatShop.mapper.OrderMapper;
import com.DatShop.model.Order;
import com.DatShop.model.OrderItem;
import com.DatShop.model.OrderStatus;
import com.DatShop.model.Payment;
import com.DatShop.model.PaymentStatus;
import com.DatShop.model.ProductVariant;
import com.DatShop.model.Voucher;
import com.DatShop.repository.OrderItemRepository;
import com.DatShop.repository.PaymentRepository;
import com.DatShop.repository.ProductVariantRepository;
import com.DatShop.repository.VoucherRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCancellationService {

    static final String UNSAFE_RESTORE = "Order inventory cannot be restored safely";
    static final String UNSAFE_VOUCHER = "Voucher usage cannot be restored safely";

    /**
     * Cancellation lock order is always:
     * 1. Order (already locked by the caller)
     * 2. Payment if present (PESSIMISTIC_WRITE) — paid check happens before restore
     * 3. ProductVariants sorted by id ASC (PESSIMISTIC_WRITE)
     * 4. Voucher when the order consumed one (PESSIMISTIC_WRITE)
     *
     * Payment admin updates lock Payment only, so they cannot invert this order.
     */
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VoucherRepository voucherRepository;
    private final PaymentRepository paymentRepository;

    public OrderCancellationService(
            OrderItemRepository orderItemRepository,
            ProductVariantRepository productVariantRepository,
            VoucherRepository voucherRepository,
            PaymentRepository paymentRepository) {
        this.orderItemRepository = orderItemRepository;
        this.productVariantRepository = productVariantRepository;
        this.voucherRepository = voucherRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public OrderDetailResponse restoreAndCancel(Order lockedOrder) {
        Payment payment = paymentRepository.findByOrder_IdForUpdate(lockedOrder.getId()).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
            throw new ConflictException(PaymentService.PAID_CANCEL_BLOCKED);
        }
        List<OrderItem> items = orderItemRepository.findByOrder_IdOrderByIdAsc(lockedOrder.getId());
        Map<Long, Integer> restoreQty = new LinkedHashMap<>();
        for (OrderItem item : items) {
            restoreQty.merge(item.getVariantId(), item.getQuantity(), Integer::sum);
        }
        List<Long> variantIds = restoreQty.keySet().stream().sorted().toList();
        List<ProductVariant> locked = List.of();
        if (!variantIds.isEmpty()) {
            locked = productVariantRepository.lockForRestoreByIdsOrderByIdAsc(variantIds);
            if (locked.size() != variantIds.size()) {
                throw new ConflictException(UNSAFE_RESTORE);
            }
        }
        Voucher voucher = lockConsumedVoucher(lockedOrder);
        for (ProductVariant variant : locked) {
            int add = restoreQty.getOrDefault(variant.getId(), 0);
            variant.setStockQuantity(variant.getStockQuantity() + add);
        }
        if (voucher != null) {
            voucher.setUsedCount(voucher.getUsedCount() - 1);
        }
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.CANCELLED);
        }
        lockedOrder.setStatus(OrderStatus.CANCELLED);
        return OrderMapper.toDetail(lockedOrder, items);
    }

    private Voucher lockConsumedVoucher(Order order) {
        if (order.getVoucher() == null) {
            return null;
        }
        Voucher voucher = voucherRepository.findByIdForUpdate(order.getVoucher().getId())
                .orElseThrow(() -> new ConflictException(UNSAFE_VOUCHER));
        int used = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
        if (used < 1) {
            throw new ConflictException(UNSAFE_VOUCHER);
        }
        return voucher;
    }
}
