package com.jwtdemo.jwt_starter.backend.auth;

import com.jwtdemo.jwt_starter.backend.auth.dto.AuthResponse;
import com.jwtdemo.jwt_starter.backend.auth.dto.LoginRequest;
import com.jwtdemo.jwt_starter.backend.common.exceptions.UnauthorizedException;
import com.jwtdemo.jwt_starter.backend.users.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Core authentication business logic: login, token refresh, and logout.
 *
 * <p>This service is the only place in the application that creates and revokes
 * sessions. Every operation that touches the {@code refresh_tokens} table is
 * wrapped in a transaction so that partial failures (e.g. token saved but cookie
 * not set) are rolled back automatically.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-token-expiration-days}")
    private long refreshTokenExpirationDays;

    /** Set to {@code true} in production so the cookie is only sent over HTTPS. */
    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RefreshTokenRepository refreshTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Authenticates the user and issues a new access/refresh token pair.
     *
     * <p>Flow:
     * <ol>
     *   <li>Delegate credential verification to Spring Security's {@link AuthenticationManager}.
     *       It loads the user from the DB, hashes the supplied password with BCrypt, and
     *       compares it to the stored hash. Throws {@link org.springframework.security.authentication.BadCredentialsException}
     *       on mismatch.</li>
     *   <li>Generate a short-lived JWT access token (15 min).</li>
     *   <li>Generate a long-lived opaque refresh token (7 days), hash it with SHA-256,
     *       and persist only the hash to the database.</li>
     *   <li>Send the raw refresh token to the client as an httpOnly cookie — JavaScript
     *       cannot read it, which eliminates the XSS token-theft vector.</li>
     *   <li>Return the access token in the response body — the frontend stores it in
     *       memory (a React state variable), never in localStorage.</li>
     * </ol>
     *
     * @param request  login credentials (email + password)
     * @param response HTTP response used to attach the Set-Cookie header
     * @return an {@link AuthResponse} containing the access token
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        // Spring Security verifies email + password; throws BadCredentialsException on failure
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = (User) auth.getPrincipal();

        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRawRefreshToken();

        saveRefreshToken(user, rawRefreshToken);
        addRefreshTokenCookie(response, rawRefreshToken);

        return new AuthResponse(accessToken);
    }

    /**
     * Issues a new access/refresh token pair in exchange for a valid, non-revoked refresh token.
     *
     * <p><b>Refresh token rotation</b>: every successful refresh call invalidates the
     * presented token and issues a brand-new one. This limits the window in which a
     * stolen token can be reused.
     *
     * <p><b>Reuse detection</b>: if the presented token is already marked as revoked in
     * the database, it means either the client is retrying a request whose response was
     * lost, or an attacker is replaying a stolen token. Because we cannot tell these
     * cases apart, the safe response is to revoke <em>all</em> sessions for that user
     * and force a fresh login. This is sometimes called "refresh token family revocation."
     *
     * @param request  HTTP request containing the httpOnly refresh-token cookie
     * @param response HTTP response used to set the new cookie
     * @return an {@link AuthResponse} containing the new access token
     * @throws UnauthorizedException if the cookie is missing, the token is unknown,
     *                               revoked, or expired
     */
    @Transactional
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = extractRefreshTokenFromCookie(request);
        if (rawToken == null) {
            throw new UnauthorizedException("Refresh token missing");
        }

        String tokenHash = jwtService.hashRefreshToken(rawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (stored.isRevoked()) {
            // Token already used or stolen: revoke every session and force re-login
            revokeAllUserTokens(stored.getUser());
            throw new UnauthorizedException(
                    "Refresh token reuse detected — all sessions have been revoked"
            );
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token expired");
        }

        // Rotation: mark the old token as revoked, then issue a new pair
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRawRefreshToken = jwtService.generateRawRefreshToken();

        saveRefreshToken(user, newRawRefreshToken);
        addRefreshTokenCookie(response, newRawRefreshToken);

        return new AuthResponse(newAccessToken);
    }

    /**
     * Revokes the current refresh token and clears the httpOnly cookie.
     *
     * <p>The access token is <em>not</em> explicitly invalidated because JWTs are
     * stateless — the server has no list to remove them from. The token simply becomes
     * useless after its 15-minute expiry. This is an accepted trade-off: keep expiry
     * short so the damage window for a stolen access token is small.
     *
     * <p>If the cookie is already absent (e.g. double-logout), the method completes
     * silently — no error is thrown.
     *
     * @param request  HTTP request containing the httpOnly refresh-token cookie
     * @param response HTTP response used to clear the cookie
     */
    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = extractRefreshTokenFromCookie(request);
        if (rawToken != null) {
            String tokenHash = jwtService.hashRefreshToken(rawToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
        clearRefreshTokenCookie(response);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Persists a new refresh token record. Only the SHA-256 hash of the raw token is
     * stored — the plaintext value never touches the database.
     */
    private void saveRefreshToken(User user, String rawToken) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(jwtService.hashRefreshToken(rawToken));
        token.setExpiresAt(Instant.now().plus(refreshTokenExpirationDays, ChronoUnit.DAYS));
        refreshTokenRepository.save(token);
    }

    /**
     * Marks every refresh token belonging to the user as revoked in a single batch.
     * Called when token reuse is detected to terminate all active sessions immediately.
     */
    private void revokeAllUserTokens(User user) {
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUser(user);
        tokens.forEach(t -> t.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
    }

    /**
     * Attaches the refresh token to the response as an httpOnly, SameSite=Strict cookie.
     *
     * <p>Key cookie attributes and their security purpose:
     * <ul>
     *   <li><b>HttpOnly</b>: JavaScript cannot access this cookie at all —
     *       completely eliminates XSS-based token theft.</li>
     *   <li><b>SameSite=Strict</b>: the browser will not send this cookie on
     *       cross-site requests, preventing CSRF attacks.</li>
     *   <li><b>Path=/api/v1/auth</b>: the cookie is sent only to auth endpoints,
     *       not to every request the browser makes to this server.</li>
     *   <li><b>Secure</b>: enforced in production so the cookie travels only over HTTPS.</li>
     * </ul>
     */
    private void addRefreshTokenCookie(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", rawToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(refreshTokenExpirationDays))
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Overwrites the refresh-token cookie with an empty value and maxAge=0,
     * which instructs the browser to delete it immediately.
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Reads the raw refresh token value from the incoming request's cookies.
     *
     * @return the token string, or {@code null} if the cookie is absent
     */
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "refresh_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
