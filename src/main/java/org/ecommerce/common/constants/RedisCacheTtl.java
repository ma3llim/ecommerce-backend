package org.ecommerce.common.constants;

import java.time.Duration;

public final class RedisCacheTtl {
    private RedisCacheTtl() {
    }

    public static final Duration SHORT = Duration.ofMinutes(3);
    public static final Duration MEDIUM = Duration.ofMinutes(6);
    public static final Duration LONG = Duration.ofMinutes(8);
}
