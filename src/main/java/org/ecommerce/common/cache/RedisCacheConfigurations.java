package org.ecommerce.common.cache;

import org.ecommerce.common.constants.RedisCacheTtl;
import org.ecommerce.common.constants.RedisKeyConstants;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.util.Map;

public final class RedisCacheConfigurations {
    private RedisCacheConfigurations() {
    }

    public static Map<String, RedisCacheConfiguration> getConfigurations(RedisCacheConfiguration defaultConfig) {
        return Map.of(
                RedisKeyConstants.DASHBOARD_SUMMARY, defaultConfig.entryTtl(RedisCacheTtl.SHORT),
                RedisKeyConstants.DASHBOARD_ORDERS, defaultConfig.entryTtl(RedisCacheTtl.SHORT),
                RedisKeyConstants.DASHBOARD_USERS, defaultConfig.entryTtl(RedisCacheTtl.SHORT),
                RedisKeyConstants.DASHBOARD_PRODUCTS, defaultConfig.entryTtl(RedisCacheTtl.LONG)
        );
    }
}
