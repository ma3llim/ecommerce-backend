package org.ecommerce.admin.dashboard.dto;

import lombok.Builder;

@Builder
public record DashboardSummaryResponse(
        long totalUsers,
        long totalProducts,
        long totalCategories,
        long totalOrders,
        long totalReviews,
        long totalCoupons,
        long totalPayments
) {
}
