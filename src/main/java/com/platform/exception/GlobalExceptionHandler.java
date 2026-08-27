package com.platform.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Central HTTP status mapping. Services throw domain exceptions; nothing catches and
 * re-throws them on the way up.
 *
 * <p>Two rules hold throughout:
 * <ul>
 *   <li>Messages for authentication and credential failures are constants, never
 *       {@code ex.getMessage()} - those can carry internals or act as a probe oracle.</li>
 *   <li>4xx logs at WARN, never ERROR. A 401 is not an application error, and it is
 *       attacker-triggerable, so it must not be able to fill the log.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 404 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler({ResourceNotFoundException.class, UserNotFoundException.class,
            ServiceNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // An unmapped URL must be a 404. Without this it falls through to the
    // catch-all below and every stray request (bots, scanners, a disabled
    // /v3/api-docs in prod) becomes a 500 with a full stack trace in the log.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint found for the requested path", request);
    }

    // ── 401 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, WebRequest request) {
        log.warn("Failed login attempt email={} ip={} path={}",
                ex.getEmail(), ex.getClientIp(), path(request));
        return build(HttpStatus.UNAUTHORIZED, "Invalid credentials", request);
    }

    // ── 429 ───────────────────────────────────────────────────────────────────

    // Too many consecutive failed logins for this account. Distinct from the 401 above so
    // the client can show a "locked, try later" message; Retry-After carries the wait.
    // The per-IP request throttle (RateLimitFilter) also returns 429 but writes its own
    // body - it runs before dispatch, out of this advice's reach.
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(
            AccountLockedException ex, WebRequest request) {
        log.warn("Login blocked: account locked email={} ip={} path={}",
                ex.getEmail(), ex.getClientIp(), path(request));
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Account temporarily locked due to repeated failed login attempts. "
                        + "Please try again later.")
                .path(path(request))
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    // Reached when a token is well-formed and signed but its user no longer exists, and
    // for any other authentication failure raised during dispatch. The token is unusable,
    // so this is a 401 - and the message stays generic so it is not a probe oracle.
    // UsernameNotFoundException is an AuthenticationException, so this covers both.
    // Unknown, expired and already-spent all return the same body: telling them apart
    // would help whoever is holding a stolen token work out what they have.
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(
            InvalidRefreshTokenException ex, WebRequest request) {
        log.warn("Refresh token rejected on {}", path(request));
        return build(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailure(
            AuthenticationException ex, WebRequest request) {
        log.warn("Authentication failure at {}: {}", path(request), ex.getClass().getSimpleName());
        return build(HttpStatus.UNAUTHORIZED, "Authentication failed", request);
    }

    // ── 403 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler({BusinessException.class, BusinessOwnershipException.class,
            UserNotEnabledException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(RuntimeException ex, WebRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // Required, not optional. A @PreAuthorize denial throws AuthorizationDeniedException
    // (an AccessDeniedException) from inside dispatch, so the @ExceptionHandler(Exception)
    // catch-all below would otherwise win the race against ExceptionTranslationFilter and
    // turn every method-security denial into a 500.
    //
    // This does not swallow the 401 case: the filter chain ends in .anyRequest().authenticated(),
    // so an anonymous request is rejected before dispatch and hits the AuthenticationEntryPoint.
    // Anything that reaches a controller method is already authenticated, which makes a
    // method-security denial unambiguously a 403.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied at {}", path(request));
        return build(HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource", request);
    }

    // ── 409 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler({BusinessFeatureAlreadyExistsException.class, ConflictException.class,
            EmailAlreadyRegisteredException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Constraint violation at {}: {}", path(request), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT,
                "The request conflicts with existing data", request);
    }

    // ── 400 ───────────────────────────────────────────────────────────────────

    // For domain rules that span more than one field, which Bean Validation cannot
    // express per-field - an open time after a close time, for one.
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message("Invalid request parameters")
                .validationErrors(errors)
                .path(path(request))
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // The @Validated counterpart of the handler above: constraints on request
    // params and path variables raise ConstraintViolationException instead, which
    // would otherwise fall through to the catch-all and be reported as a 500.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            // "addBusinessReply.reply" -> "reply"
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            errors.put(field, violation.getMessage());
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message("Invalid request parameters")
                .validationErrors(errors)
                .path(path(request))
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // A malformed UUID in a path variable is a client error, not a server one.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'", request);
    }

    // Never echo ex.getMessage() here - it carries Jackson internals and class names.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    // ── 405 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, WebRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "Method not supported for this endpoint", request);
    }

    // ── 502 ───────────────────────────────────────────────────────────────────

    // The object store failing is an upstream problem, not an application bug, so it
    // must not land in the catch-all below as a 500 with a stack trace. The message is
    // a constant - the provider's own error text can carry bucket names and tokens.
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageFailure(
            StorageException ex, WebRequest request) {
        log.warn("Storage provider failure at {}: {}", path(request), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "Image storage is temporarily unavailable", request);
    }

    // ── 500 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                WebRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path(request))
                .build();
        return new ResponseEntity<>(errorResponse, status);
    }

    private String path(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
