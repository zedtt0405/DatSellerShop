package com.DatShop.service;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.review.MyReviewResponse;
import com.DatShop.dto.review.ProductReviewPageResponse;
import com.DatShop.dto.review.ReviewCreateRequest;
import com.DatShop.dto.review.ReviewResponse;
import com.DatShop.dto.review.ReviewUpdateRequest;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.ConflictException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.ReviewMapper;
import com.DatShop.model.OrderStatus;
import com.DatShop.model.Product;
import com.DatShop.model.ProductReview;
import com.DatShop.model.ReviewStatus;
import com.DatShop.model.User;
import com.DatShop.repository.OrderItemRepository;
import com.DatShop.repository.ProductImageRepository;
import com.DatShop.repository.ProductRepository;
import com.DatShop.repository.ProductReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    static final String NOT_DELIVERED = "Product can only be reviewed after delivery";
    private static final String REVIEW_NOT_FOUND = "Review not found";
    private static final String PRODUCT_NOT_FOUND = "Product not found";
    private static final String ALREADY_REVIEWED = "Product already reviewed";

    private final CurrentUserService currentUserService;
    private final ProductRepository productRepository;
    private final ProductReviewRepository productReviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductImageRepository productImageRepository;

    public ReviewService(
            CurrentUserService currentUserService,
            ProductRepository productRepository,
            ProductReviewRepository productReviewRepository,
            OrderItemRepository orderItemRepository,
            ProductImageRepository productImageRepository) {
        this.currentUserService = currentUserService;
        this.productRepository = productRepository;
        this.productReviewRepository = productReviewRepository;
        this.orderItemRepository = orderItemRepository;
        this.productImageRepository = productImageRepository;
    }

    public ProductReviewPageResponse listPublic(String slug, int page, int size, String sort) {
        Product product = productRepository.findBySlug(slug)
                .filter(CatalogAvailability::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT_NOT_FOUND));
        Page<ProductReview> result = productReviewRepository.findByProduct_IdAndStatus(
                product.getId(),
                ReviewStatus.PUBLISHED,
                ReviewQuerySupport.publicPageable(page, size, sort));
        long reviewCount = result.getTotalElements();
        return new ProductReviewPageResponse(
                result.getContent().stream().map(ReviewMapper::toPublic).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                averageRating(product.getId(), reviewCount),
                reviewCount);
    }

    public PageResponse<MyReviewResponse> listMine(Authentication authentication, int page, int size) {
        User user = currentUserService.requireUser(authentication);
        Page<ProductReview> result = productReviewRepository.findByUser_Id(
                user.getId(), ProductQuerySupport.pageable(page, size));
        Map<Long, String> images = primaryImageUrls(result.getContent().stream()
                .map(review -> review.getProduct().getId())
                .toList());
        return ProductQuerySupport.pageResponse(result, result.getContent().stream()
                .map(review -> ReviewMapper.toMine(review, images.get(review.getProduct().getId())))
                .toList());
    }

    @Transactional
    public MyReviewResponse create(Authentication authentication, ReviewCreateRequest request) {
        User user = currentUserService.requireUser(authentication);
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT_NOT_FOUND));
        if (productReviewRepository.existsByUser_IdAndProduct_Id(user.getId(), product.getId())) {
            throw new ConflictException(ALREADY_REVIEWED);
        }
        if (!orderItemRepository.existsPurchasedProduct(user.getId(), product.getId(), OrderStatus.DELIVERED)) {
            throw new BusinessRuleException(NOT_DELIVERED);
        }
        ProductReview review = new ProductReview();
        review.setUser(user);
        review.setProduct(product);
        review.setRating(request.rating());
        review.setComment(Text.emptyToNull(request.comment()));
        review.setStatus(ReviewStatus.PUBLISHED);
        productReviewRepository.save(review);
        return ReviewMapper.toMine(review, primaryImageUrl(product.getId()));
    }

    @Transactional
    public MyReviewResponse updateMine(Authentication authentication, Long reviewId, ReviewUpdateRequest request) {
        User user = currentUserService.requireUser(authentication);
        ProductReview review = productReviewRepository.findByIdAndUser_Id(reviewId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(REVIEW_NOT_FOUND));
        review.setRating(request.rating());
        review.setComment(Text.emptyToNull(request.comment()));
        return ReviewMapper.toMine(review, primaryImageUrl(review.getProduct().getId()));
    }

    @Transactional
    public void deleteMine(Authentication authentication, Long reviewId) {
        User user = currentUserService.requireUser(authentication);
        ProductReview review = productReviewRepository.findByIdAndUser_Id(reviewId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(REVIEW_NOT_FOUND));
        productReviewRepository.delete(review);
    }

    private BigDecimal averageRating(Long productId, long reviewCount) {
        if (reviewCount == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Number sum = productReviewRepository.sumRatingByProductIdAndStatus(productId, ReviewStatus.PUBLISHED);
        long total = sum == null ? 0L : sum.longValue();
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(reviewCount), 2, RoundingMode.HALF_UP);
    }

    private String primaryImageUrl(Long productId) {
        return primaryImageUrls(List.of(productId)).get(productId);
    }

    private Map<Long, String> primaryImageUrls(List<Long> productIds) {
        return PrimaryImages.urlsByProductId(productImageRepository, productIds);
    }
}
