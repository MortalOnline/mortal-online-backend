package com.mortalonline.lobby.ws;

import java.security.Principal;

/** Identidad del usuario dentro de la sesion STOMP (name = userId). */
public class StompPrincipal implements Principal {

    private final String userId;
    private final String username;

    public StompPrincipal(String userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    @Override
    public String getName() {
        return userId;
    }

    public Long userId() {
        return Long.valueOf(userId);
    }

    public String username() {
        return username;
    }
}
