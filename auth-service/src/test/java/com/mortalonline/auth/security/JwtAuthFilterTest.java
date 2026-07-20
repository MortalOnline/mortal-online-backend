package com.mortalonline.auth.security;

import com.mortalonline.auth.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Filtro JWT de los endpoints protegidos: publica el userId como principal solo
 * con un token de acceso valido; ignora silenciosamente lo demas.
 */
class JwtAuthFilterTest {

    private final JwtService jwt =
            new JwtService("mortal-online-dev-secret-0123456789abcdef0123456789abcdef", 60);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwt);

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private Authentication runWith(String authorizationHeader) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (authorizationHeader != null) req.addHeader("Authorization", authorizationHeader);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        assertSame(req, chain.getRequest()); // la cadena siempre continua
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void tokenDeAccesoValidoEstableceLaAutenticacion() throws Exception {
        Authentication authn = runWith("Bearer " + jwt.createAccessToken(9L, "kira"));
        assertNotNull(authn);
        assertEquals("9", authn.getName());
    }

    @Test
    void sinCabeceraAuthorizationNoAutentica() throws Exception {
        assertNull(runWith(null));
    }

    @Test
    void tokenDeScope2faNoAutentica() throws Exception {
        // el token pendiente de 2FA no debe dar acceso a los endpoints protegidos
        assertNull(runWith("Bearer " + jwt.createPending2faToken(9L, "kira")));
    }

    @Test
    void tokenInvalidoNoAutenticaYNoRompeLaCadena() throws Exception {
        assertNull(runWith("Bearer token-corrupto"));
    }
}
