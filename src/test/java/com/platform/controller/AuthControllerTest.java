package com.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.controller.support.SecurityFilterChainTestConfig;
import com.platform.dto.auth.LoginRequest;
import com.platform.dto.auth.LoginResponse;
import com.platform.dto.auth.RegisterRequest;
import com.platform.exception.EmailAlreadyRegisteredException;
import com.platform.exception.InvalidCredentialsException;
import com.platform.service.AuthService;
import com.platform.service.PasswordService;
import com.platform.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer coverage for {@link AuthController} (BP-64): request binding and
 * validation, the exception-to-status mapping, and that the public auth surface
 * is reachable without a token. rate-limit.enabled=false so a handful of POSTs
 * from one MockMvc client do not trip the per-IP throttle mid-suite.
 */
@WebMvcTest(controllers = AuthController.class, properties = "rate-limit.enabled=false")
@Import(SecurityFilterChainTestConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private AuthService authService;

    @MockBean
    private PasswordService passwordService;

    @MockBean
    private JwtUtils jwtUtils;

    private LoginResponse tokenPair() {
        return LoginResponse.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .firstName("Olivia")
                .lastName("Owner")
                .role("BUSINESS_ADMIN")
                .accessToken("access-token")
                .tokenType("Bearer")
                .refreshToken("refresh-token")
                .expiresIn(900)
                .build();
    }

    @Test
    void register_happyPath_returns201WithTokenPair() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(tokenPair());

        RegisterRequest request = new RegisterRequest(
                "owner@example.com", "password1", "Olivia", "Owner", null);

        mvc.perform(post("/api/auth/register").with(anonymous())
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void register_shortPassword_returns400AndNeverCallsTheService() throws Exception {
        RegisterRequest bad = new RegisterRequest(
                "owner@example.com", "short", "Olivia", "Owner", null);

        mvc.perform(post("/api/auth/register").with(anonymous())
                        .contentType("application/json")
                        .content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(authService);
    }

    @Test
    void register_duplicateEmail_maps409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyRegisteredException("Email already in use"));

        RegisterRequest request = new RegisterRequest(
                "taken@example.com", "password1", "Olivia", "Owner", null);

        mvc.perform(post("/api/auth/register").with(anonymous())
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_happyPath_returns200() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(tokenPair());

        mvc.perform(post("/api/auth/login").with(anonymous())
                        .contentType("application/json")
                        .content(json.writeValueAsString(new LoginRequest("owner@example.com", "password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void login_wrongPassword_maps401() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));

        mvc.perform(post("/api/auth/login").with(anonymous())
                        .contentType("application/json")
                        .content(json.writeValueAsString(new LoginRequest("owner@example.com", "nope"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_malformedBody_returns400() throws Exception {
        mvc.perform(post("/api/auth/login").with(anonymous())
                        .contentType("application/json")
                        .content("{ not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_withoutAuthentication_returns401() throws Exception {
        mvc.perform(post("/api/auth/change-password").with(anonymous())
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"password1\",\"newPassword\":\"password2\"}"))
                .andExpect(status().isUnauthorized());

        org.mockito.Mockito.verifyNoInteractions(passwordService);
    }

    @Test
    void forgotPassword_alwaysReturns202() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").with(anonymous())
                        .contentType("application/json")
                        .content("{\"email\":\"whoever@example.com\"}"))
                .andExpect(status().isAccepted());
    }
}
