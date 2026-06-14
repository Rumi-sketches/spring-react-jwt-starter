package com.jwtdemo.jwt_starter.backend.common.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Centralized exception handler for the entire application.
 *
 * <p>{@code @RestControllerAdvice} intercepts exceptions thrown by any {@code @RestController}
 * and allows us to map them to consistent HTTP error responses in one place, instead of
 * scattering try/catch blocks across controllers.
 *
 * <p>Current error format is a simple {@code { "error": "message" }} JSON object.
 * For a production API, consider migrating to the RFC 7807 "Problem Details" standard
 * (Spring supports it natively via {@link org.springframework.web.ErrorResponse}).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles failed login attempts (wrong email or password).
     *
     * <p>Spring Security throws {@link BadCredentialsException} when the
     * {@link org.springframework.security.authentication.AuthenticationManager} cannot
     * verify the supplied credentials. We intentionally return a generic message —
     * not specifying whether the email or the password was wrong prevents
     * user-enumeration attacks.
     *
     * @return 401 Unauthorized with a generic error body
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials() {
        return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid credentials"));
    }

    /**
     * Handles all custom authorization failures thrown by the application logic
     * (missing cookie, revoked token, expired token, reuse detection).
     *
     * @param ex the exception carrying the specific error message
     * @return 401 Unauthorized with the exception message in the body
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(
            UnauthorizedException ex) {
        return ResponseEntity.status(401)
                .body(Map.of("error", ex.getMessage()));
    }
}
