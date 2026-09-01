package org.ecommerce.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI ecommerceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ecommerce API")
                        .description("REST API for Ecommerce application")
                        .version("1.0.0")
                        .contact(new Contact().name("Ecommerce Development Team"))
                        .license(new License()
                                .name("Copyright © 2026 Ecommerce")))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .name("Authorization")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ))
                ;
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("user-apis")
                .pathsToMatch(
                        "/api/v1/users/**",
                        "/api/v1/auth/**",
                        "/api/v1/products/**",
                        "/api/v1/categories/**",
                        "/api/v1/reviews/**",
                        "/api/v1/cart/**",
                        "/api/v1/coupons/**",
                        "/api/v1/payments/**",
                        "/api/v1/orders/**",
                        "/api/v1/shipment/**",
                        "/api/v1/contact/**",
                        "/api/v1/newsletter/**"
                )
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin-apis")
                .pathsToMatch("/api/v1/admin/**")
                .build();
    }
}
