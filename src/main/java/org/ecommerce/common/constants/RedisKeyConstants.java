package org.ecommerce.common.constants;

public class RedisKeyConstants {
    private RedisKeyConstants() {
    }

    public static final String ROOT = "ecommerce:";
    public static final String RATE_LIMIT = ROOT + "rate-limit:";
    public static final String PRODUCTS = ROOT + "products:";
    public static final String CARTS = ROOT + "carts:";
}
