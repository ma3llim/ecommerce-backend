package org.ecommerce.catelog.controller.publics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.ecommerce.catelog.dtos.admin.response.CategoryResponse;
import org.ecommerce.catelog.service.publics.CategoryService;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.response.ApiSuccessResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/categories")
@Tag(name = "Category", description = "Public APIs for browsing product categories")
public class CategoryController {
    private final CategoryService categoryService;

    @Operation(summary = "Get all categories", description = "Retrieves a paginated list of active product categories.")
    @GetMapping
    public ResponseEntity<ApiSuccessResponse<PageResponse<CategoryResponse>>> getAllCategories(
            Pageable pageable, HttpServletRequest request) {
        PageResponse<CategoryResponse> data = categoryService.getAllCategories(pageable);

        return ResponseEntity.ok(
                ApiSuccessResponse.<PageResponse<CategoryResponse>>builder()
                        .success(true)
                        .message("Categories fetched successfully.")
                        .data(data)
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
