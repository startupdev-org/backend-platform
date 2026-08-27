package com.platform.service;

import com.platform.entity.RefreshToken;
import com.platform.entity.User;
import com.platform.exception.InvalidRefreshTokenException;
import com.platform.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issue, rotate and revoke refresh tokens.
 *
 * <p>The access token stays short-lived and unrevocable - checking a denylist on every
 * request would put a database round-trip in front of the whole API. Revocation lives on
 * this side instead: killing the refresh token stops re-issuance, so access ends within
 * one access-token lifetime rather than the 24 hours it used to take.
 *
 * <p>Tokens are single-use. Refreshing revokes the presented token and returns a new one.
 * A token presented after it was already spent means the value leaked - both the client
 * and someone else hold it - so the entire family for that user is revoked and everyone
 * has to log in again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;   // 256 bits

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration:2592000000}")
    private long refreshExpirationInMs;

    private final SecureRandom secureRandom = new SecureRandom();

    /** The raw token. It is returned to the caller once and never stored. */
    public record IssuedToken(String rawToken, LocalDateTime expiresAt) {}

    @Transactional
    public IssuedToken issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusNanos(refreshExpirationInMs * 1_000_000L);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build());

        return new IssuedToken(rawToken, expiresAt);
    }

    /**
     * Spends {@code rawToken} and issues its successor.
     *
     * @return the stored token that was spent, so the caller can read the user off it
     * @throws InvalidRefreshTokenException if the token is unknown, expired, or already spent
     */
    @Transactional
    public RefreshToken verifyAndConsume(String rawToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        if (stored.isRevoked()) {
            // Reuse of a spent token: the value is in more than one pair of hands.
            log.warn("Refresh token reuse detected for userId={}; revoking all sessions",
                    stored.getUser().getId());
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId(), LocalDateTime.now());
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }

        if (stored.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);
        return stored;
    }

    /** Records which token replaced a spent one, so a reuse can be traced. */
    @Transactional
    public void linkSuccessor(RefreshToken spent, String successorRawToken) {
        spent.setReplacedBy(hash(successorRawToken));
        refreshTokenRepository.save(spent);
    }

    /** Logout. Unknown or already-revoked tokens are a no-op - logout is idempotent. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(RefreshToken::isUsable)
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.revokeAllForUser(user.getId(), LocalDateTime.now());
    }

    /**
     * SHA-256, hex-encoded. No salt and no BCrypt on purpose: the input is 256 bits of
     * CSPRNG output, so there is nothing to brute-force, and lookup has to be by exact
     * value on an indexed column.
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
