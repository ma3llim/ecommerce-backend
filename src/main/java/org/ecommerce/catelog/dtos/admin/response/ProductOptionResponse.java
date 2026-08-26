package org.ecommerce.catelog.dtos.admin.response;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.UUID;

public record ProductOptionResponse(
        @JsonAlias("id")
        UUID productId,
        String productName
) {
}
