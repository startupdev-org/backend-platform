package com.platform.exception;

/**
 * The presented password reset token is unknown, expired, or already used. Maps to 400.
 *
 * <p>All three cases carry the same message: distinguishing them would tell a caller
 * holding a stale or guessed link which of the three it is.
 */
public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException(String message) {
        super(message);
    }
}
