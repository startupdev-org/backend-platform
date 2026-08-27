package com.platform.exception;

/**
 * Raised when a login is attempted against an account that is temporarily locked after
 * too many consecutive failures. Maps to 429.
 *
 * <p>Deliberately distinct from {@link InvalidCredentialsException} (401): the client needs
 * a different message and a {@code Retry-After} hint. This is a minor enumeration signal
 * (a locked account exists) - accepted, since lockout UX needs the distinct response.
 */
public class AccountLockedException extends RuntimeException {

    private final String email;
    private final String clientIp;
    private final long retryAfterSeconds;

    public AccountLockedException(String message, String email, String clientIp, long retryAfterSeconds) {
        super(message);
        this.email = email;
        this.clientIp = clientIp;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getEmail() {
        return email;
    }

    public String getClientIp() {
        return clientIp;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
