package org.ecommerce.admin.dashboard.dto;

import lombok.Builder;

@Builder
public record OrderStatisticsResponse(
        String status,
        long count
) {
}
