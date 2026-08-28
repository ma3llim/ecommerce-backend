package org.ecommerce.admin.dashboard.dto;

import lombok.Builder;

@Builder
public record UserStatisticsResponse(
        String period,
        long count
) {
}
