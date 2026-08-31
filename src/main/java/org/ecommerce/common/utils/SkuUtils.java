package org.ecommerce.common.utils;

import java.util.UUID;

public final class SkuUtils {
    private SkuUtils() {
    }

    public static String generateSku(String productName, String variantValue) {
        String productCode = generateCode(productName, 6);
        String variantCode = generateCode(variantValue, 6);
        String randomCode = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 6).toUpperCase();

        return productCode + "-" + variantCode + "-" + randomCode;
    }

    private static String generateCode(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "NA";
        }

        String code = value.trim()
                .toUpperCase().replaceAll("[^A-Z0-9\\s-]", "")
                .replaceAll("\\s+", "-").replaceAll("-+", "-");

        if (code.length() > maxLength) {
            code = code.substring(0, maxLength);
        }

        return code;
    }
}
