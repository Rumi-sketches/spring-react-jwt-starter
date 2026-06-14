package com.jwtdemo.jwt_starter.backend.users;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints for accessing the currently authenticated user's own data.
 *
 * <p>All routes here are protected by the JWT filter chain — an unauthenticated
 * request never reaches this controller.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UsersController {

    /**
     * Returns the email and role of the currently authenticated user.
     *
     * <p>{@code @AuthenticationPrincipal} is a Spring Security annotation that injects
     * the {@link User} object stored in the {@link org.springframework.security.core.context.SecurityContext}
     * by {@link com.jwtdemo.jwt_starter.backend.common.security.JwtAuthFilter}.
     * No database query is needed here — the user was already loaded by the filter.
     *
     * <p>This endpoint is typically called by the frontend immediately after login or
     * page refresh to populate the UI with the user's identity.
     *
     * @param user the authenticated user injected from the security context
     * @return 200 OK with a JSON body containing {@code email} and {@code role}
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "role", user.getRole().name()
        ));
    }
}
