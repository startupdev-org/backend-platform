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
    // This is the single, deliberate statement of what an anonymous visitor may
    // reach. Everything not listed here (or marked permitAll in a matcher below)
    // falls through to .anyRequest().authenticated().
    //
    // Specific paths, not "/api/auth/**": a new POST under /api/auth is then
    // authenticated by default and made public only by an explicit edit here.
    // /refresh and /logout are public because the refresh token *is* the credential:
    // the caller's access token has usually already expired by the time they get here.
    // /forgot-password and /reset-password are public because the caller is by definition
    // someone who cannot log in. /change-password is deliberately absent: it needs the
    // session, so it falls through to .anyRequest().authenticated() below.
    // /api/booking is public because customers book without an account (BP-46); it is
    // throttled per IP by RateLimitFilter, like the other public POSTs.
    private static final String[] PUBLIC_POST_PATTERNS   = {
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout",
            "/api/auth/forgot-password", "/api/auth/reset-password",
            "/api/booking" };
    private static final String[] PUBLIC_GET_PATTERNS    = {
            "/api/health/**",
            "/swagger-ui/**",
            "/swagger-ui/index.html",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/config"
    };

    // The public booking page reads a business and its sub-resources with no login.
    // These GETs are permitAll in the matchers below; they are gathered here so the
    // anonymous read surface is visible in one place:
    //   GET /api/business, /api/business/{id}, /api/business/slug/{slug}  (section 7)
    //   GET /api/business/{id}/employee, /employee/** (incl. availability) (section 3)
    //   GET /api/business/{id}/location, /location/**                     (section 6b)
    //   GET /api/business/{id}/service, /service/**                       (section 6)

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)          // Safe: stateless JWT, no session
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // ── 1. Fully public ───────────────────────────────────────────
                        // Render's health checker (and any external uptime monitor) hits this
                        // with no token, so it must be reachable pre-auth. Deliberately NOT
                        // folded into PUBLIC_GET_PATTERNS below: that array is applied with no
                        // HttpMethod restriction, so anything added to it is permitAll() for
                        // every method, not just GET (BP-66). Actuator only exposes "health"
                        // (application.yml, management.endpoints.web.exposure.include), so this
                        // is also the entire public /actuator surface - everything else under
                        // /actuator falls through to .anyRequest().authenticated() below.
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
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
                        // Public, like locations and employees: the booking page lists a
                        // business's services with no login (BP-46). /service/{id} and
                        // /service/active were already public via the section 7 catch-all.
                        .requestMatchers(HttpMethod.GET,    "/api/business/*/service")       .permitAll()

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

                        // ── 7b. Analytics endpoints ───────────────────────────────────
                        // Explicit matcher, not the .anyRequest() fallback: the dashboard is
                        // owner-only data, so the role gate is stated here and the per-business
                        // ownership check lives in AnalyticsService.
                        .requestMatchers(HttpMethod.GET, "/api/analytics/**")
                                .hasAnyRole(ROLE_BUSINESS_ADMIN, ROLE_PLATFORM_ADMIN)
                        .requestMatchers("/api/analytics/**").denyAll()

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
                        // POST /api/booking is public (customers book without an account) -
                        // it is in PUBLIC_POST_PATTERNS above, matched before this section.
                        // Every other booking route is management data: reads and mutations
                        // are role-gated here and ownership-scoped in BookingService (BP-29).
                        // PATCH and DELETE are stated explicitly rather than left to the
                        // .anyRequest() fallback, which would let any authenticated account
                        // re-status or cancel anyone's booking.
                        .requestMatchers(HttpMethod.PATCH,  "/api/booking/*/status")         .hasAnyRole(ROLE_BUSINESS_ADMIN, ROLE_PLATFORM_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/booking/*")                .hasAnyRole(ROLE_BUSINESS_ADMIN, ROLE_PLATFORM_ADMIN)
                        .requestMatchers(HttpMethod.GET,    "/api/booking/**")               .hasAnyRole(ROLE_BUSINESS_ADMIN, ROLE_PLATFORM_ADMIN)
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