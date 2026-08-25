package com.platform.utils;

import com.platform.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class JwtUtils {

    /** Minimum key length for HS256. */
    private static final int MIN_SECRET_BYTES = 32;

    // No default. A fallback here would be a signing key that is public in the git history,
    // silently used by any context that forgets to set the property.
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationInMs;

    // Built once. This used to be rebuilt on every call, four times per authenticated request.
    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] keyBytes = jwtSecret == null ? new byte[0] : jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret must be at least " + MIN_SECRET_BYTES + " characters for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Token generation ──────────────────────────────────────────────────────

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId().toString());
        return createToken(claims, user.getEmail());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtExpirationInMs))
                .signWith(signingKey)  // algorithm inferred from key type
                .compact();
    }

    // ── Token reading ─────────────────────────────────────────────────────────

    /**
     * Verifies the signature and expiry and returns the claims, or empty if the token is
     * unusable for any reason.
     *
     * <p>Callers read every claim off the single returned {@link Claims}. Reading them through
     * separate accessors would re-verify the HMAC once per claim.
     *
     * @throws ExpiredJwtException so the caller can distinguish "log in again" from
     *         "this token is junk"; every other failure returns empty.
     */
    public Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (ExpiredJwtException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            // DEBUG, not ERROR: any unauthenticated caller can trigger this at will, so it
            // must not be able to fill the log. Never log the token itself.
            log.debug("JWT rejected: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public String getUserEmailFromClaims(Claims claims) {
        return claims.getSubject();
    }

    public String getUserIdFromClaims(Claims claims) {
        return claims.get("userId", String.class);
    }

    public String getRoleFromClaims(Claims claims) {
        return claims.get("role", String.class);
    }
}
