package com.platform.integration;

import com.platform.entity.RefreshToken;
import com.platform.entity.User;
import com.platform.repository.RefreshTokenRepository;
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
 * Covers {@link RefreshTokenRepository}'s two bulk {@code @Modifying} queries -
 * logout-everywhere/reuse-detection revocation and the nightly cleanup delete.
 * Both use {@code @Modifying(clearAutomatically = true, flushAutomatically = true)},
 * which is exactly the kind of detail Mockito cannot verify: a JPQL bulk UPDATE/
 * DELETE bypasses the persistence context entirely, so whether already-managed
 * entities in the same test see the change depends on that annotation actually
 * clearing the context - only a real EntityManager against a real database
 * proves it does.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class RefreshTokenRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = entityManager.persistFlushFind(
                TestFixtures.userBuilder("a-" + UUID.randomUUID() + "@example.com").build());
        userB = entityManager.persistFlushFind(
                TestFixtures.userBuilder("b-" + UUID.randomUUID() + "@example.com").build());
    }

    private RefreshToken persistToken(User user, String hash, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hash)
                .issuedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build();
        return entityManager.persistFlushFind(token);
    }

    @Test
    void revokeAllForUser_revokesOnlyThatUsersStillLiveTokensAndReturnsHowMany() {
        RefreshToken liveA1 = persistToken(userA, "hash-a1", LocalDateTime.now().plusDays(30), null);
        RefreshToken liveA2 = persistToken(userA, "hash-a2", LocalDateTime.now().plusDays(30), null);
        RefreshToken alreadyRevokedA = persistToken(
                userA, "hash-a3", LocalDateTime.now().plusDays(30), LocalDateTime.now().minusDays(1));
        RefreshToken liveB = persistToken(userB, "hash-b1", LocalDateTime.now().plusDays(30), null);

        LocalDateTime revokeAt = LocalDateTime.now();
        int revokedCount = refreshTokenRepository.revokeAllForUser(userA.getId(), revokeAt);

        // Only the two still-live tokens belonging to userA - the already-revoked
        // one is excluded by "AND revoked_at IS NULL", userB's is untouched.
        assertEquals(2, revokedCount);

        assertNotNull(refreshTokenRepository.findById(liveA1.getId()).orElseThrow().getRevokedAt());
        assertNotNull(refreshTokenRepository.findById(liveA2.getId()).orElseThrow().getRevokedAt());
        assertNull(refreshTokenRepository.findById(liveB.getId()).orElseThrow().getRevokedAt());

        // The bulk UPDATE bypasses the persistence context; clearAutomatically=true
        // is what makes this re-read hit the database instead of returning the
        // stale in-memory value captured before the update.
        assertNotNull(refreshTokenRepository.findById(alreadyRevokedA.getId()).orElseThrow().getRevokedAt());
    }

    @Test
    void deleteExpiredBefore_removesOnlyRowsThatExpiredBeforeTheCutoff() {
        LocalDateTime cutoff = LocalDateTime.now();
        RefreshToken expired = persistToken(userA, "expired-hash", cutoff.minusMinutes(1), null);
        RefreshToken stillValid = persistToken(userA, "valid-hash", cutoff.plusDays(1), null);

        int deletedCount = refreshTokenRepository.deleteExpiredBefore(cutoff);

        assertEquals(1, deletedCount);
        assertFalse(refreshTokenRepository.findById(expired.getId()).isPresent());
        assertTrue(refreshTokenRepository.findById(stillValid.getId()).isPresent());
    }
}
