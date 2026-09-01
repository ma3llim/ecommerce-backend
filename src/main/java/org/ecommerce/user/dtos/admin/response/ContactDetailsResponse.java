package org.ecommerce.user.dtos.admin.response;

import java.time.Instant;
import java.util.UUID;

public record ContactDetailsResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String subject,
        String message,
        Instant createdAt
) {
}