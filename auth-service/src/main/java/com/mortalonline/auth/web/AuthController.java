package com.mortalonline.auth.web;

import com.mortalonline.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.RegisterResponse register(@RequestBody Dtos.RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public Dtos.LoginResponse login(@RequestBody Dtos.LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/verify-2fa")
    public Dtos.TokenResponse verify2fa(@RequestBody Dtos.Verify2faRequest req) {
        return auth.verify2fa(req);
    }

    @PostMapping("/refresh")
    public Dtos.TokenResponse refresh(@RequestBody Dtos.RefreshRequest req) {
        return auth.refresh(req);
    }

    /** Recuperacion de contrasena: envia un codigo al correo de la cuenta. */
    @PostMapping("/forgot-password")
    public Dtos.MessageResponse forgotPassword(@RequestBody Dtos.ForgotPasswordRequest req) {
        return auth.forgotPassword(req);
    }

    /** Cambia la contrasena con el codigo recibido por correo. */
    @PostMapping("/reset-password")
    public Dtos.MessageResponse resetPassword(@RequestBody Dtos.ResetPasswordRequest req) {
        return auth.resetPassword(req);
    }

    /** Datos del usuario autenticado (requiere JWT valido con scope access). */
    @GetMapping("/me")
    public Dtos.MeResponse me(Principal principal) {
        return auth.me(Long.valueOf(principal.getName()));
    }

    /** Nombres publicos por id, ej. /auth/users?ids=1,2,3 (requiere JWT). */
    @GetMapping("/users")
    public java.util.List<Dtos.UserSummary> users(@RequestParam("ids") java.util.List<Long> ids) {
        return auth.usersByIds(ids);
    }
}
