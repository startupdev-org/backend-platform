package com.platform.controller;

import com.platform.dto.auth.ChangePasswordRequest;
import com.platform.dto.auth.ForgotPasswordRequest;
import com.platform.dto.auth.LoginRequest;
import com.platform.dto.auth.LoginResponse;
import com.platform.dto.auth.RefreshTokenRequest;
import com.platform.dto.auth.RegisterRequest;
import com.platform.dto.auth.ResetPasswordRequest;
import com.platform.service.AuthService;
import com.platform.service.PasswordService;
import com.platform.utils.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Authentication endpoints")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordService passwordService;

    @Operation(summary = "Register", description = "Registers a new user and returns a JWT token")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "409", description = "Email already in use")
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Login", description = "Authenticates a user and returns a JWT token")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @ApiResponse(responseCode = "429", description = "Account temporarily locked, or too many attempts from this IP")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refresh",
            description = "Exchanges a refresh token for a new access token and a new refresh "
                    + "token. The presented token is single-use: it is spent here, and replaying "
                    + "it revokes every session for that account.")
    @ApiResponse(responseCode = "200", description = "New token pair issued")
    @ApiResponse(responseCode = "400", description = "Missing refresh token")
    @ApiResponse(responseCode = "401", description = "Refresh token unknown, expired or already spent")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @Operation(summary = "Logout",
            description = "Revokes the refresh token, so no further access tokens can be issued "
                    + "for that session. Idempotent. The current access token keeps working until "
                    + "it expires - that window is what jwt.expiration bounds.")
    @ApiResponse(responseCode = "204", description = "Refresh token revoked")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    // ── Password ──────────────────────────────────────────────────────────────

    @Operation(summary = "Change password",
            description = "Changes the authenticated user's own password. The current password "
                    + "is required, so a stolen access token alone cannot take the account over. "
                    + "Every existing session is signed out, this one included: the client must "
                    + "log in again with the new password.")
    @ApiResponse(responseCode = "204", description = "Password changed")
    @ApiResponse(responseCode = "400", description = "Current password wrong, new password too short, or unchanged")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "429", description = "Too many attempts from this IP")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Forgot password",
            description = "Starts password recovery. Always answers 202, whether or not the "
                    + "address has an account - answering differently would turn this into a "
                    + "membership check against the whole user table. The client must show the "
                    + "same confirmation either way.")
    @ApiResponse(responseCode = "202", description = "Request accepted; a link is sent if the address has an account")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "429", description = "Too many attempts from this IP")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.requestReset(request.getEmail());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Reset password",
            description = "Completes recovery using the single-use token from the emailed link. "
                    + "Spending the token also clears any login lockout and signs out every "
                    + "existing session for that account.")
    @ApiResponse(responseCode = "204", description = "Password reset")
    @ApiResponse(responseCode = "400", description = "Token unknown, expired or already used, or new password too short")
    @ApiResponse(responseCode = "429", description = "Too many attempts from this IP")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
