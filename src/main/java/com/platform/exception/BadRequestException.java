package com.platform.exception;

/**
 * The request is well-formed but violates a domain rule that spans more than one field.
 * Maps to 400.
 *
 * <p>Distinct from {@code MethodArgumentNotValidException}, which Bean Validation raises
 * per-field and which carries a field map; this one carries a single message.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
