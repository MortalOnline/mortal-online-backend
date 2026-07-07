package com.mortalonline.progress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Progress Service — scoreboard global, racha de victorias y desbloqueo de
 * personajes (Reptile a las 3 victorias seguidas). Los resultados SOLO se
 * aceptan desde Combat Service (token interno), nunca desde el cliente.
 */
@SpringBootApplication
public class ProgressServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProgressServiceApplication.class, args);
    }
}
