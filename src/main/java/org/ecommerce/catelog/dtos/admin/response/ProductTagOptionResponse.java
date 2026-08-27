package org.ecommerce.catelog.dtos.admin.response;

import java.util.UUID;

public record ProductTagOptionResponse(
        UUID productId,
        String productName,
        UUID tagId,
        String tagName
) {
}
