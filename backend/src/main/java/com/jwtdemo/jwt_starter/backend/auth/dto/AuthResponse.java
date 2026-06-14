// AuthResponse.java
package com.jwtdemo.jwt_starter.backend.auth.dto;

/** Risposta del login e del refresh: contiene solo l'access token.
 *  Il refresh token viaggia nel cookie httpOnly, non nel body. */
public class AuthResponse {

    private final String accessToken;
    private final String tokenType = "Bearer";

    public AuthResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
}