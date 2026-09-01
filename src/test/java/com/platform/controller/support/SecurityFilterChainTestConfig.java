package com.platform.controller.support;

import com.platform.config.RateLimitConfig;
import com.platform.config.SecurityConfig;
import com.platform.security.JwtAuthenticationFilter;
import com.platform.security.RestAccessDeniedHandler;
import com.platform.security.RestAuthenticationEntryPoint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Wires the real security filter chain into a {@code @WebMvcTest} slice (BP-64).
 *
 * <p>{@code @WebMvcTest} does not component-scan ordinary {@code @Configuration} /
 * {@code @Component} classes - it only auto-detects controllers, {@code @ControllerAdvice},
 * converters and the like. Left alone, {@code SecurityConfig} (and the four collaborators its
 * constructor requires: {@link JwtAuthenticationFilter}, {@code RateLimitFilter},
 * {@link RestAuthenticationEntryPoint}, {@link RestAccessDeniedHandler}) would simply never be
 * found, Spring Boot would fall back to its own permit-none-by-default security auto-config,
 * and every 401/403 assertion in this suite would be testing nothing.
 *
 * <p>The trap this sidesteps: {@code @MockBean}-ing {@code JwtAuthenticationFilter} or
 * {@code RateLimitFilter} directly. A mocked {@code Filter} does not call
 * {@code chain.doFilter(...)}, so the request dies silently in the filter and every request
 * comes back as an empty 200 - not a security failure, a broken harness. Importing the real
 * filter classes here means the actual production filter-chain logic runs; only their
 * collaborators that would otherwise require real infrastructure are mocked out at the test
 * class level (see {@code JwtUtils} below).
 *
 * <p>{@code JwtUtils} is deliberately NOT imported here - every test in this suite
 * authenticates via {@code SecurityMockMvcRequestPostProcessors}, never with a real
 * {@code Authorization} header, so {@link JwtAuthenticationFilter} never calls into it. Each
 * test class supplies it as a plain {@code @MockBean}, which avoids needing a real
 * {@code jwt.secret} property (BP-61 owns {@code src/test/resources}, so this suite does not
 * add one).
 */
@TestConfiguration
@Import({
        SecurityConfig.class,
        RateLimitConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
public class SecurityFilterChainTestConfig {
}
