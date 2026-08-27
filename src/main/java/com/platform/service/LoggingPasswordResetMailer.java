package com.platform.service;

import com.platform.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Writes the reset link to the log instead of emailing it.
 *
 * <p>This is a stub, not a delivery mechanism. There is no mail provider on the classpath
 * yet, and the reset flow was built first so the token half could land and be reviewed on
 * its own. Consequence, stated plainly: <strong>the reset link is readable by anyone who
 * can read the application log</strong>, and {@code com.platform} logs at INFO in prod.
 * Until BP-136 replaces this with a real sender, password reset is a support-desk tool,
 * not a self-service one, and the frontend entry point should stay closed (BP-94).
 */
@Slf4j
@Component
public class LoggingPasswordResetMailer implements PasswordResetMailer {

    private final String frontendBaseUrl;

    public LoggingPasswordResetMailer(@Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void sendPasswordReset(User user, String rawToken, LocalDateTime expiresAt) {
        log.info("[NO MAIL PROVIDER - BP-136] Password reset link for {} (expires {}): {}",
                user.getEmail(), expiresAt, resetLink(rawToken));
    }

    private String resetLink(String rawToken) {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        return base + "/reset-password?token=" + rawToken;
    }
}
