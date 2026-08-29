package org.ecommerce.common.constants;

import java.time.Duration;

public final class RedisCacheTtl {
    private RedisCacheTtl() {
    }

    public static final Duration SHORT = Duration.ofMinutes(5);
    public static final Duration MEDIUM = Duration.ofMinutes(10);
    public static final Duration LONG = Duration.ofMinutes(15);
}
