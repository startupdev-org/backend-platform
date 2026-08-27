package com.platform.config;

import com.platform.security.JwtAuthenticationFilter;
import com.platform.security.RateLimitFilter;
import com.platform.security.RestAccessDeniedHandler;
import com.platform.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // Enables @PreAuthorize / @PostAuthorize on controllers
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    // ── Role constants ────────────────────────────────────────────────────────
    private static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String ROLE_BUSINESS_ADMIN = "BUSINESS_ADMIN";

    // ── Public endpoints ──────────────────────────────────────────────────────
    // Specific paths, not "/api/auth/**": a new POST under /api/auth is then
    // authenticated by default and made public only by an explicit edit here.
    private static final String[] PUBLIC_POST_PATTERNS   = { "/api/auth/login", "/api/auth/register" };
    private static final String[] PUBLIC_GET_PATTERNS    = {
            "/api/health/**",
            "/swagger-ui/**",
            "/swagger-ui/index.html",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/config"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)          // Safe: stateless JWT, no session
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // ── 1. Fully public ───────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATTERNS).permitAll()
                        .requestMatchers(PUBLIC_GET_PATTERNS).permitAll()

                        // ── 2. Platform admin (most privileged — checked early) ────────
                        .requestMatchers("/api/business/admin/**").hasRole(ROLE_PLATFORM_ADMIN)

                        // ── 2b. Image upload endpoints ────────────────────────────────
                        // Must precede both the employee rules below and the /api/business/**
                        // catch-all in section 7 - first match wins, and the catch-all would
                        // otherwise lock PLATFORM_ADMIN out of these.
                        .requestMatchers(HttpMethod.POST,   "/api/business/*/images/upload-url")           .hasAnyRole(ROLE_BUSINESS_ADMIN, ROLE_PLATFORM_ADMIN)
                        .requestMatchers(HttpMethod.PUT,    "/api/business/*/images")                      .hasAnyRole(ROLE_BUSINESS_ADMIN, ROLE_PLATFORM_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/business/*/images")                      .hasAnyRole(ROLE_BUSINESS_ADMIN, ROLE_PLATFORM_ADMIN)
                        .requestMatchers(HttpMethod.POST,   "/api/business/*/employee/*/images/upload-url").hasAnyRole(ROLE_BUSINESS_ADMIN, ROLE_PLATFORM_ADMIN)
                        .requestMatchers(HttpMethod.PUT,    "/api/business/*/employee/*/images")           .hasAnyRole(ROLE_BUSINESS_ADMIN, ROLE_PLATFORM_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/business/*/employee/*/images")           .hasAnyRole(ROLE_BUSINESS_ADMIN, ROLE_PLATFORM_ADMIN)

                        // ── 3. Employee endpoints ─────────────────────────────────────
                        // Write operations → BUSINESS_ADMIN only
                        .requestMatchers(HttpMethod.POST,   "/api/business/*/employee")      .hasRole(ROLE_BUSINESS_ADMIN)
                        .requestMatchers(HttpMethod.PUT,    "/api/business/*/employee/**")   .hasAnyRole(ROLE_BUSINESS_ADMIN, ROLE_PLATFORM_ADMIN)
                        // Platform-admin-only: permanent delete and disabled-employee lookup
                        // (must precede the general DELETE/GET employee rules below — first match wins)
                        .requestMatchers(HttpMethod.DELETE, "/api/business/*/employee/*/permanent") .hasRole(ROLE_PLATFORM_ADMIN)
                        .requestMatchers(HttpMethod.GET,    "/api/business/*/employee/*/admin")     .hasRole(ROLE_PLATFORM_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/business/*/employee/**")   .hasRole(ROLE_BUSINESS_ADMIN)
                        // Read operations → any authenticated user
                        .requestMatchers(HttpMethod.GET,    "/api/business/*/employee/**")   .permitAll()
                        .requestMatchers(HttpMethod.GET,    "/api/business/*/employee")      .permitAll()

                        // ── 4. Working-hours endpoints ────────────────────────────────
                        .requestMatchers(HttpMethod.GET,    "/api/business/*/working-hours")    .authenticated()
                        .requestMatchers(HttpMethod.POST,   "/api/business/*/working-hours")    .hasRole(ROLE_BUSINESS_ADMIN)
                        .requestMatchers(HttpMethod.PUT,    "/api/business/*/working-hours/**") .hasRole(ROLE_BUSINESS_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/business/*/working-hours/**") .hasRole(ROLE_BUSINESS_ADMIN)

                        // ── 5. Features endpoints ─────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,    "/api/business/*/features")      .authenticated()
                        .requestMatchers(HttpMethod.POST,   "/api/business/*/features")      .hasRole(ROLE_BUSINESS_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/business/*/features/**")   .hasRole(ROLE_BUSINESS_ADMIN)

                        // ── 6. Service endpoints ──────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,    "/api/business/*/service")       .authenticated()

                        // ── 6b. Location endpoints ────────────────────────────────────
                        .requestMatchers(HttpMethod.POST,   "/api/business/*/location")      .hasRole(ROLE_BUSINESS_ADMIN)
                        .requestMatchers(HttpMethod.PUT,    "/api/business/*/location/**")   .hasRole(ROLE_BUSINESS_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/business/*/location/**")   .hasRole(ROLE_BUSINESS_ADMIN)
                        .requestMatchers(HttpMethod.GET,    "/api/business/*/location/**")   .permitAll()
                        .requestMatchers(HttpMethod.GET,    "/api/business/*/location")      .permitAll()

                        // ── 6c. Employee/location/service pricing endpoints ───────────
                        .requestMatchers(HttpMethod.POST,   "/api/business/*/employee-service-price")     .hasRole(ROLE_BUSINESS_ADMIN)
                        .requestMatchers(HttpMethod.PUT,    "/api/business/*/employee-service-price/**")  .hasRole(ROLE_BUSINESS_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/business/*/employee-service-price/**")  .hasRole(ROLE_BUSINESS_ADMIN)
                        .requestMatchers(HttpMethod.GET,    "/api/business/*/employee-service-price/**")  .authenticated()

                        // ── 7. General business CRUD (catch-all for /api/business/**) ─
                        .requestMatchers(HttpMethod.GET,    "/api/business/**")              .permitAll()
                        .requestMatchers(HttpMethod.POST,   "/api/business/**")              .hasRole(ROLE_BUSINESS_ADMIN)
                        .requestMatchers(HttpMethod.PUT,    "/api/business/**")              .hasRole(ROLE_BUSINESS_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/business/**")              .hasRole(ROLE_BUSINESS_ADMIN)

                        // ── 8. User endpoints ─────────────────────────────────────────
                        // Self-service lives under /me and /whoami; everything else here is
                        // admin-only. The trailing default-deny matters: without it a new
                        // endpoint added to UserController without @PreAuthorize would fall
                        // through to .anyRequest().authenticated() and be open to every
                        // logged-in user. First match wins, so ordering is load-bearing.
                        .requestMatchers("/api/users/whoami")                                .authenticated()
                        .requestMatchers("/api/users/me", "/api/users/me/**")                .authenticated()
                        .requestMatchers("/api/users/**")            .hasRole(ROLE_PLATFORM_ADMIN)


                        // ── 9. Booking & Review endpoints ─────────────────────────────
                        .requestMatchers(HttpMethod.POST,   "/api/booking")                  .authenticated()
                        .requestMatchers(HttpMethod.GET,    "/api/booking/**")               .authenticated()
                        .requestMatchers(HttpMethod.POST,   "/api/review/booking/**")        .authenticated()
                        .requestMatchers(HttpMethod.GET,    "/api/review/business/**")       .authenticated()

                        // ── 10. Deny everything else ──────────────────────────────────
                        .anyRequest().authenticated()
                )
                // Without these Spring falls back to Http403ForbiddenEntryPoint, which is why
                // an expired token and a genuine role denial used to be the same bodyless 403.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Throttle the public auth endpoints before any auth work happens. CORS runs
                // earlier in the chain, so a 429 still carries CORS headers.
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}