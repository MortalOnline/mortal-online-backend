package com.mortalonline.auth.web;

import com.mortalonline.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * El controller es una capa fina que delega en AuthService. Se verifica que
 * cada endpoint invoca al servicio y devuelve su resultado tal cual.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock AuthService auth;
    @InjectMocks AuthController controller;

    @Test
    void registerDelegaEnElServicio() {
        var req = new Dtos.RegisterRequest("u", "u@x.com", "secreta1");
        var resp = new Dtos.RegisterResponse(1L, "u", "u@x.com", "pt", "u•••@x.com");
        when(auth.register(req)).thenReturn(resp);
        assertSame(resp, controller.register(req));
    }

    @Test
    void loginDelegaEnElServicio() {
        var req = new Dtos.LoginRequest("u", "secreta1");
        var resp = new Dtos.LoginResponse(false, null, null, null);
        when(auth.login(req)).thenReturn(resp);
        assertSame(resp, controller.login(req));
    }

    @Test
    void verify2faDelegaEnElServicio() {
        var req = new Dtos.Verify2faRequest("pt", "123456");
        var resp = new Dtos.TokenResponse("at", "rt", "Bearer", 3600);
        when(auth.verify2fa(req)).thenReturn(resp);
        assertSame(resp, controller.verify2fa(req));
    }

    @Test
    void refreshDelegaEnElServicio() {
        var req = new Dtos.RefreshRequest("rt");
        var resp = new Dtos.TokenResponse("at2", "rt2", "Bearer", 3600);
        when(auth.refresh(req)).thenReturn(resp);
        assertSame(resp, controller.refresh(req));
    }

    @Test
    void forgotPasswordDelegaEnElServicio() {
        var req = new Dtos.ForgotPasswordRequest("u");
        var resp = new Dtos.MessageResponse("ok");
        when(auth.forgotPassword(req)).thenReturn(resp);
        assertSame(resp, controller.forgotPassword(req));
    }

    @Test
    void resetPasswordDelegaEnElServicio() {
        var req = new Dtos.ResetPasswordRequest("u", "123456", "nueva123");
        var resp = new Dtos.MessageResponse("ok");
        when(auth.resetPassword(req)).thenReturn(resp);
        assertSame(resp, controller.resetPassword(req));
    }

    @Test
    void meTomaElIdDelPrincipalAutenticado() {
        Principal principal = () -> "42";
        var resp = new Dtos.MeResponse(42L, "u", "u@x.com");
        when(auth.me(42L)).thenReturn(resp);
        assertSame(resp, controller.me(principal));
    }

    @Test
    void usersDelegaEnElServicio() {
        var resp = List.of(new Dtos.UserSummary(1L, "u"));
        when(auth.usersByIds(List.of(1L))).thenReturn(resp);
        assertSame(resp, controller.users(List.of(1L)));
    }
}
