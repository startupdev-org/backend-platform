package com.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.platform.config.RateLimitProperties;
import com.platform.exception.ErrorResponse;
import com.platform.utils.ClientIpResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Per-IP throttle on the public authentication endpoints. Without it, {@code /api/auth/login}
 * is an unbounded credential-stuffing and BCrypt CPU-exhaustion target, and
 * {@code /api/auth/register} an unbounded account/email spam target.
 *
 * <p>Each client IP gets a bucket4j token bucket, kept in a size-bounded Caffeine cache that
 * evicts idle IPs. In-memory, so per-instance - sized for a single Render instance.
 *
 * <p>Runs before {@link JwtAuthenticationFilter}. Spring Security's CORS filter runs earlier
 * still, so a rejected request keeps its CORS headers and the browser can read the 429 body.
 * On rejection the {@link ErrorResponse} JSON is written directly here (this is before the
 * dispatcher servlet, so {@code GlobalExceptionHandler} cannot see it) - same approach as
 * {@link RestAuthenticationEntryPoint}.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // Every path here either checks a credential or hands one out. /change-password is
    // included even though it is authenticated: it compares the current password and the
    // per-account lockout does not cover it, so the per-IP throttle is the only bound on
    // guessing it with a stolen access token.
    private static final Set<String> PROTECTED_PATHS =
            Set.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh",
                    "/api/auth/forgot-password", "/api/auth/reset-password",
                    "/api/auth/change-password");

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> buckets;

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(properties.getCacheMaxSize())
                .expireAfterAccess(Duration.ofMinutes(properties.getCacheExpireMinutes()))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled()
                || !HttpMethod.POST.matches(request.getMethod())
                || !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String ip = ClientIpResolver.resolve(request);
        Bucket bucket = buckets.get(ip, k -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        log.warn("Rate limit exceeded for ip={} on {}", ip, request.getRequestURI());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Too many requests. Please try again later.")
                .path(request.getRequestURI())
                .build());
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(properties.getCapacity())
                .refillGreedy(properties.getRefillTokens(),
                        Duration.ofSeconds(properties.getRefillPeriodSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
