package com.platform.service;

import com.platform.dto.auth.ChangePasswordRequest;
import com.platform.entity.PasswordResetToken;
import com.platform.entity.User;
import com.platform.exception.BadRequestException;
import com.platform.exception.InvalidPasswordResetTokenException;
import com.platform.repository.PasswordResetTokenRepository;
import com.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Everything that changes a password: the authenticated route, and the recovery route for
 * someone who cannot authenticate because they have forgotten it.
 *
 * <p>Both routes end in {@link #applyNewPassword}, so they cannot drift on the parts that
 * matter - clearing a lockout, invalidating outstanding reset links, and ending every
 * session that predates the change.
 *
 * <p>Reset tokens follow the {@link RefreshTokenService} pattern exactly: 256 bits of
 * CSPRNG output, handed out once, stored only as a SHA-256 hex digest, single-use, and
 * short-lived.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {

    private static final int TOKEN_BYTES = 32;   // 256 bits

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetMailer passwordResetMailer;

    /** Short by design: the window is how long a link sits usable in an inbox. */
    @Value("${password-reset.expiration:1800000}")
    private long resetExpirationInMs;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Changes the authenticated user's own password.
     *
     * <p>The current password is required. Without it a stolen access token would be a
     * permanent takeover instead of one that dies with the token.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = userService.getUser();

        // 400, not 401: the caller is already authenticated, and SPAs treat a 401 as
        // "session over" and log the user out - a typo in the current-password field
        // should not do that. Brute-forcing this endpoint is bounded by the per-IP
        // throttle in RateLimitFilter, which covers it alongside the public auth paths.
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            log.warn("Password change rejected: wrong current password for userId={}", user.getId());
            throw new BadRequestException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from the current one");
        }

        applyNewPassword(user, request.getNewPassword());
        log.info("Password changed for userId={}", user.getId());
    }

    /**
     * Issues a reset link for {@code rawEmail}, if that address has an account.
     *
     * <p>Returns normally either way. The caller must respond identically in both cases -
     * an endpoint that says "no account with that email" is a free membership check
     * against the whole user table, which is the same reason login refuses to say whether
     * it was the address or the password that was wrong.
     */
    @Transactional
    public void requestReset(String rawEmail) {
        String email = UserService.normalizeEmail(rawEmail);
        Optional<User> account = userRepository.findByEmailIgnoreCase(email);

        if (account.isEmpty()) {
            // Without the address: a log line naming it would just move the enumeration
            // oracle from the response to the log.
            log.info("Password reset requested for an address with no account");
            return;
        }

        User user = account.get();
        LocalDateTime now = LocalDateTime.now();

        // One live link at a time. Asking again supersedes the previous one rather than
        // leaving a pile of working links behind in the inbox.
        passwordResetTokenRepository.invalidateAllForUser(user.getId(), now);

        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime expiresAt = now.plusNanos(resetExpirationInMs * 1_000_000L);

        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build());

        // The one moment the raw token exists on this side. It is not stored, not logged
        // here, and not returned to the caller.
        passwordResetMailer.sendPasswordReset(user, rawToken, expiresAt);
        log.info("Password reset link issued for userId={}", user.getId());
    }

    /**
     * Spends a reset token and sets the new password.
     *
     * @throws InvalidPasswordResetTokenException if the token is unknown, expired or spent
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Invalid password reset token"));

        // Unknown, expired and already-spent are one case to the caller on purpose.
        if (!stored.isUsable()) {
            throw new InvalidPasswordResetTokenException("Invalid password reset token");
        }

        User user = stored.getUser();

        stored.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(stored);

        applyNewPassword(user, newPassword);
        log.info("Password reset completed for userId={}", user.getId());
    }

    /**
     * The shared tail of both routes.
     *
     * <p>Ordering is load-bearing: the user row is flushed before the two bulk updates,
     * because {@code clearAutomatically} on those would otherwise discard the pending
     * password change along with the rest of the persistence context.
     */
    private void applyNewPassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));

        // A reset is also the way out of a lockout. Someone who has just proven control of
        // the mailbox should not still be serving out a lock that a guesser earned them.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.saveAndFlush(user);

        // Any other unopened link in the inbox stops working now.
        passwordResetTokenRepository.invalidateAllForUser(user.getId(), LocalDateTime.now());

        // The password just changed, so every session that predates it goes with it -
        // that is the whole point of resetting after a compromise. Access tokens already
        // issued survive until they expire; jwt.expiration is what bounds that.
        refreshTokenService.revokeAllForUser(user);
    }

    /**
     * SHA-256, hex-encoded. No salt and no BCrypt on purpose: the input is 256 bits of
     * CSPRNG output, so there is nothing to brute-force, and lookup has to be by exact
     * value on an indexed column.
     *
     * <p>Deliberately identical to {@code RefreshTokenService.hash}. The two are worth
     * folding into one helper once BP-39 has merged - doing it here would edit that
     * branch's files while its PR is still open.
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
