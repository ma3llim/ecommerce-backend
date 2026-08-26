package org.ecommerce.catelog.dtos.admin.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String name,
        String description,
        Map<String, Object> specifications,
        boolean published,
        Instant createdAt
) {
}
