package com.DatShop.mapper;

import com.DatShop.dto.review.AdminReviewResponse;
import com.DatShop.dto.review.MyReviewResponse;
import com.DatShop.dto.review.ReviewProductSummary;
import com.DatShop.dto.review.ReviewResponse;
import com.DatShop.dto.review.ReviewerSummary;
import com.DatShop.model.Product;
import com.DatShop.model.ProductReview;
import com.DatShop.model.User;

public final class ReviewMapper {

    private ReviewMapper() {
    }

    public static ReviewResponse toPublic(ProductReview review) {
        return new ReviewResponse(
                review.getId(),
                review.getRating(),
                review.getComment(),
                reviewerName(review.getUser()),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }

    public static MyReviewResponse toMine(ProductReview review, String primaryImageUrl) {
        return new MyReviewResponse(
                review.getId(),
                productSummary(review.getProduct(), primaryImageUrl),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }

    public static AdminReviewResponse toAdmin(ProductReview review, String primaryImageUrl) {
        User user = review.getUser();
        return new AdminReviewResponse(
                review.getId(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                productSummary(review.getProduct(), primaryImageUrl),
                new ReviewerSummary(user.getId(), user.getUsername(), user.getFullName()),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }

    private static ReviewProductSummary productSummary(Product product, String primaryImageUrl) {
        return new ReviewProductSummary(product.getId(), product.getName(), product.getSlug(), primaryImageUrl);
    }

    private static String reviewerName(User user) {
        String fullName = user.getFullName() == null ? null : user.getFullName().trim();
        if (fullName != null && !fullName.isEmpty()) {
            return fullName;
        }
        return user.getUsername();
    }
}
