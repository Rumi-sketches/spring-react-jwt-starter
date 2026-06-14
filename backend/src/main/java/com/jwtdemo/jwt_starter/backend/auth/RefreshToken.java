package com.jwtdemo.jwt_starter.backend.auth;


import com.jwtdemo.jwt_starter.backend.users.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing one active (or recently revoked) refresh token session.
 *
 * <p><b>Security note</b>: the raw token string is <em>never</em> stored here.
 * Only its SHA-256 hex digest ({@link #tokenHash}) is persisted. If an attacker
 * obtains the contents of this table, they cannot reconstruct the original tokens
 * and therefore cannot impersonate users.
 *
 * <p>One user can have multiple rows (one per active device/session). All rows for
 * a user are revoked simultaneously when token reuse is detected.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The user who owns this session. Loaded lazily to avoid unnecessary joins. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * SHA-256 hex digest of the raw refresh token (64 lowercase hex characters).
     * Unique constraint ensures two different sessions cannot accidentally share the same hash.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Absolute timestamp after which this token must be rejected, regardless of revocation status. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * {@code true} if this token has been consumed (rotation), logged out, or flagged
     * as potentially stolen. A revoked token must never be accepted for a new access token.
     */
    @Column(nullable = false)
    private boolean revoked = false;

    /** Set automatically by {@link #onCreate()} — useful for audit and debugging. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Populates {@link #createdAt} before the first INSERT. */
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getCreatedAt() { return createdAt; }
}
