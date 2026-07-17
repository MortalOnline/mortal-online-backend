package com.mortalonline.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Codigo de un solo uso enviado por CORREO. Sirve para dos propositos
 * (columna purpose) y NUNCA son intercambiables entre si:
 *   VERIFY — verificar el correo al registrarse (una sola vez por cuenta)
 *   RESET  — recuperar la contrasena olvidada
 * Un codigo por usuario y proposito a la vez, con expiracion y limite de intentos.
 */
@Entity
@Table(name = "email_otps", indexes = @Index(columnList = "userId"))
public class EmailOtp {

    public static final String PURPOSE_VERIFY = "VERIFY";
    public static final String PURPOSE_RESET = "RESET";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false, length = 10)
    @org.hibernate.annotations.ColumnDefault("'VERIFY'")
    private String purpose = PURPOSE_VERIFY;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    protected EmailOtp() {
    }

    public EmailOtp(Long userId, String code, String purpose, Instant expiresAt) {
        this.userId = userId;
        this.code = code;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getCode() { return code; }
    public String getPurpose() { return purpose; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }

    public void registerFailedAttempt() {
        this.attempts++;
    }
}
