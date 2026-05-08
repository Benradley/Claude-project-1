package com.droneanalytics.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Thin wrapper around the JJWT 0.12 API.
 *
 * <p>Tokens are signed with HS256.  The secret is loaded from
 * {@code drone.jwt.secret} — override with a strong random value in production
 * (e.g. {@code openssl rand -hex 32}).
 */
@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    private final SecretKey key;
    private final long      expirationSeconds;

    public JwtUtils(
            @Value("${drone.jwt.secret}") String secret,
            @Value("${drone.jwt.expiration-seconds:86400}") long expirationSeconds) {

        // Keys.hmacShaKeyFor requires >= 32 bytes for HS256
        this.key               = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    // ── Token generation ─────────────────────────────────────────────────────

    /**
     * Creates a signed JWT whose subject is the user's email address.
     *
     * @param email  the authenticated user's email
     * @return       compact serialised JWT string
     */
    public String generateToken(String email) {
        Instant now     = Instant.now();
        Instant expires = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
            .subject(email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expires))
            .signWith(key)
            .compact();
    }

    // ── Token validation ─────────────────────────────────────────────────────

    /**
     * Validates the token and returns the subject (email), or {@code null}
     * if the token is invalid, expired, or tampered with.
     */
    public String validateAndGetEmail(String token) {
        try {
            return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    /** Returns true only if the token parses and is not expired. */
    public boolean isValid(String token) {
        return validateAndGetEmail(token) != null;
    }

    public long getExpirationSeconds() { return expirationSeconds; }
}
