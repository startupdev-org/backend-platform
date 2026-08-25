package com.platform.exception;

/**
 * The request is valid but conflicts with the current state of the resource.
 * Maps to 409.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
