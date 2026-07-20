package com.mortalonline.auth.service;

import com.mortalonline.auth.entity.EmailOtp;
import com.mortalonline.auth.entity.RefreshToken;
import com.mortalonline.auth.entity.User;
import com.mortalonline.auth.repository.EmailOtpRepository;
import com.mortalonline.auth.repository.RefreshTokenRepository;
import com.mortalonline.auth.repository.UserRepository;
import com.mortalonline.auth.web.Dtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Reglas de negocio de autenticacion (HU-01/HU-02): registro, login, 2FA por
 * correo, refresh de token y recuperacion de contrasena. Usa dependencias
 * simuladas (Mockito) para los repositorios y el correo; el hash BCrypt, el
 * JWT y el anti fuerza-bruta son reales. No levanta Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock EmailOtpRepository otps;
    @Mock MailService mail;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final JwtService jwt =
            new JwtService("mortal-online-dev-secret-0123456789abcdef0123456789abcdef", 60);
    private final LoginAttemptService attempts = new LoginAttemptService(5, 15);

    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, refreshTokens, otps, encoder, jwt, mail, attempts, 7, 10);
    }

    // ---- helpers ----
    private User user(Long id, String username, String email, String rawPassword, boolean verified) {
        User u = new User(username, email, encoder.encode(rawPassword));
        if (verified) u.markEmailVerified();
        setId(u, id);
        return u;
    }

    private static void setId(User u, Long id) {
        try {
            Field f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ============================ REGISTER ============================

    @Test
    void rechazaUsuarioDeMenosDeTresCaracteres() {
        var req = new Dtos.RegisterRequest("ab", "a@b.com", "secreta1");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.register(req));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void rechazaCorreoConFormatoInvalido() {
        var req = new Dtos.RegisterRequest("juanito", "correo-malo", "secreta1");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.register(req));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void rechazaContrasenaDeMenosDeSeisCaracteres() {
        var req = new Dtos.RegisterRequest("juanito", "a@b.com", "123");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.register(req));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void rechazaUsuarioYaExistente() {
        when(users.existsByUsername("juanito")).thenReturn(true);
        var req = new Dtos.RegisterRequest("juanito", "a@b.com", "secreta1");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.register(req));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void rechazaCorreoYaRegistrado() {
        when(users.existsByUsername("juanito")).thenReturn(false);
        when(users.existsByEmail("a@b.com")).thenReturn(true);
        var req = new Dtos.RegisterRequest("juanito", "a@b.com", "secreta1");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.register(req));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void registroValidoHasheaLaContrasenaYEnviaElCodigo() {
        when(users.existsByUsername(anyString())).thenReturn(false);
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = auth.register(new Dtos.RegisterRequest("juanito", "juan@ejemplo.com", "secreta1"));

        assertEquals("juanito", resp.username());
        assertNotNull(resp.pendingToken());
        verify(users).save(argThat(u ->
                !"secreta1".equals(u.getPasswordHash()) && encoder.matches("secreta1", u.getPasswordHash())));
        verify(mail).sendLoginCode(eq("juan@ejemplo.com"), eq("juanito"), anyString(), anyLong());
    }

    // ============================= LOGIN =============================

    @Test
    void loginConCorreoVerificadoDevuelveJwtDirecto() {
        when(users.findByUsername("juanito")).thenReturn(Optional.of(user(1L, "juanito", "a@b.com", "secreta1", true)));

        var resp = auth.login(new Dtos.LoginRequest("juanito", "secreta1"));

        assertFalse(resp.twoFactorRequired());
        assertNotNull(resp.tokens());
        assertNotNull(resp.tokens().accessToken());
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void loginSinVerificarReenviaCodigo() {
        when(users.findByUsername("juanito")).thenReturn(Optional.of(user(1L, "juanito", "a@b.com", "secreta1", false)));

        var resp = auth.login(new Dtos.LoginRequest("juanito", "secreta1"));

        assertTrue(resp.twoFactorRequired());
        assertNotNull(resp.pendingToken());
        assertNull(resp.tokens());
        verify(mail).sendLoginCode(eq("a@b.com"), eq("juanito"), anyString(), anyLong());
    }

    @Test
    void loginConContrasenaIncorrectaLanza401() {
        when(users.findByUsername("juanito")).thenReturn(Optional.of(user(1L, "juanito", "a@b.com", "secreta1", true)));
        var req = new Dtos.LoginRequest("juanito", "otra-clave");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.login(req));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void loginDeUsuarioInexistenteLanza401() {
        when(users.findByUsername("fantasma")).thenReturn(Optional.empty());
        var req = new Dtos.LoginRequest("fantasma", "secreta1");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.login(req));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void loginSeBloqueaTrasCincoIntentosFallidos() {
        when(users.findByUsername("juanito")).thenReturn(Optional.of(user(1L, "juanito", "a@b.com", "secreta1", true)));
        var mala = new Dtos.LoginRequest("juanito", "mala");
        for (int i = 0; i < 5; i++) {
            assertThrows(ResponseStatusException.class, () -> auth.login(mala));
        }
        var buena = new Dtos.LoginRequest("juanito", "secreta1");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.login(buena));
        assertEquals(429, ex.getStatusCode().value()); // bloqueada aunque la clave sea correcta
    }

    // ============================ VERIFY 2FA ============================

    @Test
    void verify2faValidoMarcaVerificadoYEmiteJwt() {
        User u = user(7L, "ana", "ana@b.com", "secreta1", false);
        String pending = jwt.createPending2faToken(7L, "ana");
        when(users.findById(7L)).thenReturn(Optional.of(u));
        when(otps.findFirstByUserIdAndPurposeOrderByIdDesc(7L, EmailOtp.PURPOSE_VERIFY))
                .thenReturn(Optional.of(new EmailOtp(7L, "123456", EmailOtp.PURPOSE_VERIFY, Instant.now().plusSeconds(300))));

        var tokens = auth.verify2fa(new Dtos.Verify2faRequest(pending, "123456"));

        assertNotNull(tokens.accessToken());
        assertTrue(u.isEmailVerified());
        verify(otps).delete(any(EmailOtp.class));
        verify(users).save(u);
    }

    @Test
    void verify2faConCodigoIncorrectoLanza401YRegistraIntento() {
        User u = user(7L, "ana", "ana@b.com", "secreta1", false);
        String pending = jwt.createPending2faToken(7L, "ana");
        EmailOtp otp = new EmailOtp(7L, "123456", EmailOtp.PURPOSE_VERIFY, Instant.now().plusSeconds(300));
        when(users.findById(7L)).thenReturn(Optional.of(u));
        when(otps.findFirstByUserIdAndPurposeOrderByIdDesc(7L, EmailOtp.PURPOSE_VERIFY)).thenReturn(Optional.of(otp));

        var req = new Dtos.Verify2faRequest(pending, "999999");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.verify2fa(req));
        assertEquals(401, ex.getStatusCode().value());
        verify(otps).save(otp); // se guarda el intento fallido
    }

    @Test
    void verify2faSinCodigoVigenteLanza401() {
        User u = user(7L, "ana", "ana@b.com", "secreta1", false);
        String pending = jwt.createPending2faToken(7L, "ana");
        when(users.findById(7L)).thenReturn(Optional.of(u));
        when(otps.findFirstByUserIdAndPurposeOrderByIdDesc(7L, EmailOtp.PURPOSE_VERIFY)).thenReturn(Optional.empty());

        var req = new Dtos.Verify2faRequest(pending, "123456");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.verify2fa(req));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void verify2faConCodigoExpiradoLanza401() {
        User u = user(7L, "ana", "ana@b.com", "secreta1", false);
        String pending = jwt.createPending2faToken(7L, "ana");
        when(users.findById(7L)).thenReturn(Optional.of(u));
        when(otps.findFirstByUserIdAndPurposeOrderByIdDesc(7L, EmailOtp.PURPOSE_VERIFY))
                .thenReturn(Optional.of(new EmailOtp(7L, "123456", EmailOtp.PURPOSE_VERIFY, Instant.now().minusSeconds(1))));

        var req = new Dtos.Verify2faRequest(pending, "123456");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.verify2fa(req));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void verify2faRechazaTokenQueNoEsDe2fa() {
        String accessToken = jwt.createAccessToken(7L, "ana"); // scope "access", no "2fa"
        var req = new Dtos.Verify2faRequest(accessToken, "123456");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.verify2fa(req));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void verify2faConTokenBasuraLanza401() {
        var req = new Dtos.Verify2faRequest("token.invalido", "123456");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.verify2fa(req));
        assertEquals(401, ex.getStatusCode().value());
    }

    // ============================= REFRESH =============================

    @Test
    void refreshValidoRotaElToken() {
        RefreshToken stored = new RefreshToken(5L, "rt-viejo", Instant.now().plusSeconds(3600));
        when(refreshTokens.findByToken("rt-viejo")).thenReturn(Optional.of(stored));
        when(users.findById(5L)).thenReturn(Optional.of(user(5L, "leo", "leo@b.com", "secreta1", true)));

        var tokens = auth.refresh(new Dtos.RefreshRequest("rt-viejo"));

        assertNotNull(tokens.accessToken());
        verify(refreshTokens).delete(stored);          // rotacion: el viejo se elimina
        verify(refreshTokens).save(any(RefreshToken.class)); // y se emite uno nuevo
    }

    @Test
    void refreshExpiradoLanza401YSeElimina() {
        RefreshToken stored = new RefreshToken(5L, "rt-exp", Instant.now().minusSeconds(1));
        when(refreshTokens.findByToken("rt-exp")).thenReturn(Optional.of(stored));

        var req = new Dtos.RefreshRequest("rt-exp");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.refresh(req));
        assertEquals(401, ex.getStatusCode().value());
        verify(refreshTokens).delete(stored);
    }

    @Test
    void refreshInexistenteLanza401() {
        when(refreshTokens.findByToken("no-existe")).thenReturn(Optional.empty());
        var req = new Dtos.RefreshRequest("no-existe");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.refresh(req));
        assertEquals(401, ex.getStatusCode().value());
    }

    // ========================= FORGOT / RESET =========================

    @Test
    void forgotDeCuentaExistenteEnviaCodigoDeReset() {
        when(users.findByUsername("leo")).thenReturn(Optional.of(user(5L, "leo", "leo@b.com", "secreta1", true)));

        var resp = auth.forgotPassword(new Dtos.ForgotPasswordRequest("leo"));

        assertNotNull(resp.message());
        verify(mail).sendResetCode(eq("leo@b.com"), eq("leo"), anyString(), anyLong());
    }

    @Test
    void forgotDeCuentaInexistenteNoEnviaPeroRespondeIgual() {
        when(users.findByUsername("nadie")).thenReturn(Optional.empty());
        when(users.findByEmail("nadie")).thenReturn(Optional.empty());

        var resp = auth.forgotPassword(new Dtos.ForgotPasswordRequest("nadie"));

        assertNotNull(resp.message()); // misma respuesta generica (no revela si existe)
        verify(mail, never()).sendResetCode(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void resetValidoCambiaLaContrasenaYCierraSesiones() {
        User u = user(5L, "leo", "leo@b.com", "vieja123", true);
        when(users.findByUsername("leo")).thenReturn(Optional.of(u));
        when(otps.findFirstByUserIdAndPurposeOrderByIdDesc(5L, EmailOtp.PURPOSE_RESET))
                .thenReturn(Optional.of(new EmailOtp(5L, "654321", EmailOtp.PURPOSE_RESET, Instant.now().plusSeconds(300))));

        auth.resetPassword(new Dtos.ResetPasswordRequest("leo", "654321", "nueva123"));

        verify(users).save(argThat(saved -> encoder.matches("nueva123", saved.getPasswordHash())));
        verify(refreshTokens).deleteByUserId(5L); // cierra todas las sesiones
    }

    @Test
    void resetConContrasenaCortaLanza400() {
        var req = new Dtos.ResetPasswordRequest("leo", "654321", "123");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.resetPassword(req));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void resetConCodigoIncorrectoLanza401() {
        User u = user(5L, "leo", "leo@b.com", "vieja123", true);
        when(users.findByUsername("leo")).thenReturn(Optional.of(u));
        when(otps.findFirstByUserIdAndPurposeOrderByIdDesc(5L, EmailOtp.PURPOSE_RESET))
                .thenReturn(Optional.of(new EmailOtp(5L, "654321", EmailOtp.PURPOSE_RESET, Instant.now().plusSeconds(300))));

        var req = new Dtos.ResetPasswordRequest("leo", "000000", "nueva123");
        var ex = assertThrows(ResponseStatusException.class, () -> auth.resetPassword(req));
        assertEquals(401, ex.getStatusCode().value());
    }

    // ============================ ME / USERS ============================

    @Test
    void meDevuelveElPerfil() {
        when(users.findById(9L)).thenReturn(Optional.of(user(9L, "kira", "kira@b.com", "secreta1", true)));
        var me = auth.me(9L);
        assertEquals("kira", me.username());
        assertEquals("kira@b.com", me.email());
    }

    @Test
    void meDeUsuarioInexistenteLanza404() {
        when(users.findById(99L)).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () -> auth.me(99L));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void usersByIdsDevuelveNombresPublicos() {
        when(users.findAllById(List.of(1L, 2L))).thenReturn(List.of(
                user(1L, "a", "a@b.com", "secreta1", true),
                user(2L, "b", "b@b.com", "secreta1", true)));
        var list = auth.usersByIds(List.of(1L, 2L));
        assertEquals(2, list.size());
    }

    @Test
    void usersByIdsConListaVaciaDevuelveVacio() {
        assertTrue(auth.usersByIds(List.of()).isEmpty());
        verifyNoInteractions(users);
    }
}
