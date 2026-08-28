package org.ecommerce.common.constants;

public class RedisKeyConstants {
    private RedisKeyConstants() {
    }

    public static final String ROOT = "ecommerce:";
    public static final String ADMIN = ROOT + "admin:";
    public static final String DASHBOARD = ADMIN + "dashboard:";

    public static final String RATE_LIMIT = ROOT + "rate-limit:";
    public static final String PRODUCTS = ROOT + "products:";
    public static final String CARTS = ROOT + "carts:";
    public static final String DASHBOARD_SUMMARY = DASHBOARD + "summary";
    public static final String DASHBOARD_USERS = DASHBOARD + "users";
    public static final String DASHBOARD_ORDERS = DASHBOARD + "orders";
    public static final String DASHBOARD_PRODUCTS = DASHBOARD + "products";
}
