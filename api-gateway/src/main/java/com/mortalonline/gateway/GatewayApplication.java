package com.mortalonline.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * API Gateway de Mortal Online (Spring Cloud Gateway).
 * Punto de entrada unico detras de Nginx: enruta hacia los 4 microservicios,
 * valida el JWT antes de enrutar y aplica rate limiting por IP.
 */
@SpringBootApplication
@EnableScheduling // limpieza periodica del rate limiter en memoria
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
