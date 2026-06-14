package com.jwtdemo.jwt_starter.backend.auth;

import com.jwtdemo.jwt_starter.backend.auth.dto.AuthResponse;
import com.jwtdemo.jwt_starter.backend.auth.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing the public authentication endpoints.
 *
 * <p>All paths under {@code /api/v1/auth/**} are declared as public in
 * {@link com.jwtdemo.jwt_starter.backend.common.security.SecurityConfig}, so
 * these endpoints can be reached without an access token.
 *
 * <p>This controller is intentionally thin — it only validates the request shape
 * and delegates all logic to {@link AuthService}. Keeping business logic out of
 * controllers makes it easier to test and reuse.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates the user with email and password.
     *
     * <p>On success, the response contains:
     * <ul>
     *   <li><b>Body</b>: a short-lived JWT access token the client stores in memory.</li>
     *   <li><b>Set-Cookie header</b>: a long-lived, httpOnly refresh token the browser
     *       manages automatically — JavaScript never sees it.</li>
     * </ul>
     *
     * <p>{@code @Valid} triggers Bean Validation on {@link LoginRequest} before the
     * method body runs, returning 400 automatically if required fields are missing.
     *
     * @param request  validated login credentials from the request body
     * @param response used by {@link AuthService} to attach the Set-Cookie header
     * @return 200 OK with an {@link AuthResponse} body
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(request, response));
    }

    /**
     * Issues a new access token using the refresh-token cookie.
     *
     * <p>The browser sends the httpOnly cookie automatically — the client does not
     * need to read or forward it. This endpoint is called by the Axios response
     * interceptor when a request fails with 401 (expired access token).
     *
     * <p>Implements refresh token rotation: the old token is revoked and a new one
     * is issued. Reuse of an already-revoked token triggers full session revocation.
     *
     * @param request  used to read the httpOnly refresh-token cookie
     * @param response used to set the new refresh-token cookie
     * @return 200 OK with a new {@link AuthResponse} body
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.refresh(request, response));
    }

    /**
     * Revokes the current session and clears the refresh-token cookie.
     *
     * <p>The access token is not explicitly invalidated — it will expire naturally
     * within its 15-minute window. The frontend is responsible for discarding it
     * from memory immediately after calling this endpoint.
     *
     * @param request  used to read the refresh-token cookie that should be revoked
     * @param response used to clear the cookie
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.noContent().build();
    }
}
