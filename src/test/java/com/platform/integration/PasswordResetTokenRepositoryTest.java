package com.platform.integration;

import com.platform.entity.PasswordResetToken;
import com.platform.entity.User;
import com.platform.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * Covers {@link PasswordResetTokenRepository}'s two bulk {@code @Modifying}
 * queries. {@code invalidateAllForUser} is the guard that stops an unopened
 * reset link in an old inbox from working once a newer reset is requested or
 * the password changes through the authenticated route (see CLAUDE.md's
 * "Passwords" section) - a wrong {@code WHERE} clause here would silently leave
 * old links live, so it is worth pinning against a real database rather than
 * trusting the JPQL by inspection.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class PasswordResetTokenRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = entityManager.persistFlushFind(
                TestFixtures.userBuilder("a-" + UUID.randomUUID() + "@example.com").build());
        userB = entityManager.persistFlushFind(
                TestFixtures.userBuilder("b-" + UUID.randomUUID() + "@example.com").build());
    }

    private PasswordResetToken persistToken(User user, String hash, LocalDateTime expiresAt, LocalDateTime usedAt) {
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash(hash)
                .issuedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .usedAt(usedAt)
                .build();
        return entityManager.persistFlushFind(token);
    }

    @Test
    void invalidateAllForUser_marksOnlyThatUsersUnusedTokensAsUsed() {
        LocalDateTime originalUsedAt = LocalDateTime.of(2020, 1, 1, 0, 0);
        PasswordResetToken liveA1 = persistToken(userA, "hash-a1", LocalDateTime.now().plusMinutes(30), null);
        PasswordResetToken liveA2 = persistToken(userA, "hash-a2", LocalDateTime.now().plusMinutes(30), null);
        PasswordResetToken alreadyUsedA = persistToken(
                userA, "hash-a3", LocalDateTime.now().plusMinutes(30), originalUsedAt);
        PasswordResetToken liveB = persistToken(userB, "hash-b1", LocalDateTime.now().plusMinutes(30), null);

        LocalDateTime invalidateAt = LocalDateTime.now();
        int invalidatedCount = passwordResetTokenRepository.invalidateAllForUser(userA.getId(), invalidateAt);

        // Only userA's two still-unused tokens - "AND used_at IS NULL" excludes the
        // one already spent, and the WHERE on user_id excludes userB's entirely.
        assertEquals(2, invalidatedCount);

        assertNotNull(passwordResetTokenRepository.findById(liveA1.getId()).orElseThrow().getUsedAt());
        assertNotNull(passwordResetTokenRepository.findById(liveA2.getId()).orElseThrow().getUsedAt());
        assertNull(passwordResetTokenRepository.findById(liveB.getId()).orElseThrow().getUsedAt());

        // clearAutomatically=true on the bulk update matters here: without it this
        // re-read could return the stale pre-update entity from the persistence
        // context instead of hitting the database. The already-used row's original
        // used_at must be untouched, not overwritten with invalidateAt.
        assertEquals(originalUsedAt,
                passwordResetTokenRepository.findById(alreadyUsedA.getId()).orElseThrow().getUsedAt());
    }

    @Test
    void deleteExpiredBefore_removesOnlyRowsThatExpiredBeforeTheCutoff() {
        LocalDateTime cutoff = LocalDateTime.now();
        PasswordResetToken expired = persistToken(userA, "expired-hash", cutoff.minusMinutes(1), null);
        PasswordResetToken stillValid = persistToken(userA, "valid-hash", cutoff.plusDays(1), null);

        int deletedCount = passwordResetTokenRepository.deleteExpiredBefore(cutoff);

        assertEquals(1, deletedCount);
        assertFalse(passwordResetTokenRepository.findById(expired.getId()).isPresent());
        assertTrue(passwordResetTokenRepository.findById(stillValid.getId()).isPresent());
    }
}
