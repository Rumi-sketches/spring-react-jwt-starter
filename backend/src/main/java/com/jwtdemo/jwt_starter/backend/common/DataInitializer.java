package com.jwtdemo.jwt_starter.backend.common;

import com.jwtdemo.jwt_starter.backend.users.User;
import com.jwtdemo.jwt_starter.backend.users.UserRepository;
import com.jwtdemo.jwt_starter.backend.users.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the database with demo users on first startup.
 *
 * <p>{@link ApplicationRunner} implementations are called by Spring Boot once the
 * application context is fully started. The seed is idempotent: if the {@code users}
 * table already has rows (e.g. on a restart), it does nothing.
 *
 * <p>Two demo accounts are created:
 * <ul>
 *   <li>{@code admin@demo.com / password} — ADMIN role</li>
 *   <li>{@code user@demo.com / password} — USER role</li>
 * </ul>
 *
 * <p><b>Production note</b>: remove or gate this class behind a profile (e.g.
 * {@code @Profile("dev")}) before deploying to a real environment.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates the demo users if the database is empty.
     *
     * <p>Passwords are hashed with BCrypt before being stored — the plaintext
     * string {@code "password"} never reaches the database.
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (userRepository.count() > 0) return;

        User admin = new User();
        admin.setFirstName("Admin");
        admin.setLastName("DemoAdmin");
        admin.setEmail("admin@demo.com");
        admin.setPasswordHash(passwordEncoder.encode("password"));
        admin.setRole(UserRole.ADMIN);

        User user = new User();
        user.setFirstName("User");
        user.setLastName("DemoUser");
        user.setEmail("user@demo.com");
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setRole(UserRole.USER);

        userRepository.saveAll(List.of(admin, user));
    }
}
