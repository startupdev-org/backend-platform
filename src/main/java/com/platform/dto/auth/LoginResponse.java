package com.platform.dto.auth;

import com.platform.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String accessToken;
    private String tokenType;

    /**
     * The refresh token, in the response body rather than a cookie so the SPA can hold
     * it the same way it holds the access token. Sent once, at login and at each
     * refresh; the server keeps only its hash.
     */
    private String refreshToken;

    /** Access-token lifetime in seconds, so the client can schedule the refresh. */
    private long expiresIn;

    public static LoginResponse fromUser(User user, String accessToken, String refreshToken, long expiresInSeconds) {
        return LoginResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .accessToken(accessToken)
                .tokenType("Bearer")
                .refreshToken(refreshToken)
                .expiresIn(expiresInSeconds)
                .build();
    }
}
