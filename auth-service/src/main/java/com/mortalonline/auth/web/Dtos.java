package com.mortalonline.auth.web;

/** DTOs del API de autenticacion. */
public final class Dtos {

    private Dtos() {
    }

    public record RegisterRequest(String username, String email, String password) {
    }

    public record RegisterResponse(Long id, String username, String email) {
    }

    public record LoginRequest(String username, String password) {
    }

    /**
     * El login correcto NO emite JWT: se envio un codigo al correo registrado
     * (emailHint = correo enmascarado para mostrar en la pantalla de 2FA).
     */
    public record LoginResponse(boolean twoFactorRequired, String pendingToken, String emailHint) {
    }

    public record Verify2faRequest(String pendingToken, String code) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
    }

    public record MeResponse(Long id, String username, String email) {
    }

    /** Nombre publico de un usuario (para el scoreboard y las salas). */
    public record UserSummary(Long id, String username) {
    }
}
