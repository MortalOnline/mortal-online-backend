package com.mortalonline.combat.ws;

import com.mortalonline.combat.security.JwtVerifier;
import io.jsonwebtoken.Claims;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Autenticacion del WebSocket: el frame CONNECT de STOMP debe traer el header
 * Authorization: Bearer <jwt>. Sin token valido, la conexion se rechaza.
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtVerifier verifier;

    public JwtChannelInterceptor(JwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new IllegalArgumentException("Falta el token de acceso en el CONNECT");
            }
            try {
                Claims claims = verifier.verifyAccessToken(header.substring(7));
                accessor.setUser(new StompPrincipal(claims.getSubject(), claims.get("username", String.class)));
            } catch (Exception e) {
                throw new IllegalArgumentException("Token de acceso invalido");
            }
        }
        return message;
    }
}
