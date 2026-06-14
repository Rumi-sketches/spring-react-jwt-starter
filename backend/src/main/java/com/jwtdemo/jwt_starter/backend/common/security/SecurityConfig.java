package com.jwtdemo.jwt_starter.backend.common.security;

import com.jwtdemo.jwt_starter.backend.users.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Central Spring Security configuration.
 *
 * <p>This class wires together the filter chain, CORS policy, session strategy,
 * authorization rules, and the beans that the rest of the security infrastructure
 * depends on (password encoder, authentication manager).
 *
 * <p>{@code @EnableMethodSecurity} activates {@code @PreAuthorize} / {@code @Secured}
 * annotations on controller methods, enabling fine-grained role checks beyond what the
 * URL-level rules in {@link #filterChain} provide.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService, JwtAuthFilter jwtAuthFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * Defines the HTTP security filter chain — the ordered pipeline through which every
     * request passes before reaching a controller.
     *
     * <p>Configuration decisions explained:
     * <ul>
     *   <li><b>CSRF disabled</b>: CSRF protection works by comparing a secret token
     *       stored in the session with a value in the request. Because this API is fully
     *       stateless (no server-side session) and the refresh token cookie uses
     *       {@code SameSite=Strict}, CSRF attacks are already prevented at the cookie
     *       level. Enabling CSRF here would add complexity with no security benefit.</li>
     *   <li><b>STATELESS sessions</b>: Spring Security is told never to create or use an
     *       HTTP session. Authentication state lives entirely in the JWT on each request.
     *       This makes the backend horizontally scalable — any server instance can handle
     *       any request without shared session storage.</li>
     *   <li><b>CORS</b>: the browser enforces the Same-Origin Policy, blocking the React
     *       dev server (port 5173) from calling the API (port 8080) unless the server
     *       explicitly permits it. See {@link #corsConfigurationSource()}.</li>
     *   <li><b>Authorization rules</b>: {@code /api/v1/auth/**} is public (login, refresh,
     *       logout). Every other endpoint requires a valid JWT. Additional role-level
     *       restrictions can be added with {@code @PreAuthorize} on individual methods.</li>
     *   <li><b>JwtAuthFilter position</b>: inserted before the default username/password
     *       filter so our token-based authentication runs first.</li>
     *   <li><b>401 instead of 302</b>: by default Spring Security redirects unauthenticated
     *       API requests to a login page (302). REST clients expect 401, so we override
     *       the {@code authenticationEntryPoint} to send that instead.</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex ->
                ex.authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access Denied")));

        return http.build();
    }

    /**
     * Exposes Spring Security's {@link AuthenticationManager} as a bean so that
     * {@link com.jwtdemo.jwt_starter.backend.auth.AuthService} can inject and call it
     * directly during login.
     *
     * <p>Spring Boot auto-configures a {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider}
     * under the hood because it detects both a {@link PasswordEncoder} bean and a
     * {@link org.springframework.security.core.userdetails.UserDetailsService} bean in the
     * context. We do not need to declare it manually.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Provides a BCrypt password encoder used when hashing passwords at registration
     * and when verifying them at login.
     *
     * <p>BCrypt is intentionally slow (configurable cost factor, default 10 rounds).
     * This makes brute-force and dictionary attacks computationally expensive even if
     * an attacker obtains the hashed passwords from the database.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures which cross-origin requests the browser is allowed to make.
     *
     * <p>In development the React Vite server runs on {@code http://localhost:5173}
     * while the API runs on port 8080 — different ports mean different origins.
     * Without this CORS configuration the browser would block every API call.
     *
     * <p>{@code allowCredentials(true)} is required so the browser includes the
     * httpOnly refresh-token cookie in cross-origin requests. It must be paired with
     * an explicit origin list (wildcards are forbidden when credentials are involved).
     *
     * <p>In production, replace {@code localhost:5173} with the real frontend domain.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
