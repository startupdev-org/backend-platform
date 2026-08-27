package com.platform.exception;

/**
 * Login failed. Maps to 401.
 *
 * <p>Both failure modes - unknown email and wrong password - must throw this with the same
 * message. Distinguishing them turns the login endpoint into a user-enumeration oracle.
 *
 * <p>The attempted email and source IP are carried here so {@code GlobalExceptionHandler}
 * can log a useful failed-login line without changing the client-facing message or status.
 * They may be {@code null} when the exception is thrown outside the login flow.
 */
public class InvalidCredentialsException extends RuntimeException {

    private final String email;
    private final String clientIp;

    public InvalidCredentialsException(String message) {
        this(message, null, null);
    }

    public InvalidCredentialsException(String message, String email, String clientIp) {
        super(message);
        this.email = email;
        this.clientIp = clientIp;
    }

    public String getEmail() {
        return email;
    }

    public String getClientIp() {
        return clientIp;
    }
}
