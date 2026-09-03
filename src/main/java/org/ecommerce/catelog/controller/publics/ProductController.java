package org.ecommerce.catelog.controller.publics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.ecommerce.catelog.dtos.publics.ProductDetailsResponse;
import org.ecommerce.catelog.dtos.publics.ProductListResponse;
import org.ecommerce.catelog.dtos.publics.ProductReviewResponse;
import org.ecommerce.catelog.dtos.publics.ProductSearchResponse;
import org.ecommerce.catelog.service.publics.ProductService;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.response.ApiSuccessResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
@Tag(name = "Product", description = "Public APIs for browsing products and viewing product details")
public class ProductController {
    private final ProductService productService;

    @Operation(summary = "Get all products", description = "Retrieves a paginated list of published products. Products can optionally be filtered by category slug.")
    @GetMapping
    public ResponseEntity<ApiSuccessResponse<PageResponse<ProductListResponse>>> allProducts(
            @RequestParam(value = "category", required = false) String category,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest request
    ) {
        PageResponse<ProductListResponse> responsePageResponse = productService.allProducts(category, pageable);

        return ResponseEntity.ok(
                ApiSuccessResponse.<PageResponse<ProductListResponse>>builder()
                        .success(true)
                        .message("Products fetched successfully")
                        .data(responsePageResponse)
                        .path(request.getRequestURI()).build()
        );
    }

    @Operation(summary = "Get product details", description = "Retrieves complete details of a published product by its unique slug, including active variants, primary images, and FAQs.")
    @GetMapping("/{productSlug}")
    public ResponseEntity<ApiSuccessResponse<ProductDetailsResponse>> getDetailProduct(
            @PathVariable(value = "productSlug") String productSlug,
            HttpServletRequest request
    ) {
        ProductDetailsResponse productDetailsResponse = productService.productDetails(productSlug);

        return ResponseEntity.ok(
                ApiSuccessResponse.<ProductDetailsResponse>builder()
                        .success(true)
                        .message("Product details fetched successfully")
                        .data(productDetailsResponse)
                        .path(request.getRequestURI()).build()
        );
    }

    @Operation(summary = "Get product reviews", description = "Retrieves a paginated list of reviews for a published product using its unique slug.")
    @GetMapping("/{productSlug}/reviews")
    public ResponseEntity<ApiSuccessResponse<PageResponse<ProductReviewResponse>>> getProductReview(
            @PathVariable(value = "productSlug") String productSlug,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest request
    ) {
        PageResponse<ProductReviewResponse> productReviewResponse = productService.getProductReview(productSlug, pageable);

        return ResponseEntity.ok(
                ApiSuccessResponse.<PageResponse<ProductReviewResponse>>builder()
                        .success(true)
                        .message("Product reviews fetched successfully")
                        .data(productReviewResponse)
                        .path(request.getRequestURI()).build()
        );
    }

    @GetMapping("/search")
    @Operation(summary = "Search products for selection",
            description = "Searches products by name and returns only the product ID, name, and slug.")
    public ResponseEntity<ApiSuccessResponse<PageResponse<ProductSearchResponse>>> searchProducts(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            HttpServletRequest request
    ) {
        PageResponse<ProductSearchResponse> searchProducts = productService.searchForProducts(keyword, pageable);

        return ResponseEntity.ok(
                ApiSuccessResponse.<PageResponse<ProductSearchResponse>>builder()
                        .success(true)
                        .message("Product search fetched successfully")
                        .data(searchProducts)
                        .path(request.getRequestURI()).build()
        );
    }

    @GetMapping("/tag/{tagSlug}")
    @Operation(summary = "Get products by tag", description = "Retrieve published products associated with a single tag")
    public ResponseEntity<ApiSuccessResponse<PageResponse<ProductListResponse>>> getProductsByTag(
            @PathVariable String tagSlug, Pageable pageable, HttpServletRequest request
    ) {
        PageResponse<ProductListResponse> response = productService.getProductsByTag(tagSlug, pageable);

        return ResponseEntity.ok(ApiSuccessResponse.<PageResponse<ProductListResponse>>builder()
                .success(true)
                .message("Products fetched successfully")
                .data(response)
                .path(request.getRequestURI())
                .build()
        );
    }

}
