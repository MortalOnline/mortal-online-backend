package com.mortalonline.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "email"),
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(nullable = false, length = 120)
    private String email;

    /** Hash BCrypt — la contrasena NUNCA se almacena en texto plano. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    /** Secreto TOTP en Base32 para el 2FA (Google Authenticator y similares). */
    @Column(nullable = false, length = 64)
    private String totpSecret;

    /** Se activa cuando el usuario completa su primer 2FA con exito. */
    @Column(nullable = false)
    private boolean totpEnabled = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
    }

    public User(String username, String email, String passwordHash, String totpSecret) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.totpSecret = totpSecret;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getTotpSecret() { return totpSecret; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }
}
