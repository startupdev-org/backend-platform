package com.platform.service;

import com.platform.dto.auth.LoginRequest;
import com.platform.dto.auth.LoginResponse;
import com.platform.dto.auth.RegisterRequest;
import com.platform.entity.User;
import com.platform.exception.EmailAlreadyRegisteredException;
import com.platform.exception.InvalidCredentialsException;
import com.platform.repository.UserRepository;
import com.platform.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

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

        return LoginResponse.fromUser(user, jwtUtils.generateToken(user));
    }

    public LoginResponse login(LoginRequest request) {
        // Both failure paths throw the same exception with the same message. Distinguishing
        // "no such user" from "wrong password" turns this endpoint into a user-enumeration
        // oracle.
        User user = userRepository.findByEmailIgnoreCase(UserService.normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        return LoginResponse.fromUser(user, jwtUtils.generateToken(user));
    }
}
