package com.mortalonline.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Codigo de verificacion de dos pasos enviado por CORREO al iniciar sesion.
 * Un codigo por usuario a la vez, con expiracion y limite de intentos.
 */
@Entity
@Table(name = "email_otps", indexes = @Index(columnList = "userId"))
public class EmailOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    protected EmailOtp() {
    }

    public EmailOtp(Long userId, String code, Instant expiresAt) {
        this.userId = userId;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getCode() { return code; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }

    public void registerFailedAttempt() {
        this.attempts++;
    }
}
