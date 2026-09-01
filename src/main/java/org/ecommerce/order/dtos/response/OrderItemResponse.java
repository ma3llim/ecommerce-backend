package org.ecommerce.order.dtos.response;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderItemResponse(
        @JsonAlias("id")
        UUID orderItem,
        UUID orderId,
        String productName,
        String imageUrl,
        String variantName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        Instant createdAt
) {
}
