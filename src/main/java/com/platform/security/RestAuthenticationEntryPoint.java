package com.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Renders a 401 with an {@link ErrorResponse} body for unauthenticated requests.
 *
 * <p>Without an entry point Spring Security falls back to {@code Http403ForbiddenEntryPoint},
 * which is why an expired token and a genuine role denial used to be the same bodyless 403 -
 * leaving the frontend no way to tell "log in again" from "you may not do this".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** Set by {@link JwtAuthenticationFilter} so this class can distinguish the failure modes. */
    public static final String JWT_ERROR_ATTRIBUTE = "com.platform.jwtError";
    public static final String JWT_ERROR_EXPIRED = "expired";
    public static final String JWT_ERROR_INVALID = "invalid";

    // Boot's configured bean, which carries JavaTimeModule. A raw `new ObjectMapper()` cannot
    // serialize ErrorResponse.timestamp (a LocalDateTime) and would throw in here, producing
    // the bare container 500 this class exists to remove.
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        Object jwtError = request.getAttribute(JWT_ERROR_ATTRIBUTE);
        String message;
        if (JWT_ERROR_EXPIRED.equals(jwtError)) {
            message = "Authentication token has expired";
        } else if (JWT_ERROR_INVALID.equals(jwtError)) {
            message = "Authentication token is invalid";
        } else {
            message = "Authentication required";
        }

        log.warn("Unauthenticated request to {}: {}", request.getRequestURI(), message);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build());
    }
}
