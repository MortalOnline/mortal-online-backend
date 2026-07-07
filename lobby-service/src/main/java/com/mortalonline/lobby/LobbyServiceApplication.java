package com.mortalonline.lobby;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Lobby Service — salas en tiempo real, chat y senalizacion WebRTC.
 * REST para crear/listar/unirse; STOMP sobre WebSocket para difusion de
 * salas, chat por sala y relay de senales WebRTC (el audio nunca pasa por
 * el backend). Soporta reconexion con ventana de 5 segundos.
 */
@SpringBootApplication
@EnableScheduling // barrido de la ventana de reconexion
public class LobbyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LobbyServiceApplication.class, args);
    }
}
