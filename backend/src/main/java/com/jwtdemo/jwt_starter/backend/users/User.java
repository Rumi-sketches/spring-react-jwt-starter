package com.jwtdemo.jwt_starter.backend.users;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity that also implements Spring Security's {@link UserDetails} contract.
 *
 * <p>By implementing {@link UserDetails} directly on the entity, we avoid a
 * separate adapter class. Spring Security's {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider}
 * calls {@link UserDetailsServiceImpl#loadUserByUsername} during login,
 * receives this object, and uses its methods to verify credentials and load authorities.
 *
 * <p>After authentication, the same object is stored in the {@link org.springframework.security.core.context.SecurityContext}
 * and can be injected into controllers with {@code @AuthenticationPrincipal User user}.
 */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt hash of the user's password. The plaintext is never stored. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Stored as a string in the DB (e.g. "ADMIN", "USER") via {@code @Enumerated(EnumType.STRING)}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserRole role;

    /**
     * Soft-disable flag. When {@code false}, Spring Security rejects login attempts
     * for this account via {@link #isEnabled()} without deleting the row.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic locking version counter managed by JPA.
     * Prevents lost-update anomalies when two concurrent transactions modify the same row.
     */
    @Version
    private Integer version;

    // --- JPA lifecycle callbacks ---

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // --- UserDetails contract (required by Spring Security) ---

    /**
     * Returns the user's role wrapped in a {@link SimpleGrantedAuthority}.
     *
     * <p>Spring Security expects role names to be prefixed with {@code "ROLE_"} when
     * using {@code hasRole()} expressions. We add the prefix here so the enum values
     * ({@code ADMIN}, {@code USER}) stay clean in the rest of the codebase.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * Returns the BCrypt hash — Spring Security compares this against the submitted
     * password using the configured {@link org.springframework.security.crypto.password.PasswordEncoder}.
     */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /**
     * Returns the email address as the unique username. Spring Security uses this value
     * as the key for loading the user ({@code loadUserByUsername}) and as the JWT
     * {@code sub} claim.
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Returns {@code false} when the account has been soft-disabled ({@link #active} = false).
     * Spring Security will reject authentication attempts for disabled accounts.
     */
    @Override
    public boolean isEnabled() {
        return active;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }
}
