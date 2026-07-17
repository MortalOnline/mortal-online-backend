package com.mortalonline.auth.web;

/** DTOs del API de autenticacion. */
public final class Dtos {

    private Dtos() {
    }

    public record RegisterRequest(String username, String email, String password) {
    }

    /**
     * El registro deja la cuenta pendiente de VERIFICAR EL CORREO: se envia un
     * codigo y el cliente pasa directo a la pantalla de verificacion con el
     * pendingToken (emailHint = correo enmascarado para mostrar en pantalla).
     */
    public record RegisterResponse(Long id, String username, String email,
                                   String pendingToken, String emailHint) {
    }

    public record LoginRequest(String username, String password) {
    }

    /**
     * Cuenta ya verificada -> twoFactorRequired=false y tokens emitidos aqui
     * mismo (login normal). Cuenta sin verificar -> se envia el codigo al
     * correo y el cliente debe llamar a verify-2fa con el pendingToken.
     */
    public record LoginResponse(boolean twoFactorRequired, String pendingToken, String emailHint,
                                TokenResponse tokens) {
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

    // ---- Recuperacion de contrasena ----

    public record ForgotPasswordRequest(String usernameOrEmail) {
    }

    public record ResetPasswordRequest(String usernameOrEmail, String code, String newPassword) {
    }

    /** Respuesta generica (no revela si la cuenta existe o no). */
    public record MessageResponse(String message) {
    }
}
