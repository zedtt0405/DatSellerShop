package com.DatShop.service;

import com.DatShop.dto.order.CheckoutRequest;
import com.DatShop.dto.order.OrderDetailResponse;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.CatalogMapper;
import com.DatShop.mapper.OrderMapper;
import com.DatShop.model.Address;
import com.DatShop.model.Cart;
import com.DatShop.model.CartItem;
import com.DatShop.model.Order;
import com.DatShop.model.OrderItem;
import com.DatShop.model.OrderStatus;
import com.DatShop.model.Product;
import com.DatShop.model.ProductVariant;
import com.DatShop.model.User;
import com.DatShop.model.Voucher;
import com.DatShop.repository.AddressRepository;
import com.DatShop.repository.CartItemRepository;
import com.DatShop.repository.CartRepository;
import com.DatShop.repository.OrderItemRepository;
import com.DatShop.repository.OrderRepository;
import com.DatShop.repository.ProductImageRepository;
import com.DatShop.repository.ProductVariantRepository;
import com.DatShop.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {

    static final BigDecimal SHIPPING_FEE = BigDecimal.ZERO.setScale(2);

    /**
     * Checkout lock order is always:
     * 1. current User (PESSIMISTIC_WRITE)
     * 2. ProductVariants sorted by id ASC (PESSIMISTIC_WRITE)
     * 3. Voucher if a code is supplied (PESSIMISTIC_WRITE)
     */
    private static final String EMPTY_CART = "Cart is empty";
    private static final String INVALID_VOUCHER = "Voucher is invalid";

    private final CurrentUserService currentUserService;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherCalculator voucherCalculator;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final Clock clock;

    public CheckoutService(
            CurrentUserService currentUserService,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            AddressRepository addressRepository,
            ProductVariantRepository productVariantRepository,
            ProductImageRepository productImageRepository,
            VoucherRepository voucherRepository,
            VoucherCalculator voucherCalculator,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            Clock clock) {
        this.currentUserService = currentUserService;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.addressRepository = addressRepository;
        this.productVariantRepository = productVariantRepository;
        this.productImageRepository = productImageRepository;
        this.voucherRepository = voucherRepository;
        this.voucherCalculator = voucherCalculator;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.clock = clock;
    }

    @Transactional
    public OrderDetailResponse checkout(Authentication authentication, CheckoutRequest request) {
        User user = currentUserService.lockCurrentUser(authentication);
        Cart cart = cartRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new BusinessRuleException(EMPTY_CART));
        List<CartItem> cartItems = cartItemRepository.findByCart_IdOrderByCreatedAtAscIdAsc(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BusinessRuleException(EMPTY_CART);
        }

        Address address = addressRepository.findByIdAndUser_Id(request.addressId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        List<Long> variantIds = cartItems.stream()
                .map(item -> item.getProductVariant().getId())
                .distinct()
                .sorted()
                .toList();
        List<ProductVariant> locked = productVariantRepository.lockByIdsOrderByIdAsc(variantIds);
        if (locked.size() != variantIds.size()) {
            throw new BusinessRuleException(CatalogAvailability.UNAVAILABLE);
        }
        Map<Long, ProductVariant> variants = new HashMap<>();
        for (ProductVariant variant : locked) {
            variants.put(variant.getId(), variant);
        }

        List<PreparedLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (CartItem cartItem : cartItems) {
            ProductVariant variant = variants.get(cartItem.getProductVariant().getId());
            if (variant == null) {
                throw new BusinessRuleException(CatalogAvailability.UNAVAILABLE);
            }
            CatalogAvailability.assertPurchasableLine(variant, cartItem.getQuantity());
            Product product = variant.getProduct();
            BigDecimal unitPrice = VoucherEvaluation.money(
                    CatalogMapper.effectivePrice(product.getPrice(), product.getSalePrice()));
            BigDecimal lineTotal = VoucherEvaluation.money(
                    unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            subtotal = subtotal.add(lineTotal);
            lines.add(new PreparedLine(variant, product, cartItem.getQuantity(), unitPrice, lineTotal));
        }
        subtotal = VoucherEvaluation.money(subtotal);

        Voucher voucher = null;
        BigDecimal discount = VoucherEvaluation.money(BigDecimal.ZERO);
        String voucherCode = VoucherService.normalizeCode(request.voucherCode());
        if (voucherCode != null) {
            voucher = voucherRepository.findByCodeIgnoreCaseForUpdate(voucherCode)
                    .orElseThrow(() -> new BusinessRuleException(INVALID_VOUCHER));
            VoucherEvaluation evaluation = voucherCalculator.evaluate(voucher, subtotal, clock.instant());
            if (!evaluation.valid()) {
                throw new BusinessRuleException(evaluation.message());
            }
            discount = evaluation.discountAmount();
        }

        BigDecimal total = VoucherEvaluation.money(subtotal.add(SHIPPING_FEE).subtract(discount));
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = VoucherEvaluation.money(BigDecimal.ZERO);
        }

        Map<Long, String> images = primaryImageUrls(lines);
        Order order = new Order();
        order.setOrderNumber(newOrderNumber());
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setSubtotal(subtotal);
        order.setDiscountAmount(discount);
        order.setShippingFee(SHIPPING_FEE);
        order.setTotalAmount(total);
        if (voucher != null) {
            order.setVoucher(voucher);
            order.setVoucherCode(voucher.getCode());
            order.setVoucherType(voucher.getType());
            order.setVoucherDiscountValue(VoucherEvaluation.money(voucher.getDiscountValue()));
        }
        order.setRecipientName(address.getRecipientName());
        order.setRecipientPhone(address.getPhoneNumber());
        order.setShippingProvince(address.getProvince());
        order.setShippingDistrict(address.getDistrict());
        order.setShippingWard(address.getWard());
        order.setShippingAddressLine(address.getAddressLine());
        order.setCustomerNote(Text.emptyToNull(request.customerNote()));
        orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();
        for (PreparedLine line : lines) {
            ProductVariant variant = line.variant();
            Product product = line.product();
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(product.getId());
            item.setVariantId(variant.getId());
            item.setProductName(product.getName());
            item.setProductSlug(product.getSlug());
            item.setSku(variant.getSku());
            item.setSizeName(variant.getSize() == null ? null : variant.getSize().getName());
            item.setColorName(variant.getColor() == null ? null : variant.getColor().getName());
            item.setProductImageUrl(images.get(product.getId()));
            item.setUnitPrice(line.unitPrice());
            item.setQuantity(line.quantity());
            item.setLineTotal(line.lineTotal());
            orderItems.add(item);

            int remaining = variant.getStockQuantity() - line.quantity();
            if (remaining < 0) {
                throw new BusinessRuleException(CatalogAvailability.OVER_STOCK);
            }
            variant.setStockQuantity(remaining);
        }
        orderItemRepository.saveAll(orderItems);

        if (voucher != null) {
            int used = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
            voucher.setUsedCount(used + 1);
        }

        cartItemRepository.deleteAll(cartItems);
        cartItemRepository.flush();
        return OrderMapper.toDetail(order, orderItems);
    }

    private String newOrderNumber() {
        String candidate;
        do {
            candidate = "ORD-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        } while (orderRepository.existsByOrderNumber(candidate));
        return candidate;
    }

    private Map<Long, String> primaryImageUrls(List<PreparedLine> lines) {
        List<Long> productIds = lines.stream()
                .map(line -> line.product().getId())
                .distinct()
                .toList();
        return PrimaryImages.urlsByProductId(productImageRepository, productIds);
    }

    private record PreparedLine(
            ProductVariant variant,
            Product product,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }
}
