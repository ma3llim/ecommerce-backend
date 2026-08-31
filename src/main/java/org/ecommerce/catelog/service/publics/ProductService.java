package org.ecommerce.catelog.service.publics;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.catelog.dtos.publics.*;
import org.ecommerce.catelog.entities.Product;
import org.ecommerce.catelog.entities.ProductFaq;
import org.ecommerce.catelog.entities.ProductVariant;
import org.ecommerce.catelog.entities.ProductVariantImage;
import org.ecommerce.catelog.repository.*;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.exception.ResourceNotFoundException;
import org.ecommerce.review.entities.Review;
import org.ecommerce.review.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantImageRepository productVariantImageRepository;
    private final ProductFaqRepository productFaqRepository;
    private final ReviewRepository reviewRepository;
    private final ObjectMapper objectMapper;

    public PageResponse<ProductListResponse> allProducts(String category, Pageable pageable) {
        Page<Product> products;

        if (category != null && !category.isBlank()) {
            UUID categoryId = categoryRepository.findBySlugAndActiveTrue(category).orElseThrow(() -> {
                        log.warn("Fetch products failed: active category not found. categorySlug={}", category);
                        return new ResourceNotFoundException("Category not found");
                    }
            ).getId();

            products = productRepository.findByCategoryIdAndPublishedTrue(categoryId, pageable);
        } else {
            products = productRepository.findByPublishedTrue(pageable);
        }

        List<UUID> defaultVariantIds = products.stream().map(Product::getDefaultVariantId).filter(Objects::nonNull).toList();

        List<ProductVariant> variants = defaultVariantIds.isEmpty() ? List.of() : productVariantRepository.findAllByIdInAndActiveTrue(defaultVariantIds);

        Map<UUID, ProductVariant> variantMap = variants.stream().collect(Collectors.toMap(
                ProductVariant::getId,
                Function.identity()
        ));

        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        List<ProductVariantImage> images = variantIds.isEmpty() ? List.of() : productVariantImageRepository.findAllByProductVariantIdInAndPrimaryTrue(variantIds);

        Map<UUID, String> imageMap = images.stream().collect(Collectors
                .toMap(ProductVariantImage::getProductVariantId, ProductVariantImage::getImageUrl));

        List<ProductListResponse> content = products.getContent().stream().map(product -> {
            ProductVariant variant = variantMap.get(product.getDefaultVariantId());
            String imageUrl = imageMap.get(product.getDefaultVariantId());

            return new ProductListResponse(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getSlug(),
                    variant != null ? variant.getPrice() : null,
                    imageUrl
            );
        }).toList();

        return PageResponse.<ProductListResponse>builder()
                .content(content)
                .page(products.getNumber())
                .size(products.getSize())
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .first(products.isFirst())
                .last(products.isLast()).build();
    }

    public org.ecommerce.catelog.dtos.publics.ProductDetailsResponse productDetails(String productSlug) {
        Product product = productRepository.findBySlugAndPublishedTrue(productSlug).orElseThrow(() -> {
            log.warn("Fetch product details failed: published product not found. productSlug={}", productSlug);
            return new ResourceNotFoundException("Product not found");
        });

        List<ProductVariant> variants = productVariantRepository.findAllByProductIdAndActiveTrue(product.getId());
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();

        List<ProductVariantImage> images = variantIds.isEmpty() ? List.of() :
                productVariantImageRepository.findAllByProductVariantIdIn(variantIds);

        Map<UUID, List<ProductVariantImage>> imageMap = images.stream().collect(
                Collectors.groupingBy(ProductVariantImage::getProductVariantId)
        );

        List<ProductVariantResponse> variantResponses = variants.stream().map(variant -> {
            List<ProductVariantImage> variantImages = imageMap.getOrDefault(variant.getId(), List.of());

            List<ProductVariantImageResponse> imageResponses = variantImages.stream()
                    .map(image -> new ProductVariantImageResponse(
                            image.getId(),
                            image.getImageUrl(),
                            image.getDisplayOrder(),
                            image.isPrimary()
                    )).toList();

            return new ProductVariantResponse(
                    variant.getId(),
                    variant.getSku(),
                    variant.getPrice(),
                    variant.getStockQuantity(),
                    variant.getAttributes(),
                    imageResponses
            );
        }).toList();

        List<ProductFaq> faqs = productFaqRepository.findAllByProductIdOrderByCreatedAtAsc(product.getId());

        List<ProductFaqResponse> faqResponses = faqs.stream().map(faq -> new ProductFaqResponse(
                faq.getId(),
                faq.getQuestion(),
                faq.getAnswer()
        )).toList();

        return new ProductDetailsResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getSpecifications(),
                product.getDefaultVariantId(),
                variantResponses,
                faqResponses
        );
    }

    public PageResponse<ProductReviewResponse> getProductReview(String productSlug, Pageable pageable) {
        Product product = productRepository.findBySlugAndPublishedTrue(productSlug).orElseThrow(() -> {
            log.warn("Product not found for reviews. slug={}", productSlug);
            return new ResourceNotFoundException("Product not found");
        });

        Page<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(product.getId(), pageable);
        List<ProductReviewResponse> content = reviews.getContent().stream().map(
                review -> ProductReviewResponse.builder()
                        .id(review.getId())
                        .rating(review.getRating())
                        .title(review.getTitle())
                        .review(review.getReview())
                        .verifiedPurchase(review.isVerifiedPurchase())
                        .createdAt(review.getCreatedAt())
                        .build()
        ).toList();

        return new PageResponse<>(
                content,
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages(),
                reviews.isFirst(),
                reviews.isLast()
        );
    }

    public PageResponse<ProductSearchResponse> searchForProducts(String keyword, Pageable pageable) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        Page<ProductSearchResponse> productSearchResponses = productRepository.searchProducts(normalizedKeyword, pageable);

        List<ProductSearchResponse> content = productSearchResponses.getContent().stream()
                .map(product -> objectMapper.convertValue(product, ProductSearchResponse.class))
                .toList();

        return new PageResponse<>(
                content,
                productSearchResponses.getNumber(),
                productSearchResponses.getSize(),
                productSearchResponses.getTotalElements(),
                productSearchResponses.getTotalPages(),
                productSearchResponses.isFirst(),
                productSearchResponses.isLast()
        );
    }
}
