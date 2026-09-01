package org.ecommerce.user.dtos.admin.response;

import java.time.Instant;
import java.util.UUID;

public record ContactListResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String subject,
        Instant createdAt
) {
}