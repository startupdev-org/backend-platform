package com.platform.exception;

/**
 * Registration was attempted with an email that already has an account. Maps to 409.
 *
 * <p>Narrower than {@link BusinessException} on purpose: that one maps to 403, which is
 * the wrong answer for a duplicate.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
