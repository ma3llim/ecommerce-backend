package org.ecommerce.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.auth.entities.User;
import org.ecommerce.auth.repository.UserRepository;
import org.ecommerce.catelog.entities.Product;
import org.ecommerce.catelog.entities.ProductVariant;
import org.ecommerce.catelog.repository.ProductRepository;
import org.ecommerce.catelog.repository.ProductVariantRepository;
import org.ecommerce.common.exception.BadRequestException;
import org.ecommerce.common.exception.ResourceNotFoundException;
import org.ecommerce.order.service.PaymentService;
import org.ecommerce.review.dtos.request.CreateReviewRequest;
import org.ecommerce.review.dtos.request.UpdateReviewRequest;
import org.ecommerce.review.dtos.response.ReviewResponse;
import org.ecommerce.review.entities.Review;
import org.ecommerce.review.repository.ReviewRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewService {
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ReviewRepository reviewRepository;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public ReviewResponse getMyReview(UUID productId, UUID productVariantId, Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();

        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("Get review failed: user not found, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        Review review = reviewRepository.findByUserIdAndProductIdAndProductVariantId(user.getId(), productId, productVariantId).orElseThrow(() -> {
            log.info("Review not found for user and product variant. userId={}, productId={}, variantId={}",
                    userId, productId, productVariantId
            );
            return new ResourceNotFoundException("Review not found");
        });

        return objectMapper.convertValue(review, ReviewResponse.class);
    }

    public ReviewResponse createReview(@Valid CreateReviewRequest request, Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("create review request failed: user not found, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        Product product = productRepository.findByIdAndPublishedTrue(request.productId()).orElseThrow(() -> {
            log.warn("Create review failed: published product not found. productId={}, userId={}", request.productId(), userId);
            return new ResourceNotFoundException("Product not found");
        });

        ProductVariant variant = productVariantRepository.findByIdAndActiveTrue(request.productVariantId()).orElseThrow(() -> {
            log.warn("Create review failed: active product variant not found. variantId={}, productId={}, userId={}",
                    request.productVariantId(), request.productId(), userId
            );
            return new ResourceNotFoundException("Product variant not found");
        });

        if (!variant.getProductId().equals(product.getId())) {
            log.warn("Create review failed: variant does not belong to product. variantId={}, productId={}, userId={}",
                    variant.getId(), product.getId(), userId
            );
            throw new BadRequestException("Product variant does not belong to the specified product");
        }

        if (reviewRepository.existsByProductIdAndProductVariantIdAndUserId(product.getId(), variant.getId(), userId)) {
            log.warn("Create review rejected: user already reviewed product. productId={}, userId={}", product.getId(), userId);
            throw new BadRequestException("You have already reviewed this product");
        }

        // TODO: boolean verifiedPurchase here we need one function to check user is purchase or not in order service
        boolean verifiedPurchase = paymentService.hasPurchasedVariant(user.getId(), product.getId(), variant.getId());
        if (!verifiedPurchase) {
            log.warn("Create review rejected: user has not purchased the product. productId={}, variantId={}, userId={}",
                    request.productId(), request.productVariantId(), userId
            );

            throw new BadRequestException("You can review only products you have purchased");
        }

        Review review = Review.builder()
                .productId(request.productId())
                .productVariantId(request.productVariantId())
                .userId(userId)
                .rating(request.rating())
                .title(request.title())
                .review(request.review())
                .isVerifiedPurchase(true)
                .build();

        Review savedReview = reviewRepository.save(review);

        log.info("Review created successfully. reviewId={}, productId={}, variantId={}, userId={}",
                savedReview.getId(), savedReview.getProductId(), savedReview.getProductVariantId(), userId);
        return objectMapper.convertValue(savedReview, ReviewResponse.class);
    }

    public ReviewResponse updateReview(UUID reviewId, @Valid UpdateReviewRequest request, Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("update review request failed: user not found, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        Review review = reviewRepository.findByIdAndUserId(reviewId, userId).orElseThrow(() -> {
            log.warn("Update review failed: review not found or user is not the owner. reviewId={}, userId={}",
                    reviewId, user.getId());
            return new ResourceNotFoundException("Review not found");
        });

        if (request.rating() != null) {
            review.setRating(request.rating());
        }
        if (request.title() != null) {
            review.setTitle(request.title());
        }
        if (request.review() != null) {
            review.setReview(request.review());
        }

        Review updatedReview = reviewRepository.save(review);

        log.info("Review updated successfully. reviewId={}, userId={}", updatedReview.getId(), userId);
        return objectMapper.convertValue(updatedReview, ReviewResponse.class);
    }

    public void deleteReview(UUID reviewId, Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();

        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("delete review request failed: user not found, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        Review review = reviewRepository.findByIdAndUserId(reviewId, userId).orElseThrow(() -> {
            log.warn("Delete review failed: review not found or user is not the owner. reviewId={}, userId={}",
                    reviewId, user.getId());
            return new ResourceNotFoundException("Review not found");
        });

        reviewRepository.delete(review);
    }
}
