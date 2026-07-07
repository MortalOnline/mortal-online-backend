package com.mortalonline.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting basico EN MEMORIA por IP (ventana fija de 1 segundo), para no
 * requerir Redis en esta fase. En produccion, con varias instancias del
 * gateway, se reemplazaria por el RequestRateLimiter de Spring Cloud Gateway
 * respaldado en Redis (limite compartido entre instancias).
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final int requestsPerSecond;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${rate-limit.requests-per-second:50}") int requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = clientIp(exchange);
        long second = System.currentTimeMillis() / 1000;
        Window w = windows.compute(ip, (k, cur) ->
                (cur == null || cur.second != second) ? new Window(second) : cur);
        if (w.count.incrementAndGet() > requestsPerSecond) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private String clientIp(ServerWebExchange exchange) {
        // detras de Nginx llega en X-Forwarded-For; sin proxy, la remota
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    /** Limpieza periodica de ventanas viejas para no acumular IPs. */
    @Scheduled(fixedDelay = 60_000)
    public void evictStale() {
        long second = System.currentTimeMillis() / 1000;
        windows.entrySet().removeIf(e -> e.getValue().second < second - 10);
    }

    @Override
    public int getOrder() {
        return -200; // primero: rechazar exceso de trafico antes de validar JWT
    }

    private static final class Window {
        final long second;
        final AtomicInteger count = new AtomicInteger();

        Window(long second) {
            this.second = second;
        }
    }
}
