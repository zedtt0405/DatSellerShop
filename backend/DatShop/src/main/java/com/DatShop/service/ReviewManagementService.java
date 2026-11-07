package com.DatShop.service;

import com.DatShop.dto.catalog.PageResponse;
import com.DatShop.dto.review.AdminReviewResponse;
import com.DatShop.dto.review.ReviewStatusUpdateRequest;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.ReviewMapper;
import com.DatShop.model.ProductReview;
import com.DatShop.model.ReviewStatus;
import com.DatShop.repository.ProductImageRepository;
import com.DatShop.repository.ProductReviewRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReviewManagementService {

    private static final String NOT_FOUND = "Review not found";

    private final ProductReviewRepository productReviewRepository;
    private final ProductImageRepository productImageRepository;

    public ReviewManagementService(
            ProductReviewRepository productReviewRepository,
            ProductImageRepository productImageRepository) {
        this.productReviewRepository = productReviewRepository;
        this.productImageRepository = productImageRepository;
    }

    public PageResponse<AdminReviewResponse> list(ReviewStatus status, Integer rating, Long productId, String q, int page, int size) {
        Page<ProductReview> result = productReviewRepository.findAll(
                ReviewSpecifications.management(status, rating, productId, q),
                ProductQuerySupport.pageable(page, size));
        Map<Long, String> images = primaryImageUrls(result.getContent().stream()
                .map(review -> review.getProduct().getId())
                .toList());
        return ProductQuerySupport.pageResponse(result, result.getContent().stream()
                .map(review -> ReviewMapper.toAdmin(review, images.get(review.getProduct().getId())))
                .toList());
    }

    public AdminReviewResponse getById(Long reviewId) {
        ProductReview review = productReviewRepository.findDetailsById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        return ReviewMapper.toAdmin(review, primaryImageUrl(review.getProduct().getId()));
    }

    @Transactional
    public AdminReviewResponse updateStatus(Long reviewId, ReviewStatusUpdateRequest request) {
        ProductReview review = productReviewRepository.findDetailsById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        review.setStatus(request.status());
        return ReviewMapper.toAdmin(review, primaryImageUrl(review.getProduct().getId()));
    }

    private String primaryImageUrl(Long productId) {
        return primaryImageUrls(List.of(productId)).get(productId);
    }

    private Map<Long, String> primaryImageUrls(List<Long> productIds) {
        return PrimaryImages.urlsByProductId(productImageRepository, productIds);
    }
}
