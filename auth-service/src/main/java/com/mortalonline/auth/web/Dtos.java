package com.mortalonline.auth.web;

/** DTOs del API de autenticacion. */
public final class Dtos {

    private Dtos() {
    }

    public record RegisterRequest(String username, String email, String password) {
    }

    /** El secreto TOTP y su URL otpauth se entregan solo en el registro. */
    public record RegisterResponse(Long id, String username, String totpSecret, String otpauthUrl) {
    }

    public record LoginRequest(String username, String password) {
    }

    /** El login correcto NO emite JWT: pide completar el 2FA. */
    public record LoginResponse(boolean twoFactorRequired, String pendingToken) {
    }

    public record Verify2faRequest(String pendingToken, String code) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
    }

    public record MeResponse(Long id, String username, String email, boolean totpEnabled) {
    }
}
