package com.platform.service;

import com.platform.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Drops password reset tokens that are past their expiry.
 *
 * <p>Without this the table only ever grows: every forgot-password request inserts a row,
 * and an expired or spent row can never reset anything again. The grace period is short -
 * unlike a refresh token there is no reuse-detection story here that a kept row would
 * serve, only the ability to tell an expired token from an unknown one, which this flow
 * deliberately does not expose anyway.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetTokenCleanupJob {

    private static final int GRACE_DAYS = 1;

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Scheduled(cron = "0 45 3 * * *")   // 03:45 daily, after the refresh-token sweep
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = passwordResetTokenRepository.deleteExpiredBefore(
                LocalDateTime.now().minusDays(GRACE_DAYS));
        if (deleted > 0) {
            log.info("Deleted {} expired password reset tokens", deleted);
        }
    }
}
