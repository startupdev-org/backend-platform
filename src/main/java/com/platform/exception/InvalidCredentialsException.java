package com.platform.exception;

/**
 * Login failed. Maps to 401.
 *
 * <p>Both failure modes - unknown email and wrong password - must throw this with the same
 * message. Distinguishing them turns the login endpoint into a user-enumeration oracle.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
