package com.mortalonline.progress.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Todo endpoint REST valida el JWT antes de procesar (ademas del gateway:
 * defensa en profundidad). El principal expuesto es el userId.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtVerifier verifier;

    public JwtAuthFilter(JwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = verifier.verifyAccessToken(header.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ignored) {
                // token invalido: la cadena de seguridad respondera 401/403
            }
        }
        chain.doFilter(request, response);
    }
}
