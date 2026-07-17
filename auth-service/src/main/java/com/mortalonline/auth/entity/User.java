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

    /**
     * Correo verificado con el codigo enviado AL REGISTRARSE. La verificacion
     * se hace UNA sola vez; despues el login es directo (usuario + contrasena).
     */
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("false")
    private boolean emailVerified = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant getCreatedAt() { return createdAt; }

    public void markEmailVerified() { this.emailVerified = true; }
    public void changePassword(String newHash) { this.passwordHash = newHash; }
}
