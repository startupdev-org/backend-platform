package com.platform.utils;

import com.platform.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtUtils} is configured by {@code @Value} fields and initialised in
 * {@code @PostConstruct}, so these tests set the fields directly and call {@code init()}
 * themselves rather than standing up a Spring context.
 */
class JwtUtilsTest {

    private static final String SECRET = "a-test-signing-secret-that-is-long-enough-32";
    private static final long EXPIRATION_MS = 900_000L;

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = newJwtUtils(SECRET, EXPIRATION_MS);
    }

    // ── The startup guard ─────────────────────────────────────────────────────

    /**
     * HS256 needs at least 256 bits of key. A shorter secret must fail at startup, not
     * silently produce tokens signed with a weak key.
     */
    @Test
    void init_rejectsASecretShorterThan32Bytes() {
        JwtUtils tooShort = new JwtUtils();
        ReflectionTestUtils.setField(tooShort, "jwtSecret", "31-characters-is-one-too-few!!!");
        ReflectionTestUtils.setField(tooShort, "jwtExpirationInMs", EXPIRATION_MS);

        IllegalStateException error = assertThrows(IllegalStateException.class, tooShort::init);
        assertTrue(error.getMessage().contains("at least 32"), error.getMessage());
    }

    @Test
    void init_rejectsAMissingSecret() {
        JwtUtils missing = new JwtUtils();
        ReflectionTestUtils.setField(missing, "jwtSecret", null);
        ReflectionTestUtils.setField(missing, "jwtExpirationInMs", EXPIRATION_MS);

        assertThrows(IllegalStateException.class, missing::init);
    }

    @Test
    void init_acceptsASecretOfExactly32Bytes() {
        JwtUtils exact = new JwtUtils();
        ReflectionTestUtils.setField(exact, "jwtSecret", "12345678901234567890123456789012");
        ReflectionTestUtils.setField(exact, "jwtExpirationInMs", EXPIRATION_MS);

        exact.init();  // must not throw
    }

    // ── Generation and round trip ─────────────────────────────────────────────

    @Test
    void generateToken_carriesEmailRoleAndUserId() {
        User user = user("Owner@Example.com", User.UserRole.BUSINESS_ADMIN);

        Claims claims = jwtUtils.parseClaims(jwtUtils.generateToken(user)).orElseThrow();

        assertEquals("Owner@Example.com", jwtUtils.getUserEmailFromClaims(claims));
        assertEquals("BUSINESS_ADMIN", jwtUtils.getRoleFromClaims(claims));
        assertEquals(user.getId().toString(), jwtUtils.getUserIdFromClaims(claims));
    }

    @Test
    void generateToken_setsTheConfiguredExpiry() {
        User user = user("a@b.io", User.UserRole.PLATFORM_ADMIN);

        Claims claims = jwtUtils.parseClaims(jwtUtils.generateToken(user)).orElseThrow();

        long lifetimeMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(EXPIRATION_MS, lifetimeMs);
    }

    @Test
    void getExpirationInSeconds_convertsTheConfiguredMilliseconds() {
        assertEquals(900L, jwtUtils.getExpirationInSeconds());
        assertEquals(60L, newJwtUtils(SECRET, 60_000L).getExpirationInSeconds());
    }

    // ── Rejection: expired versus junk ────────────────────────────────────────

    /**
     * The distinction the filter relies on: an expired token throws so the caller can
     * answer "log in again", while anything else returns empty and is simply unusable.
     */
    @Test
    void parseClaims_throwsForAnExpiredToken() {
        String expired = signedWith(SECRET, "a@b.io",
                new Date(System.currentTimeMillis() - 120_000),
                new Date(System.currentTimeMillis() - 60_000));

        assertThrows(ExpiredJwtException.class, () -> jwtUtils.parseClaims(expired));
    }

    @Test
    void parseClaims_returnsEmptyForATokenSignedWithAnotherKey() {
        String foreign = signedWith("a-completely-different-secret-key-32-bytes", "a@b.io",
                new Date(), new Date(System.currentTimeMillis() + 60_000));

        assertEquals(Optional.empty(), jwtUtils.parseClaims(foreign));
    }

    @Test
    void parseClaims_returnsEmptyForATamperedPayload() {
        String token = jwtUtils.generateToken(user("a@b.io", User.UserRole.BUSINESS_ADMIN));
        String[] parts = token.split("\\.");
        // Re-sign is impossible without the key, so flipping the payload breaks the HMAC.
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AB."
                + parts[2];

        assertEquals(Optional.empty(), jwtUtils.parseClaims(tampered));
    }

    @Test
    void parseClaims_returnsEmptyForGarbage() {
        assertEquals(Optional.empty(), jwtUtils.parseClaims("not-a-jwt"));
        assertEquals(Optional.empty(), jwtUtils.parseClaims(""));
        assertEquals(Optional.empty(), jwtUtils.parseClaims("a.b.c"));
        assertEquals(Optional.empty(), jwtUtils.parseClaims(null));
    }

    @Test
    void parseClaims_returnsEmptyForAnUnsignedToken() {
        // The "alg: none" shape - accepting it would let anyone mint any identity.
        String unsigned = Jwts.builder().subject("a@b.io").compact();

        assertEquals(Optional.empty(), jwtUtils.parseClaims(unsigned));
    }

    // ── Key isolation ─────────────────────────────────────────────────────────

    @Test
    void tokensDoNotVerifyAcrossDifferentSecrets() {
        JwtUtils other = newJwtUtils("yet-another-signing-secret-of-32-bytes+", EXPIRATION_MS);
        User user = user("a@b.io", User.UserRole.BUSINESS_ADMIN);

        String mine = jwtUtils.generateToken(user);

        assertNotEquals(Optional.empty(), jwtUtils.parseClaims(mine));
        assertEquals(Optional.empty(), other.parseClaims(mine));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JwtUtils newJwtUtils(String secret, long expirationMs) {
        JwtUtils utils = new JwtUtils();
        ReflectionTestUtils.setField(utils, "jwtSecret", secret);
        ReflectionTestUtils.setField(utils, "jwtExpirationInMs", expirationMs);
        utils.init();
        return utils;
    }

    private User user(String email, User.UserRole role) {
        return User.builder().id(UUID.randomUUID()).email(email).role(role).build();
    }

    private String signedWith(String secret, String subject, Date issuedAt, Date expiry) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claims(Map.of("role", "BUSINESS_ADMIN", "userId", UUID.randomUUID().toString()))
                .subject(subject)
                .issuedAt(issuedAt)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }
}
