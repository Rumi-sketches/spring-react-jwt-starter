package com.jwtdemo.jwt_starter.backend.auth;

import com.jwtdemo.jwt_starter.backend.users.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/**
 * Handles all JWT and refresh-token cryptographic operations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Generate signed JWTs (access tokens)</li>
 *   <li>Generate cryptographically secure refresh token strings</li>
 *   <li>Hash refresh tokens with SHA-256 before they are stored</li>
 *   <li>Validate and parse JWTs</li>
 * </ul>
 *
 * <p>Why two different token types?
 * <ul>
 *   <li><b>Access token (JWT)</b>: self-contained, stateless, validated by checking the signature —
 *       no database round-trip needed on every request. Short-lived (15 min) to limit damage if stolen.</li>
 *   <li><b>Refresh token (opaque string)</b>: not a JWT. It is just a random value stored in the
 *       database (as a hash). Long-lived (7 days), but revocable at any time.</li>
 * </ul>
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    /**
     * Builds and signs a JWT access token for the given user.
     *
     * <p>The token payload (claims) contains:
     * <ul>
     *   <li>{@code sub} — the user's email, used as the unique identifier</li>
     *   <li>{@code role} — the user's role (e.g. "USER" or "ADMIN")</li>
     *   <li>{@code iat} — issued-at timestamp</li>
     *   <li>{@code exp} — expiration timestamp (now + configured duration)</li>
     * </ul>
     *
     * <p>The token is signed with HMAC-SHA256 using the application secret.
     * The signature guarantees the payload was not tampered with, but the payload
     * itself is Base64-encoded — not encrypted. Never put sensitive data in a JWT.
     *
     * @param user the authenticated user
     * @return a compact, URL-safe JWT string (three Base64 segments separated by dots)
     */
    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusMillis(accessTokenExpirationMs)))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generates a cryptographically secure, random refresh token.
     *
     * <p>This is <b>not</b> a JWT — it is a 256-bit opaque random string encoded in
     * Base64url. "Opaque" means it carries no structured information; the server looks
     * it up in the database by its hash to find the associated session.
     *
     * <p>{@link SecureRandom} is used instead of {@link java.util.Random} because it is
     * cryptographically strong: the output cannot be predicted from previous values.
     *
     * @return a 43-character Base64url string (32 random bytes without padding)
     */
    public String generateRawRefreshToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Returns the SHA-256 hex digest of a raw refresh token.
     *
     * <p>We never store the raw token in the database. If an attacker dumps the
     * {@code refresh_tokens} table, they get only hashes — useless without the originals,
     * which only exist in the client's httpOnly cookie.
     *
     * <p>SHA-256 is a one-way function: given the hash you cannot recover the token.
     * Unlike passwords, refresh tokens are already high-entropy random strings, so a
     * salt is not necessary here.
     *
     * @param rawToken the plaintext refresh token received from the client
     * @return a 64-character lowercase hex string (32 bytes = 256 bits)
     */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java specification — this can never happen
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Extracts the email address (JWT {@code sub} claim) from a valid token.
     *
     * <p>Call {@link #isTokenValid(String)} first if you are not certain the token is
     * well-formed and unexpired. This method throws if the token is invalid.
     *
     * @param token a signed JWT string
     * @return the email stored in the {@code sub} claim
     */
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Returns {@code true} if the token has a valid signature and has not expired.
     *
     * <p>The JJWT parser verifies both the HMAC-SHA256 signature and the {@code exp}
     * claim in a single call. Any tampered or expired token causes a {@link JwtException},
     * which this method catches and maps to {@code false}.
     *
     * @param token the JWT string to validate
     * @return {@code true} if valid, {@code false} otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Parses the token and returns its claims (payload).
     *
     * <p>This is the single point where JJWT verifies the signature against the
     * signing key and checks the expiration timestamp. Throws {@link JwtException}
     * for any validation failure (bad signature, expired, malformed).
     */
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Decodes the Base64-encoded application secret and converts it into a
     * {@link SecretKey} suitable for HMAC-SHA256 signing.
     *
     * <p>The secret is stored in application properties as a Base64 string so it can
     * be kept in environment variables without encoding issues. JJWT's
     * {@link Keys#hmacShaKeyFor} enforces a minimum key length of 256 bits.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
