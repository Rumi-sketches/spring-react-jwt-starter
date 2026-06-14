// LoginRequest.java
package com.jwtdemo.jwt_starter.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Email obbligatoria")
    @Email(message = "Formato email non valido")
    private String email;

    @NotBlank(message = "Password obbligatoria")
    private String password;

    public String getEmail() { return email; }
    public String getPassword() { return password; }
}