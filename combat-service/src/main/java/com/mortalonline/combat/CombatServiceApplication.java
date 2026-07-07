package com.mortalonline.combat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Combat Service — sincronizacion de combate en tiempo real (cliente gordo:
 * el juego se procesa en el navegador; aqui SOLO se retransmiten inputs y se
 * valida/persiste el resultado).
 *
 * Rendimiento (escenario de calidad, < 100ms):
 *  - los inputs viajan por WebSocket (STOMP) y se retransmiten de inmediato;
 *  - el estado de la partida EN CURSO vive en memoria (mapa concurrente);
 *  - la base de datos solo se toca al FINALIZAR la partida.
 */
@SpringBootApplication
public class CombatServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CombatServiceApplication.class, args);
    }
}
