package org.ecommerce.common.config.security;

import lombok.RequiredArgsConstructor;
import org.ecommerce.auth.security.JwtAuthenticationFilter;
import org.ecommerce.common.config.rate.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableMethodSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(requestMatcherRegistry ->
                        requestMatcherRegistry.requestMatchers(
                                        SecurityConstants.AUTH_REGISTER,
                                        SecurityConstants.AUTH_LOGIN,
                                        SecurityConstants.AUTH_REFRESH_TOKEN,
                                        SecurityConstants.AUTH_VERIFY_EMAIL,
                                        SecurityConstants.AUTH_FORGET_PASSWORD,
                                        SecurityConstants.AUTH_RESEND_VERIFICATION,
                                        SecurityConstants.AUTH_RESET_PASSWORD,
                                        SecurityConstants.CATEGORY_LISTING,
                                        SecurityConstants.PRODUCT_LISTING,
                                        SecurityConstants.RAZORPAY_WEBHOOK,
                                        SecurityConstants.SWAGGER_UI,
                                        SecurityConstants.SWAGGER_UI_RESOURCES,
                                        SecurityConstants.OPEN_API_DOCS
                                ).permitAll()
                                .requestMatchers(SecurityConstants.ACTUATOR_PROMETHEUS)
                                .hasRole("MONITORING")
                                .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
