package com.platform.service;

import com.platform.entity.RefreshToken;
import com.platform.entity.User;
import com.platform.exception.InvalidRefreshTokenException;
import com.platform.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setLifetime() {
        // @Value is not applied without a Spring context.
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationInMs", THIRTY_DAYS_MS);
    }

    // ==================== issue ====================

    // A dump of refresh_tokens must not hand anyone a working credential.
    @Test
    void issue_storesOnlyTheHash() {
        User user = user();

        RefreshTokenService.IssuedToken issued = refreshTokenService.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertNotEquals(issued.rawToken(), saved.getTokenHash());
        assertEquals(sha256Hex(issued.rawToken()), saved.getTokenHash());
        assertEquals(user, saved.getUser());
        assertNull(saved.getRevokedAt());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now().plusDays(29)));
    }

    @Test
    void issue_producesADifferentTokenEveryTime() {
        User user = user();

        assertNotEquals(
                refreshTokenService.issue(user).rawToken(),
                refreshTokenService.issue(user).rawToken());
    }

    // ==================== verifyAndConsume ====================

    @Test
    void verifyAndConsume_spendsTheToken() {
        RefreshToken stored = storedToken(user(), LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(sha256Hex("raw"))).thenReturn(Optional.of(stored));

        RefreshToken consumed = refreshTokenService.verifyAndConsume("raw");

        assertNotNull(consumed.getRevokedAt(), "a spent token must be revoked");
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void verifyAndConsume_unknownToken_rejected() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.verifyAndConsume("raw"));
    }

    @Test
    void verifyAndConsume_expiredToken_rejected() {
        RefreshToken stored = storedToken(user(), LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByTokenHash(sha256Hex("raw"))).thenReturn(Optional.of(stored));

        assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.verifyAndConsume("raw"));

        verify(refreshTokenRepository, never()).save(any());
    }

    // Replay means the value is in two pairs of hands and there is no way to tell which
    // one is the legitimate client, so every session for that user goes.
    @Test
    void verifyAndConsume_replayOfSpentToken_revokesEverySession() {
        User user = user();
        RefreshToken stored = storedToken(user, LocalDateTime.now().plusDays(1));
        stored.setRevokedAt(LocalDateTime.now().minusMinutes(5));

        when(refreshTokenRepository.findByTokenHash(sha256Hex("raw"))).thenReturn(Optional.of(stored));

        assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.verifyAndConsume("raw"));

        verify(refreshTokenRepository).revokeAllForUser(eq(user.getId()), any(LocalDateTime.class));
    }

    // ==================== revoke ====================

    @Test
    void revoke_marksTheTokenRevoked() {
        RefreshToken stored = storedToken(user(), LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(sha256Hex("raw"))).thenReturn(Optional.of(stored));

        refreshTokenService.revoke("raw");

        assertNotNull(stored.getRevokedAt());
        verify(refreshTokenRepository).save(stored);
    }

    // Logging out twice, or with a token the server has never seen, is not an error.
    @Test
    void revoke_unknownToken_isANoOp() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> refreshTokenService.revoke("raw"));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revoke_alreadyRevokedToken_isANoOp() {
        RefreshToken stored = storedToken(user(), LocalDateTime.now().plusDays(1));
        stored.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByTokenHash(sha256Hex("raw"))).thenReturn(Optional.of(stored));

        refreshTokenService.revoke("raw");

        verify(refreshTokenRepository, never()).save(any());
    }

    // ==================== helpers ====================

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(User.UserRole.BUSINESS_ADMIN);
        return user;
    }

    private RefreshToken storedToken(User user, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(sha256Hex("raw"))
                .issuedAt(LocalDateTime.now().minusHours(1))
                .expiresAt(expiresAt)
                .build();
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
