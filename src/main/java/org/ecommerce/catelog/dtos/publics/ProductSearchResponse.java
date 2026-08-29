package org.ecommerce.catelog.dtos.publics;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.UUID;

public record ProductSearchResponse(
        @JsonAlias("id")
        UUID productId,
        String name,
        String slug
) {
}
