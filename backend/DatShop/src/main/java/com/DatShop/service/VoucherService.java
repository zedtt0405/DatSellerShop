package com.DatShop.service;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.voucher.VoucherCreateRequest;
import com.DatShop.dto.voucher.VoucherResponse;
import com.DatShop.dto.voucher.VoucherUpdateRequest;
import com.DatShop.dto.voucher.VoucherValidationRequest;
import com.DatShop.dto.voucher.VoucherValidationResponse;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.DuplicateResourceException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.VoucherMapper;
import com.DatShop.model.Voucher;
import com.DatShop.model.VoucherType;
import com.DatShop.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoucherService {

    private static final String INVALID_CODE = "Voucher is invalid";

    private final VoucherRepository voucherRepository;
    private final VoucherCalculator voucherCalculator;
    private final CartService cartService;
    private final RequestValidator requestValidator;
    private final Clock clock;

    public VoucherService(
            VoucherRepository voucherRepository,
            VoucherCalculator voucherCalculator,
            CartService cartService,
            RequestValidator requestValidator,
            Clock clock) {
        this.voucherRepository = voucherRepository;
        this.voucherCalculator = voucherCalculator;
        this.cartService = cartService;
        this.requestValidator = requestValidator;
        this.clock = clock;
    }

    public PageResponse<VoucherResponse> list(Boolean active, VoucherType type, String q, int page, int size) {
        var pageable = ProductQuerySupport.pageable(page, size);
        var result = voucherRepository.findAll(VoucherSpecifications.management(active, type, q), pageable);
        return ProductQuerySupport.pageResponse(result, result.getContent().stream()
                .map(VoucherMapper::toResponse)
                .toList());
    }

    public VoucherResponse getById(Long id) {
        return VoucherMapper.toResponse(requireVoucher(id));
    }

    @Transactional
    public VoucherResponse create(VoucherCreateRequest request) {
        VoucherCreateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        assertBusinessRules(
                normalized.type(),
                normalized.discountValue(),
                normalized.maxDiscountAmount(),
                normalized.startAt(),
                normalized.endAt());
        if (voucherRepository.existsByCodeIgnoreCase(normalized.code())) {
            throw new DuplicateResourceException("Voucher code already exists");
        }
        Voucher voucher = new Voucher();
        apply(
                voucher,
                normalized.code(),
                normalized.type(),
                normalized.discountValue(),
                normalized.minOrderAmount(),
                normalized.maxDiscountAmount(),
                normalized.usageLimit(),
                normalized.startAt(),
                normalized.endAt(),
                normalized.active());
        voucher.setUsedCount(0);
        return VoucherMapper.toResponse(voucherRepository.save(voucher));
    }

    @Transactional
    public VoucherResponse update(Long id, VoucherUpdateRequest request) {
        VoucherUpdateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        assertBusinessRules(
                normalized.type(),
                normalized.discountValue(),
                normalized.maxDiscountAmount(),
                normalized.startAt(),
                normalized.endAt());
        Voucher voucher = requireVoucher(id);
        if (voucherRepository.existsByCodeIgnoreCaseAndIdNot(normalized.code(), id)) {
            throw new DuplicateResourceException("Voucher code already exists");
        }
        apply(
                voucher,
                normalized.code(),
                normalized.type(),
                normalized.discountValue(),
                normalized.minOrderAmount(),
                normalized.maxDiscountAmount(),
                normalized.usageLimit(),
                normalized.startAt(),
                normalized.endAt(),
                normalized.active());
        return VoucherMapper.toResponse(voucher);
    }

    public VoucherValidationResponse preview(Authentication authentication, VoucherValidationRequest request) {
        String code = normalizeCode(request.code());
        var cart = cartService.getCart(authentication);
        BigDecimal subtotal = VoucherEvaluation.money(cart.subtotal() == null ? BigDecimal.ZERO : cart.subtotal());
        if (cart.items() == null || cart.items().isEmpty()) {
            return invalid(code, subtotal, "Cart is empty");
        }
        if (cart.items().stream().anyMatch(item -> !Boolean.TRUE.equals(item.available()))) {
            return invalid(code, subtotal, "Cart contains unavailable items");
        }
        return voucherRepository.findByCodeIgnoreCase(code)
                .map(voucher -> toPreview(voucher.getCode(), subtotal,
                        voucherCalculator.evaluate(voucher, subtotal, clock.instant())))
                .orElseGet(() -> invalid(code, subtotal, INVALID_CODE));
    }

    private Voucher requireVoucher(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found"));
    }

    private static VoucherCreateRequest normalize(VoucherCreateRequest request) {
        return new VoucherCreateRequest(
                normalizeCode(request.code()),
                request.type(),
                request.discountValue(),
                request.minOrderAmount(),
                request.maxDiscountAmount(),
                request.usageLimit(),
                request.startAt(),
                request.endAt(),
                request.active());
    }

    private static VoucherUpdateRequest normalize(VoucherUpdateRequest request) {
        return new VoucherUpdateRequest(
                normalizeCode(request.code()),
                request.type(),
                request.discountValue(),
                request.minOrderAmount(),
                request.maxDiscountAmount(),
                request.usageLimit(),
                request.startAt(),
                request.endAt(),
                request.active());
    }

    static String normalizeCode(String code) {
        String trimmed = Text.emptyToNull(code);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static void assertBusinessRules(
            VoucherType type,
            BigDecimal discountValue,
            BigDecimal maxDiscountAmount,
            Instant startAt,
            Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new BusinessRuleException("endAt must be after startAt");
        }
        if (type == VoucherType.PERCENT && discountValue.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessRuleException("PERCENT discountValue must be at most 100");
        }
        if (type == VoucherType.FIXED && maxDiscountAmount != null) {
            throw new BusinessRuleException("FIXED vouchers must not set maxDiscountAmount");
        }
    }

    private static void apply(
            Voucher voucher,
            String code,
            VoucherType type,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            Integer usageLimit,
            Instant startAt,
            Instant endAt,
            Boolean active) {
        voucher.setCode(code);
        voucher.setType(type);
        voucher.setDiscountValue(discountValue);
        voucher.setMinOrderAmount(minOrderAmount);
        voucher.setMaxDiscountAmount(maxDiscountAmount);
        voucher.setUsageLimit(usageLimit);
        voucher.setStartAt(startAt);
        voucher.setEndAt(endAt);
        voucher.setActive(active);
    }

    private static VoucherValidationResponse toPreview(String code, BigDecimal subtotal, VoucherEvaluation evaluation) {
        BigDecimal discount = evaluation.discountAmount();
        return new VoucherValidationResponse(
                code,
                evaluation.valid(),
                subtotal,
                discount,
                VoucherEvaluation.money(subtotal.subtract(discount)),
                evaluation.message());
    }

    private static VoucherValidationResponse invalid(String code, BigDecimal subtotal, String message) {
        return new VoucherValidationResponse(
                code,
                false,
                subtotal,
                VoucherEvaluation.money(BigDecimal.ZERO),
                subtotal,
                message);
    }
}
