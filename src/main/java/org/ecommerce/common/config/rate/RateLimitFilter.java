package org.ecommerce.common.config.rate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.common.config.properties.RateLimitProperties;
import org.ecommerce.common.constants.RedisKeyConstants;
import org.ecommerce.common.response.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;
    private final RateLimitProperties rateLimitProperties;
    private final ProxyManager<byte[]> buckets;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        String path = request.getRequestURI();

        Bandwidth limit = isAuthEndpoint(path) ? createBandwidth(rateLimitProperties.getAuth())
                : createBandwidth(rateLimitProperties.getDefaultLimit());

        String bucketKey = RedisKeyConstants.RATE_LIMIT + getLimitType(path) + ":" + clientIp;

        BucketConfiguration configuration = BucketConfiguration.builder().addLimit(limit).build();

        Bucket bucket = buckets.builder().build(bucketKey.getBytes(StandardCharsets.UTF_8), () -> configuration);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .success(false)
                .message("Rate limit exceeded. Please try again later.")
                .errorCode("RATE_LIMIT_EXCEEDED")
                .path(request.getRequestURI())
                .build();

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    private Bandwidth createBandwidth(RateLimitProperties.Limit config) {
        return Bandwidth.builder()
                .capacity(config.getCapacity())
                .refillGreedy(
                        config.getRefillTokens(),
                        config.getRefillDuration()
                )
                .build();
    }

    private boolean isAuthEndpoint(String path) {
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/forgot-password")
                || path.equals("/api/v1/auth/resend-verification")
                || path.equals("/api/v1/auth/reset-password");
    }

    private String getLimitType(String path) {
        return isAuthEndpoint(path) ? "auth" : "default";
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}