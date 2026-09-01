package org.ecommerce.user.dtos.admin.response;

import java.time.Instant;
import java.util.UUID;

public record NewsletterResponse(
        UUID id,
        String email,
        Instant createdAt
) {
}