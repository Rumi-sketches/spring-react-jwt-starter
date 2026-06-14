package com.jwtdemo.jwt_starter.backend.common.security;


import com.jwtdemo.jwt_starter.backend.auth.JwtService;
import com.jwtdemo.jwt_starter.backend.users.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Intercepts every HTTP request and authenticates the caller if they present a valid JWT.
 *
 * <p>This filter is inserted into the Spring Security filter chain <em>before</em>
 * {@link org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter}.
 * It runs once per request (guaranteed by {@link OncePerRequestFilter}) and sets up the
 * {@link SecurityContextHolder} so that downstream code — controllers, method-security
 * annotations, etc. — can ask "who is the current user?" without touching the database again.
 *
 * <p>If no valid token is found, the filter simply passes the request along. Spring
 * Security's own machinery then rejects it with 401 if the endpoint requires authentication.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthFilter(JwtService jwtService,
                         UserDetailsServiceImpl userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Core filter logic executed for every incoming HTTP request.
     *
     * <p>Step-by-step:
     * <ol>
     *   <li>Read the {@code Authorization} header. If it is absent or does not start with
     *       {@code "Bearer "}, skip authentication entirely and let Spring Security decide
     *       whether the endpoint is public or protected.</li>
     *   <li>Strip the {@code "Bearer "} prefix to get the raw JWT string.</li>
     *   <li>Validate the token's signature and expiry with {@link JwtService#isTokenValid}.
     *       An invalid or expired token is treated the same as a missing token.</li>
     *   <li>Extract the user's email from the token and load the full {@link UserDetails}
     *       from the database. This step confirms the user still exists and is still active
     *       — a JWT alone cannot tell you if the account was disabled after it was issued.</li>
     *   <li>Build a {@link UsernamePasswordAuthenticationToken} — Spring Security's standard
     *       authentication object — and store it in the {@link SecurityContextHolder}.
     *       Every component in the same request thread can now read the current user from
     *       there, for example via {@code @AuthenticationPrincipal}.</li>
     *   <li>Continue the filter chain so the request reaches its target controller.</li>
     * </ol>
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Step 1: read Authorization header
        final String authHeader = request.getHeader("Authorization");

        // Step 2: if missing or wrong scheme, skip — Spring Security handles unauthenticated access
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 3: extract the JWT by removing the "Bearer " prefix (7 characters)
        final String token = authHeader.substring(7);

        // Step 4: reject tampered or expired tokens without touching the database
        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 5: load the user from the DB to verify the account is still active
        final String email = jwtService.extractEmail(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        // Step 6: register the authenticated user in the SecurityContext for this request
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,                        // credentials are null after authentication
                        userDetails.getAuthorities() // roles, used by method-security annotations
                );

        // Attach request metadata (IP address, session ID) for audit/logging purposes
        authToken.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        SecurityContextHolder.getContext().setAuthentication(authToken);

        // Step 7: hand off to the next filter (eventually the controller)
        filterChain.doFilter(request, response);
    }
}
