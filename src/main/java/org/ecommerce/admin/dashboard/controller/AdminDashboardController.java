package org.ecommerce.admin.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.ecommerce.admin.dashboard.dto.DashboardSummaryResponse;
import org.ecommerce.admin.dashboard.dto.OrderStatisticsResponse;
import org.ecommerce.admin.dashboard.dto.ProductCategoryStatisticsResponse;
import org.ecommerce.admin.dashboard.dto.UserStatisticsResponse;
import org.ecommerce.admin.dashboard.service.AdminDashboardService;
import org.ecommerce.common.response.ApiSuccessResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard", description = "APIs for admin dashboard statistics and analytics")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {
    private final AdminDashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(
            summary = "Get dashboard summary",
            description = "Returns overall counts for users, products, categories, orders, reviews, coupons and payments."
    )
    public ResponseEntity<ApiSuccessResponse<DashboardSummaryResponse>> getSummary(HttpServletRequest request) {
        DashboardSummaryResponse response = dashboardService.getSummary();

        return ResponseEntity.ok(
                ApiSuccessResponse.<DashboardSummaryResponse>builder()
                        .success(true)
                        .message("Dashboard summary fetched successfully")
                        .data(response)
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @GetMapping("/users/statistics")
    @Operation(
            summary = "Get user statistics",
            description = "Returns monthly user registration statistics."
    )
    public ResponseEntity<ApiSuccessResponse<List<UserStatisticsResponse>>> getUserStatistics(HttpServletRequest request) {
        List<UserStatisticsResponse> response = dashboardService.getUserStatistics();

        return ResponseEntity.ok(
                ApiSuccessResponse.<List<UserStatisticsResponse>>builder()
                        .success(true)
                        .message("User statistics fetched successfully")
                        .data(response)
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @GetMapping("/orders/statistics")
    @Operation(summary = "Get order statistics", description = "Returns order counts grouped by order status.")
    public ResponseEntity<ApiSuccessResponse<List<OrderStatisticsResponse>>> getOrderStatistics(
            HttpServletRequest request
    ) {
        List<OrderStatisticsResponse> response = dashboardService.getOrderStatistics();

        return ResponseEntity.ok(
                ApiSuccessResponse.<List<OrderStatisticsResponse>>builder()
                        .success(true)
                        .message("Order statistics fetched successfully")
                        .data(response)
                        .path(request.getRequestURI())
                        .build()
        );
    }

    @GetMapping("/products/statistics")
    @Operation(
            summary = "Get product category statistics",
            description = "Returns product counts grouped by category."
    )
    public ResponseEntity<ApiSuccessResponse<List<ProductCategoryStatisticsResponse>>> getProductStatistics(
            HttpServletRequest request
    ) {
        List<ProductCategoryStatisticsResponse> response = dashboardService.getProductCategoryStatistics();

        return ResponseEntity.ok(
                ApiSuccessResponse.<List<ProductCategoryStatisticsResponse>>builder()
                        .success(true)
                        .message("Product statistics fetched successfully")
                        .data(response)
                        .path(request.getRequestURI())
                        .build()
        );
    }
}
