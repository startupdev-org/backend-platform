package com.platform.service;

import com.platform.entity.User;

import java.time.LocalDateTime;

/**
 * Delivers a password reset link to its owner.
 *
 * <p>The seam exists so that picking a mail provider is a new implementation plus a
 * property, not a change to {@link PasswordService} - the same shape as
 * {@code StorageProvider} and its R2 implementation.
 *
 * <p>The only implementation today is {@link LoggingPasswordResetMailer}. Real delivery
 * is BP-136.
 */
public interface PasswordResetMailer {

    /**
     * @param user      the account the link resets
     * @param rawToken  the single-use token, seen here and nowhere else on the server
     * @param expiresAt when the link stops working, so the message can say so
     */
    void sendPasswordReset(User user, String rawToken, LocalDateTime expiresAt);
}
