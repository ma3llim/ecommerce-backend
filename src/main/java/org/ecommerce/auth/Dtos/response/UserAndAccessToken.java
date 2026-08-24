package org.ecommerce.auth.Dtos.response;

import lombok.Builder;

@Builder
public record UserAndAccessToken(
        String accessToken, UserResponseDto userResponseDto
) {
}
