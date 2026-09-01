package org.ecommerce.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ecommerce.common.response.ApiSuccessResponse;
import org.ecommerce.review.dtos.request.CreateReviewRequest;
import org.ecommerce.review.dtos.request.UpdateReviewRequest;
import org.ecommerce.review.dtos.response.ReviewResponse;
import org.ecommerce.review.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@PreAuthorize("hasRole('USER')")
@RequestMapping("/api/v1/reviews")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Review Management", description = "APIs for authenticated users to create, update, and delete their product reviews")
public class ReviewController {
    private final ReviewService reviewService;

    @Operation(summary = "Create a product review", description = "Allows an authenticated user to submit a review for a purchased product.")
    @PostMapping
    public ResponseEntity<ApiSuccessResponse<ReviewResponse>> createReview(
            @Valid @RequestBody CreateReviewRequest request, Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        ReviewResponse response = reviewService.createReview(request, authentication);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccessResponse.<ReviewResponse>builder()
                        .success(true)
                        .message("Review submitted successfully")
                        .data(response)
                        .path(httpRequest.getRequestURI())
                        .build()
                );
    }

    @Operation(summary = "Update a review", description = "Allows an authenticated user to update their own product review.")
    @PatchMapping("/{reviewId}")
    public ResponseEntity<ApiSuccessResponse<ReviewResponse>> updateReview(
            @PathVariable UUID reviewId, @Valid @RequestBody UpdateReviewRequest request,
            Authentication authentication, HttpServletRequest httpRequest
    ) {
        ReviewResponse response = reviewService.updateReview(reviewId, request, authentication);

        return ResponseEntity.ok(ApiSuccessResponse.<ReviewResponse>builder()
                .success(true)
                .message("Review updated successfully")
                .data(response)
                .path(httpRequest.getRequestURI())
                .build()
        );
    }

    @Operation(summary = "Delete a review", description = "Allows an authenticated user to delete their own product review.")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiSuccessResponse<Void>> deleteReview(
            @PathVariable UUID reviewId, Authentication authentication, HttpServletRequest httpRequest
    ) {
        reviewService.deleteReview(reviewId, authentication);

        return ResponseEntity.ok(ApiSuccessResponse.<Void>builder()
                .success(true)
                .message("Review deleted successfully")
                .data(null)
                .path(httpRequest.getRequestURI())
                .build()
        );
    }

    @Operation(
            summary = "Get my review for a product variant",
            description = "Retrieves the authenticated user's review for the specified product and product variant."
    )
    @GetMapping("/product/{productId}/variant/{productVariantId}")
    public ResponseEntity<ApiSuccessResponse<ReviewResponse>> getMyReview(
            @PathVariable UUID productId, @PathVariable UUID productVariantId, Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        ReviewResponse response = reviewService.getMyReview(productId, productVariantId, authentication);

        return ResponseEntity.ok(ApiSuccessResponse.<ReviewResponse>builder()
                .success(true)
                .message("Review retrieved successfully")
                .data(response)
                .path(httpRequest.getRequestURI())
                .build()
        );
    }
}
