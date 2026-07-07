package com.mortalonline.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Filtro global de seguridad: valida el JWT (emitido por Auth Service) en el
 * header Authorization para TODAS las rutas, excepto el flujo publico de
 * autenticacion. Si el token es valido, propaga la identidad al microservicio
 * en los headers X-User-Id / X-Username.
 */
@Component
public class JwtGlobalFilter implements GlobalFilter, Ordered {

    /**
     * Rutas publicas:
     *  - registro / login / verify-2fa: flujo de autenticacion.
     *  - refresh: usa el refresh token en el body (el access token ya expiro).
     *  - /ws/**: el handshake HTTP del WebSocket no lleva header Authorization;
     *    la autenticacion ocurre en el frame CONNECT de STOMP dentro de cada
     *    servicio (JwtChannelInterceptor).
     */
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/auth/register", "/auth/login", "/auth/verify-2fa", "/auth/refresh", "/ws/");

    private final SecretKey key;

    public JwtGlobalFilter(@Value("${security.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(header.substring(7)).getPayload();
            if (!"access".equals(claims.get("scope", String.class))) {
                return unauthorized(exchange); // un token de 2FA pendiente no autoriza nada mas
            }
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", claims.getSubject())
                    .header("X-Username", claims.get("username", String.class))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            return unauthorized(exchange);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100; // despues del rate limiter, antes del enrutamiento
    }
}
