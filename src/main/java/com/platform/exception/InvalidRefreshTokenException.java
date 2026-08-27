package com.platform.exception;

/**
 * The presented refresh token is unknown, expired, or already spent. Maps to 401.
 *
 * <p>All three cases carry the same message: distinguishing them would tell a caller
 * holding a stolen token which of the three it is.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
