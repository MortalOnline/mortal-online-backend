package com.mortalonline.auth.service;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Reglas de negocio del registro (HU-01) con dependencias simuladas (Mockito):
 * validaciones, unicidad, hash de contraseña y envio del codigo de verificacion.
 * No levanta Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock EmailOtpRepository otps;
    @Mock JwtService jwt;
    @Mock MailService mail;

    // dependencias reales (logica pura, sin efectos externos)
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final LoginAttemptService attempts = new LoginAttemptService(5, 15);

    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, refreshTokens, otps, encoder, jwt, mail, attempts, 7, 10);
    }

    @Test
    void rechazaUsuarioDeMenosDeTresCaracteres() {
        Dtos.RegisterRequest req = new Dtos.RegisterRequest("ab", "a@b.com", "secreta1");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> auth.register(req));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void rechazaCorreoConFormatoInvalido() {
        Dtos.RegisterRequest req = new Dtos.RegisterRequest("juanito", "correo-sin-arroba", "secreta1");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> auth.register(req));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void rechazaContrasenaDeMenosDeSeisCaracteres() {
        Dtos.RegisterRequest req = new Dtos.RegisterRequest("juanito", "a@b.com", "123");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> auth.register(req));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void rechazaUsuarioYaExistente() {
        when(users.existsByUsername("juanito")).thenReturn(true);
        Dtos.RegisterRequest req = new Dtos.RegisterRequest("juanito", "a@b.com", "secreta1");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> auth.register(req));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void registroValidoHasheaLaContrasenaYEnviaElCodigo() {
        when(users.existsByUsername(anyString())).thenReturn(false);
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwt.createPending2faToken(any(), anyString())).thenReturn("pending.jwt.token");

        Dtos.RegisterRequest req = new Dtos.RegisterRequest("juanito", "juan@ejemplo.com", "secreta1");
        Dtos.RegisterResponse resp = auth.register(req);

        assertEquals("juanito", resp.username());
        assertNotNull(resp.pendingToken());

        // la contraseña se guarda HASHEADA (nunca en texto plano)
        verify(users).save(argThat(u ->
                !"secreta1".equals(u.getPasswordHash()) && encoder.matches("secreta1", u.getPasswordHash())));
        // se envia el codigo de verificacion al correo
        verify(mail).sendLoginCode(eq("juan@ejemplo.com"), eq("juanito"), anyString(), anyLong());
    }
}
