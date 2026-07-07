package com.mortalonline.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Auth Service — unica fuente de verdad de identidad de Mortal Online.
 * Registro, login con contrasena (BCrypt), 2FA obligatorio (TOTP) y emision
 * de JWT + refresh tokens. Ningun otro servicio valida contrasenas ni emite
 * tokens.
 */
@SpringBootApplication
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
