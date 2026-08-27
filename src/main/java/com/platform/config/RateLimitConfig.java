package com.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.security.RateLimitFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the per-IP auth throttle. The filter is a plain {@code @Bean}, not a
 * {@code @Component}: as an {@code OncePerRequestFilter} bean the latter would also be
 * auto-registered with the servlet container and run twice. {@code SecurityConfig} adds
 * this one instance to the security filter chain.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        return new RateLimitFilter(properties, objectMapper);
    }
}
