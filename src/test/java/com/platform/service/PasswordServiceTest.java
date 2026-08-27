package com.platform.service;

import com.platform.dto.auth.ChangePasswordRequest;
import com.platform.entity.PasswordResetToken;
import com.platform.entity.User;
import com.platform.exception.BadRequestException;
import com.platform.exception.InvalidPasswordResetTokenException;
import com.platform.repository.PasswordResetTokenRepository;
import com.platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private static final long THIRTY_MINUTES_MS = 30L * 60 * 1000;
    private static final String CURRENT_RAW = "currentPassword";
    private static final String CURRENT_HASH = "encodedCurrentPassword";
    private static final String NEW_RAW = "brandNewPassword";
    private static final String NEW_HASH = "encodedNewPassword";

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordResetMailer passwordResetMailer;

    @InjectMocks
    private PasswordService passwordService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<PasswordResetToken> tokenCaptor;

    @BeforeEach
    void setLifetime() {
        // @Value is not applied without a Spring context.
        ReflectionTestUtils.setField(passwordService, "resetExpirationInMs", THIRTY_MINUTES_MS);
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_replacesTheHashAndEndsEverySession() {
        User user = user();
        when(userService.getUser()).thenReturn(user);
        when(passwordEncoder.matches(CURRENT_RAW, CURRENT_HASH)).thenReturn(true);
        when(passwordEncoder.matches(NEW_RAW, CURRENT_HASH)).thenReturn(false);
        when(passwordEncoder.encode(NEW_RAW)).thenReturn(NEW_HASH);

        passwordService.changePassword(new ChangePasswordRequest(CURRENT_RAW, NEW_RAW));

        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals(NEW_HASH, userCaptor.getValue().getPassword());
        assertNotEquals(NEW_RAW, userCaptor.getValue().getPassword(), "the raw password is never stored");

        // A password change that left old sessions alive would not lock anyone out.
        verify(refreshTokenService).revokeAllForUser(user);
    }

    @Test
    void changePassword_invalidatesOutstandingResetLinks() {
        User user = user();
        when(userService.getUser()).thenReturn(user);
        when(passwordEncoder.matches(CURRENT_RAW, CURRENT_HASH)).thenReturn(true);
        when(passwordEncoder.matches(NEW_RAW, CURRENT_HASH)).thenReturn(false);
        when(passwordEncoder.encode(NEW_RAW)).thenReturn(NEW_HASH);

        passwordService.changePassword(new ChangePasswordRequest(CURRENT_RAW, NEW_RAW));

        // An unopened link sitting in the inbox must not survive the change.
        verify(passwordResetTokenRepository)
                .invalidateAllForUser(eq(user.getId()), any(LocalDateTime.class));
    }

    @Test
    void changePassword_wrongCurrentPassword_isRejectedAndChangesNothing() {
        User user = user();
        when(userService.getUser()).thenReturn(user);
        when(passwordEncoder.matches("wrongPassword", CURRENT_HASH)).thenReturn(false);

        assertThrows(BadRequestException.class, () ->
                passwordService.changePassword(new ChangePasswordRequest("wrongPassword", NEW_RAW)));

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePassword_newPasswordSameAsCurrent_isRejected() {
        User user = user();
        when(userService.getUser()).thenReturn(user);
        when(passwordEncoder.matches(CURRENT_RAW, CURRENT_HASH)).thenReturn(true);

        assertThrows(BadRequestException.class, () ->
                passwordService.changePassword(new ChangePasswordRequest(CURRENT_RAW, CURRENT_RAW)));

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    // ── requestReset ──────────────────────────────────────────────────────────

    // A dump of password_reset_tokens must not hand anyone a working link.
    @Test
    void requestReset_storesOnlyTheHash() {
        User user = user();
        when(userRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user));

        passwordService.requestReset("ana@example.com");

        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();

        ArgumentCaptor<String> rawToken = ArgumentCaptor.forClass(String.class);
        verify(passwordResetMailer)
                .sendPasswordReset(eq(user), rawToken.capture(), any(LocalDateTime.class));

        assertNotEquals(rawToken.getValue(), saved.getTokenHash());
        assertEquals(sha256Hex(rawToken.getValue()), saved.getTokenHash());
        assertEquals(user, saved.getUser());
        assertNull(saved.getUsedAt());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(29)));
        assertTrue(saved.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(31)),
                "the link must be short-lived");
    }

    // Answering differently for a missing address would make this a free membership
    // check against the whole user table.
    @Test
    void requestReset_unknownEmail_isSilentAndWritesNothing() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> passwordService.requestReset("nobody@example.com"));

        verify(passwordResetTokenRepository, never()).save(any());
        verifyNoInteractions(passwordResetMailer);
    }

    @Test
    void requestReset_supersedesEarlierLinks() {
        User user = user();
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));

        passwordService.requestReset("ana@example.com");

        verify(passwordResetTokenRepository)
                .invalidateAllForUser(eq(user.getId()), any(LocalDateTime.class));
    }

    @Test
    void requestReset_normalizesEmail() {
        when(userRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.empty());

        passwordService.requestReset("  Ana@Example.COM  ");

        verify(userRepository).findByEmailIgnoreCase("ana@example.com");
    }

    @Test
    void requestReset_producesADifferentTokenEveryTime() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user()));

        passwordService.requestReset("ana@example.com");
        passwordService.requestReset("ana@example.com");

        ArgumentCaptor<String> rawTokens = ArgumentCaptor.forClass(String.class);
        verify(passwordResetMailer, times(2))
                .sendPasswordReset(any(User.class), rawTokens.capture(), any(LocalDateTime.class));

        assertNotEquals(rawTokens.getAllValues().get(0), rawTokens.getAllValues().get(1));
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_spendsTheTokenSetsThePasswordAndEndsEverySession() {
        User user = user();
        PasswordResetToken stored = storedToken(user, LocalDateTime.now().plusMinutes(10));
        when(passwordResetTokenRepository.findByTokenHash(sha256Hex("raw")))
                .thenReturn(Optional.of(stored));
        when(passwordEncoder.encode(NEW_RAW)).thenReturn(NEW_HASH);

        passwordService.resetPassword("raw", NEW_RAW);

        assertNotNull(stored.getUsedAt(), "a spent token must be marked used");
        verify(passwordResetTokenRepository).save(stored);

        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals(NEW_HASH, userCaptor.getValue().getPassword());

        verify(refreshTokenService).revokeAllForUser(user);
    }

    // Someone who has just proven control of the mailbox should not still be serving out
    // a lock that a failed guesser earned them.
    @Test
    void resetPassword_clearsAnyLoginLockout() {
        User user = user();
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(LocalDateTime.now().plusHours(1));

        PasswordResetToken stored = storedToken(user, LocalDateTime.now().plusMinutes(10));
        when(passwordResetTokenRepository.findByTokenHash(sha256Hex("raw")))
                .thenReturn(Optional.of(stored));
        when(passwordEncoder.encode(NEW_RAW)).thenReturn(NEW_HASH);

        passwordService.resetPassword("raw", NEW_RAW);

        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals(0, userCaptor.getValue().getFailedLoginAttempts());
        assertNull(userCaptor.getValue().getLockedUntil());
    }

    @Test
    void resetPassword_unknownToken_rejected() {
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(InvalidPasswordResetTokenException.class,
                () -> passwordService.resetPassword("raw", NEW_RAW));

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void resetPassword_expiredToken_rejected() {
        PasswordResetToken stored = storedToken(user(), LocalDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByTokenHash(sha256Hex("raw")))
                .thenReturn(Optional.of(stored));

        assertThrows(InvalidPasswordResetTokenException.class,
                () -> passwordService.resetPassword("raw", NEW_RAW));

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(refreshTokenService);
    }

    // Single-use: the link in the inbox stops working the moment it is spent.
    @Test
    void resetPassword_alreadyUsedToken_rejected() {
        PasswordResetToken stored = storedToken(user(), LocalDateTime.now().plusMinutes(10));
        stored.setUsedAt(LocalDateTime.now().minusMinutes(2));
        when(passwordResetTokenRepository.findByTokenHash(sha256Hex("raw")))
                .thenReturn(Optional.of(stored));

        assertThrows(InvalidPasswordResetTokenException.class,
                () -> passwordService.resetPassword("raw", NEW_RAW));

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(refreshTokenService);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("ana@example.com")
                .password(CURRENT_HASH)
                .role(User.UserRole.BUSINESS_ADMIN)
                .build();
    }

    private PasswordResetToken storedToken(User user, LocalDateTime expiresAt) {
        return PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(sha256Hex("raw"))
                .issuedAt(LocalDateTime.now().minusMinutes(5))
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
