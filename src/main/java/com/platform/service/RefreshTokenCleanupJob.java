package com.platform.service;

import com.platform.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Drops refresh tokens that are past their expiry.
 *
 * <p>Without this the table only ever grows: every login and every rotation inserts a
 * row, and a spent or expired row can never authorise anything again. Rows are kept for
 * a grace period past expiry so a reuse arriving just after the deadline is still caught
 * as reuse rather than as an unknown token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private static final int GRACE_DAYS = 7;

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 30 3 * * *")   // 03:30 daily
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(
                LocalDateTime.now().minusDays(GRACE_DAYS));
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }
}
