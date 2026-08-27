package com.platform.service;

import com.platform.dto.auth.LoginRequest;
import com.platform.dto.auth.LoginResponse;
import com.platform.dto.auth.RegisterRequest;
import com.platform.config.RateLimitProperties;
import com.platform.entity.User;
import com.platform.exception.AccountLockedException;
import com.platform.exception.EmailAlreadyRegisteredException;
import com.platform.exception.InvalidCredentialsException;
import com.platform.exception.InvalidRefreshTokenException;
import com.platform.exception.UserNotEnabledException;
import com.platform.entity.RefreshToken;
import com.platform.repository.UserRepository;
import com.platform.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RefreshTokenService refreshTokenService;

    // Real config with production defaults (lockout after 5, base 15 min).
    @Spy
    private RateLimitProperties rateLimitProperties = new RateLimitProperties();

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private static final String IP = "203.0.113.7";

    // Every successful login and register path now issues a refresh token alongside the
    // access token; lenient so the failure-path tests that never get there still pass.
    @BeforeEach
    void stubRefreshTokenIssue() {
        lenient().when(refreshTokenService.issue(any(User.class)))
                .thenReturn(new RefreshTokenService.IssuedToken(
                        "refresh-token", LocalDateTime.now().plusDays(30)));
    }

    private RegisterRequest registerRequest(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword("password123");
        request.setFirstName("Ana");
        request.setLastName("Popescu");
        request.setPhone("+37360000000");
        return request;
    }

    private User persistedUser(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password("encodedPassword")
                .firstName("Ana")
                .lastName("Popescu")
                .role(User.UserRole.BUSINESS_ADMIN)
                .build();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_success() {
        RegisterRequest request = registerRequest("test@example.com");

        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(persistedUser("test@example.com"));
        when(jwtUtils.generateToken(any(User.class))).thenReturn("jwt-token");

        LoginResponse response = authService.register(request);

        assertEquals("test@example.com", response.getEmail());
        assertEquals("jwt-token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("Ana", response.getFirstName());
    }

    /**
     * The assertion that was missing when {@code User.isEnabled} lost its initializer to
     * Lombok's builder and every account was created disabled.
     */
    @Test
    void register_persistsEnabledBusinessAdminWithEncodedPassword() {
        RegisterRequest request = registerRequest("test@example.com");

        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(persistedUser("test@example.com"));
        when(jwtUtils.generateToken(any(User.class))).thenReturn("jwt-token");

        authService.register(request);

        verify(userRepository).saveAndFlush(userCaptor.capture());
        User saved = userCaptor.getValue();

        assertTrue(saved.isEnabled(), "new accounts must be enabled");
        assertEquals(User.UserRole.BUSINESS_ADMIN, saved.getRole());
        assertEquals("encodedPassword", saved.getPassword());
        assertNotEquals("password123", saved.getPassword());
        assertEquals("Ana", saved.getFirstName());
        assertEquals("Popescu", saved.getLastName());
    }

    @Test
    void register_normalizesEmailToLowercase() {
        RegisterRequest request = registerRequest("  Test@Example.COM  ");

        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(persistedUser("test@example.com"));
        when(jwtUtils.generateToken(any(User.class))).thenReturn("jwt-token");

        authService.register(request);

        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals("test@example.com", userCaptor.getValue().getEmail());
    }

    @Test
    void register_emailAlreadyExists_throwsConflict() {
        RegisterRequest request = registerRequest("test@example.com");

        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);

        EmailAlreadyRegisteredException ex = assertThrows(EmailAlreadyRegisteredException.class,
                () -> authService.register(request));
        assertEquals("Email already registered", ex.getMessage());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    /** Two registrations racing past the existence check: the DB constraint is the real guard. */
    @Test
    void register_concurrentDuplicate_translatesConstraintViolation() {
        RegisterRequest request = registerRequest("test@example.com");

        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(EmailAlreadyRegisteredException.class, () -> authService.register(request));
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User user = persistedUser("test@example.com");
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
        when(jwtUtils.generateToken(user)).thenReturn("jwt-token");

        LoginResponse response = authService.login(request, IP);

        assertEquals("test@example.com", response.getEmail());
        assertEquals("jwt-token", response.getAccessToken());
        assertEquals("BUSINESS_ADMIN", response.getRole());
    }

    @Test
    void login_isCaseInsensitive() {
        LoginRequest request = new LoginRequest();
        request.setEmail("TEST@Example.com");
        request.setPassword("password123");

        User user = persistedUser("test@example.com");
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtils.generateToken(user)).thenReturn("jwt-token");

        assertEquals("test@example.com", authService.login(request, IP).getEmail());
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("missing@example.com");
        request.setPassword("password123");

        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(request, IP));
        assertEquals("Invalid credentials", ex.getMessage());
        assertEquals(IP, ex.getClientIp());
    }

    @Test
    void login_invalidPassword_throwsInvalidCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongPassword");

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(persistedUser("test@example.com")));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(request, IP));

        // Identical to the unknown-email message: distinguishing them would make this
        // endpoint a user-enumeration oracle.
        assertEquals("Invalid credentials", ex.getMessage());
    }

    // ── lockout ───────────────────────────────────────────────────────────────

    @Test
    void login_failedPassword_incrementsCounter() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongPassword");

        User user = persistedUser("test@example.com");
        user.setFailedLoginAttempts(2);
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request, IP));

        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals(3, userCaptor.getValue().getFailedLoginAttempts());
        assertNull(userCaptor.getValue().getLockedUntil(), "not locked before the threshold");
    }

    @Test
    void login_reachingThreshold_locksAccount() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongPassword");

        User user = persistedUser("test@example.com");
        user.setFailedLoginAttempts(4); // 5th failure trips the default threshold
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request, IP));

        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals(5, userCaptor.getValue().getFailedLoginAttempts());
        assertNotNull(userCaptor.getValue().getLockedUntil());
        assertTrue(userCaptor.getValue().getLockedUntil().isAfter(LocalDateTime.now()));
    }

    @Test
    void login_whileLocked_throwsAccountLockedWithoutCheckingPassword() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User user = persistedUser("test@example.com");
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));

        AccountLockedException ex = assertThrows(AccountLockedException.class,
                () -> authService.login(request, IP));
        assertEquals(IP, ex.getClientIp());
        assertTrue(ex.getRetryAfterSeconds() > 0);
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void login_expiredLock_allowsLoginAndClearsCounter() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User user = persistedUser("test@example.com");
        user.setFailedLoginAttempts(6);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // lock already expired
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
        when(jwtUtils.generateToken(user)).thenReturn("jwt-token");

        LoginResponse response = authService.login(request, IP);

        assertEquals("jwt-token", response.getAccessToken());
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals(0, userCaptor.getValue().getFailedLoginAttempts());
        assertNull(userCaptor.getValue().getLockedUntil());
    }

    @Test
    void login_success_withCleanCounter_doesNotWrite() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User user = persistedUser("test@example.com");
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
        when(jwtUtils.generateToken(user)).thenReturn("jwt-token");

        authService.login(request, IP);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    // ==================== refresh ====================

    @Test
    void refresh_rotatesBothTokens() {
        User user = persistedUser("test@example.com");
        RefreshToken spent = RefreshToken.builder().user(user).build();

        when(refreshTokenService.verifyAndConsume("old-refresh")).thenReturn(spent);
        when(jwtUtils.generateToken(user)).thenReturn("new-jwt");
        when(jwtUtils.getExpirationInSeconds()).thenReturn(900L);

        LoginResponse response = authService.refresh("old-refresh");

        assertEquals("new-jwt", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(900L, response.getExpiresIn());
        // the spent token records its successor, so a replay can be traced along the chain
        verify(refreshTokenService).linkSuccessor(spent, "refresh-token");
    }

    // The one place a disabled account is re-checked against the database - the access
    // token carries its claims for its whole lifetime.
    @Test
    void refresh_disabledUser_isCutOffAndAllSessionsRevoked() {
        User user = persistedUser("test@example.com");
        user.setEnabled(false);
        RefreshToken spent = RefreshToken.builder().user(user).build();

        when(refreshTokenService.verifyAndConsume("old-refresh")).thenReturn(spent);

        assertThrows(UserNotEnabledException.class, () -> authService.refresh("old-refresh"));

        verify(refreshTokenService).revokeAllForUser(user);
        verify(jwtUtils, never()).generateToken(any());
    }

    @Test
    void refresh_invalidToken_propagates() {
        when(refreshTokenService.verifyAndConsume("bad"))
                .thenThrow(new InvalidRefreshTokenException("Invalid refresh token"));

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh("bad"));
    }

    // ==================== logout ====================

    @Test
    void logout_revokesTheRefreshToken() {
        authService.logout("some-refresh");

        verify(refreshTokenService).revoke("some-refresh");
    }
}
