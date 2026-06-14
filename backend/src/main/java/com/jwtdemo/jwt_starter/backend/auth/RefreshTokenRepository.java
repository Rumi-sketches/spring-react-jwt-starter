package com.jwtdemo.jwt_starter.backend.auth;

import com.jwtdemo.jwt_starter.backend.users.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Usato nella reuse detection: revoca tutte le sessioni dell'utente. */
    List<RefreshToken> findAllByUser(User user);
}