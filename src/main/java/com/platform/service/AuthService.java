package com.platform.service;

import com.platform.config.RateLimitProperties;
import com.platform.dto.auth.LoginRequest;
import com.platform.dto.auth.LoginResponse;
import com.platform.dto.auth.RegisterRequest;
import com.platform.entity.RefreshToken;
import com.platform.entity.User;
import com.platform.exception.AccountLockedException;
import com.platform.exception.EmailAlreadyRegisteredException;
import com.platform.exception.InvalidCredentialsException;
import com.platform.exception.UserNotEnabledException;
import com.platform.repository.UserRepository;
import com.platform.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RateLimitProperties rateLimitProperties;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String email = UserService.normalizeEmail(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException("Email already registered");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(User.UserRole.BUSINESS_ADMIN)
                .build();

        try {
            // saveAndFlush, not save: inside @Transactional a plain save defers the INSERT to
            // commit, which happens after this method returns - the catch would never fire.
            // The check above gives the friendly message; this is what makes it correct when
            // two registrations for the same email race.
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException("Email already registered");
        }

        return issueSession(user);
    }

    /**
     * @param clientIp source IP of the attempt, for the failed-login audit log. May be null.
     */
    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        String email = UserService.normalizeEmail(request.getEmail());

        // Both failure paths throw the same exception with the same message. Distinguishing
        // "no such user" from "wrong password" turns this endpoint into a user-enumeration
        // oracle.
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials", email, clientIp));

        RateLimitProperties.Lockout lockout = rateLimitProperties.getLockout();

        if (lockout.isEnabled() && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long retryAfter = Math.max(1,
                    Duration.between(LocalDateTime.now(), user.getLockedUntil()).toSeconds());
            throw new AccountLockedException(
                    "Account temporarily locked due to repeated failed login attempts",
                    email, clientIp, retryAfter);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            registerFailedAttempt(user, lockout);
            throw new InvalidCredentialsException("Invalid credentials", email, clientIp);
        }

        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.saveAndFlush(user);
        }

        return issueSession(user);
    }

    /**
     * Exchanges a refresh token for a new access token, rotating the refresh token in the
     * process. The old one is spent here, so a replay of it revokes every session for the
     * user - see {@link RefreshTokenService#verifyAndConsume}.
     */
    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        RefreshToken spent = refreshTokenService.verifyAndConsume(rawRefreshToken);
        User user = spent.getUser();

        // The one place a disabled or demoted account is re-checked against the database.
        // The access token carries role and identity as claims for its whole lifetime, so
        // this is where a change actually takes effect - within one access-token lifetime.
        if (!user.isEnabled()) {
            refreshTokenService.revokeAllForUser(user);
            throw new UserNotEnabledException("User is not enabled");
        }

        LoginResponse response = issueSession(user);
        refreshTokenService.linkSuccessor(spent, response.getRefreshToken());
        return response;
    }

    /** Idempotent: an unknown or already-spent token is not an error. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private LoginResponse issueSession(User user) {
        return LoginResponse.fromUser(
                user,
                jwtUtils.generateToken(user),
                refreshTokenService.issue(user).rawToken(),
                jwtUtils.getExpirationInSeconds());
    }

    private void registerFailedAttempt(User user, RateLimitProperties.Lockout lockout) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (lockout.isEnabled() && attempts >= lockout.getMaxFailedAttempts()) {
            int overshoot = attempts - lockout.getMaxFailedAttempts();
            long minutes = Math.min(
                    lockout.getMaxMinutes(),
                    lockout.getBaseMinutes() << Math.min(overshoot, 20));
            user.setLockedUntil(LocalDateTime.now().plusMinutes(minutes));
        }

        userRepository.saveAndFlush(user);
    }
}
