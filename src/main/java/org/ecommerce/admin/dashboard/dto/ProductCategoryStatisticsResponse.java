package org.ecommerce.admin.dashboard.dto;

import lombok.Builder;

@Builder
public record ProductCategoryStatisticsResponse(
        String category,
        long count
) {
}
