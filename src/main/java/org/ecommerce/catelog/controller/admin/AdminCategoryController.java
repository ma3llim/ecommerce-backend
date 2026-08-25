package org.ecommerce.catelog.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ecommerce.catelog.dtos.admin.request.AddCategoryRequest;
import org.ecommerce.catelog.dtos.admin.request.UpdateCategoryRequest;
import org.ecommerce.catelog.dtos.admin.response.CategoryResponse;
import org.ecommerce.catelog.service.admin.AdminCategoryService;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.response.ApiSuccessResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/categories")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Category Management", description = "Admin APIs for creating, viewing, updating, and deleting product categories")
public class AdminCategoryController {
    private final AdminCategoryService adminCategoryService;

    @Operation(summary = "Create a category", description = "Creates a new product category with an optional category image.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiSuccessResponse<CategoryResponse>> createCategory(
            @Valid @ModelAttribute AddCategoryRequest newCategory, HttpServletRequest request) {
        CategoryResponse categoryResponse = adminCategoryService.createCategory(newCategory);

        return ResponseEntity.ok(
                ApiSuccessResponse.<CategoryResponse>builder()
                        .success(true).message("Category created successfully")
                        .data(categoryResponse).path(request.getRequestURI()).build()
        );
    }

    @Operation(summary = "Get all categories", description = "Retrieves a paginated list of product categories.")
    @GetMapping
    public ResponseEntity<ApiSuccessResponse<PageResponse<CategoryResponse>>> getAllCategories(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest request) {
        PageResponse<CategoryResponse> allCategories = adminCategoryService.getAllCategories(pageable);

        return ResponseEntity.ok(
                ApiSuccessResponse.<PageResponse<CategoryResponse>>builder()
                        .success(true).message("Categories retrieved successfully")
                        .data(allCategories)
                        .path(request.getRequestURI()).build()
        );
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "Get category by ID", description = "Retrieves a category by its ID.")
    public ResponseEntity<ApiSuccessResponse<CategoryResponse>> getCategoryById(
            @PathVariable UUID categoryId, HttpServletRequest request
    ) {
        CategoryResponse categoryResponse = adminCategoryService.getCategoryById(categoryId);

        return ResponseEntity.ok(
                ApiSuccessResponse.<CategoryResponse>builder()
                        .success(true)
                        .message("Category retrieved successfully")
                        .data(categoryResponse)
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @Operation(summary = "Update a category", description = "Updates an existing category. Category name, status, and image can be updated independently.")
    @PutMapping(value = "/{categoryId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiSuccessResponse<CategoryResponse>> updateCategory(@PathVariable UUID categoryId,
                                                                               @Valid @ModelAttribute UpdateCategoryRequest categoryRequest, HttpServletRequest request) {
        CategoryResponse categoryResponse = adminCategoryService.updateCategory(categoryId, categoryRequest);
        return ResponseEntity.ok(
                ApiSuccessResponse.<CategoryResponse>builder()
                        .success(true).message("Category updated successfully")
                        .data(categoryResponse).path(request.getRequestURI()).build()
        );
    }

    @Operation(summary = "Delete a category", description = "Deletes an existing category and its associated image.")
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ApiSuccessResponse<Void>> deleteCategory(@PathVariable UUID categoryId, HttpServletRequest request) {
        adminCategoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiSuccessResponse.<Void>builder()
                .success(true)
                .message("Category deleted successfully")
                .data(null)
                .path(request.getRequestURI()).build()
        );
    }
}
