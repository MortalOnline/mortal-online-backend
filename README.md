# Mortal Online — Backend (Microservicios)

Backend de **Mortal Online** (recreación web de Mortal Kombat 1). Originalmente
planeado como monolito, migrado a **microservicios** manteniendo el principio de
**cliente gordo**: el juego (físicas, hitboxes, animaciones) corre 100% en el
navegador con Phaser; el backend **no procesa juego** — solo sincroniza inputs,
valida resultados y gestiona sesiones, salas y progreso.

## Arquitectura

```
Cliente (Phaser + React)
   → HTTPS/WSS → Nginx (Load Balancer)
   → HTTP/WS  → API Gateway (Spring Cloud Gateway)
       ├── /auth/**      → Auth Service      (JWT, 2FA TOTP, BCrypt)     → Auth DB
       ├── /lobby/**     → Lobby Service     (salas, chat, señal WebRTC) → Lobby DB
       ├── /combat/**    → Combat Service    (sync combate, WebSocket)   → Combat DB
       └── /progress/**  → Progress Service  (scoreboard, rachas)        → Progress DB
```

- **Comunicación entre servicios:** REST **síncrono** (WebClient). Sin brokers
  de mensajes. Al terminar una partida, Combat Service llama directamente a
  `POST /progress/matches-completed` de Progress Service.
- **Base de datos:** PostgreSQL, **una por microservicio** (nunca compartida),
  cada una con **réplica de lectura** en docker-compose (replicación básica;
  en producción sería un servicio gestionado).
- **Tiempo real:** Spring WebSocket + STOMP (lobby y combate). El input de
  combate se retransmite de inmediato **sin tocar base de datos** (estado de
  la partida en curso en un mapa concurrente en memoria; se persiste solo al
  finalizar) — objetivo <100ms normal / <150ms bajo carga.
- **Señalización WebRTC:** dentro de Lobby Service, relay puro de ofertas SDP,
  respuestas y candidatos ICE por `/topic/rooms/{id}/webrtc-signal`. El audio
  nunca pasa por el backend.
- **Reconexión:** si un jugador pierde el WebSocket del lobby, conserva su
  sala durante **5 segundos** antes de ser expulsado.

## Seguridad

- **Auth Service es la única fuente de identidad**: solo él valida contraseñas
  (BCrypt, 12 rounds) y emite JWT. El **2FA por correo es obligatorio**: el
  login con contraseña correcta envía un **código de 6 dígitos al correo
  registrado** (expira en 10 min, un solo uso, máx. 5 intentos) y devuelve un
  token temporal (scope `2fa`); el JWT de acceso solo se emite tras
  `verify-2fa` con el código correcto.
- El **Gateway valida el JWT** en todas las rutas excepto
  `/auth/register|login|verify-2fa|refresh` (y el handshake `/ws/**`, que se
  autentica en el frame CONNECT de STOMP). Cada microservicio **vuelve a
  validar** el JWT (defensa en profundidad).
- Los resultados de partida **solo** los acepta Progress Service si vienen de
  Combat Service (header `X-Internal-Token`); el cliente nunca puede reportar
  victorias directamente.
- Rate limiting por IP en el Gateway (en memoria; en producción,
  `RequestRateLimiter` con Redis).

## Cómo levantar todo

Requisitos: Docker + Docker Compose.

```bash
docker-compose up --build
```

Levanta: 4 PostgreSQL (+ 4 réplicas), los 4 microservicios, el API Gateway
(`:8080`) y Nginx (`:80`). Todo el tráfico del cliente entra por Nginx:
`http://localhost/`.

> En producción defina `JWT_SECRET` e `INTERNAL_API_TOKEN` como variables de
> entorno (los valores por defecto son SOLO de desarrollo).

### Correo (código 2FA)

Para que el código de verificación llegue por correo, configure SMTP en el
`auth-service` (ver líneas comentadas en `docker-compose.yml`). Ejemplo con
Gmail (requiere una *app password*):

```
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=micuenta@gmail.com
SPRING_MAIL_PASSWORD=<app-password>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

**Sin SMTP configurado** (desarrollo), el código se escribe en el **log del
auth-service** en lugar de enviarse — el flujo completo se puede probar igual.

## Probar el flujo completo

### 1. Registro

```bash
curl -s -X POST http://localhost/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"subzero","email":"subzero@earthrealm.com","password":"secreta1"}'
```

### 2. Login (paso 1: contraseña)

```bash
curl -s -X POST http://localhost/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"subzero","password":"secreta1"}'
```

Respuesta: `{"twoFactorRequired":true,"pendingToken":"...","emailHint":"s•••@earthrealm.com"}`
— todavía **no** hay JWT. En ese momento llega el **código de 6 dígitos al
correo registrado** (sin SMTP configurado, búsquelo en el log del
auth-service: `docker-compose logs auth-service | grep "Codigo 2FA"`).

### 3. Login (paso 2: código del correo)

```bash
curl -s -X POST http://localhost/auth/verify-2fa \
  -H "Content-Type: application/json" \
  -d '{"pendingToken":"<pendingToken>","code":"<código del correo>"}'
```

Respuesta: `{"accessToken":"...","refreshToken":"..."}`. Exporte el token:

```bash
TOKEN=<accessToken>
```

### 4. Crear sala y unirse

```bash
curl -s -X POST http://localhost/lobby/rooms \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Sala de Sub-Zero","mode":"VERSUS_NORMAL"}'

curl -s http://localhost/lobby/rooms -H "Authorization: Bearer $TOKEN"

# El segundo jugador (con su propio token):
curl -s -X POST http://localhost/lobby/rooms/1/join -H "Authorization: Bearer $TOKEN2"
```

Chat y señalización WebRTC van por WebSocket STOMP en `ws://localhost/ws/lobby`
(header `Authorization: Bearer <token>` en el CONNECT):
publicar en `/app/rooms/{id}/chat`, escuchar `/topic/rooms/{id}/chat` y
`/topic/rooms/{id}/webrtc-signal`.

### 5. Simular una partida

```bash
# Registrar la partida en memoria (jugadores 1 y 2, sala 1)
curl -s -X POST http://localhost/combat/matches \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"roomId":1,"player1Id":1,"player2Id":2}'
```

Durante el combate, los inputs viajan por `ws://localhost/ws/combat`
(STOMP): publicar en `/app/combat/1/input`, escuchar
`/topic/combat/1/input`. Rondas y fin de partida:
`/app/combat/1/round-end` (`{"roundNumber":1,"winnerId":1,"durationSeconds":42}`)
y `/app/combat/1/match-end` (`{"winnerId":1}`). Al recibir `match-end`,
Combat persiste la partida y notifica a Progress por REST síncrono.

Consultar la partida persistida:

```bash
curl -s http://localhost/combat/matches/1 -H "Authorization: Bearer $TOKEN"
```

### 6. Scoreboard y progreso

```bash
curl -s http://localhost/progress/scoreboard -H "Authorization: Bearer $TOKEN"
curl -s http://localhost/progress/stats/1   -H "Authorization: Bearer $TOKEN"
```

Con 3 victorias seguidas, `stats` muestra `"reptileUnlocked": true`.

## Estructura

```
mortal-online-backend/
  docker-compose.yml        # 4 servicios + gateway + nginx + 4 BD (+réplicas)
  nginx/nginx.conf          # LB con upstream x2 y soporte WebSocket
  api-gateway/              # Spring Cloud Gateway: rutas, JWT, rate limit
  auth-service/             # :8081 — usuarios, BCrypt, TOTP, JWT, refresh
  lobby-service/            # :8082 — salas, chat, WebRTC signaling, reconexión
  combat-service/           # :8083 — relay de inputs, resultado de partida
  progress-service/         # :8084 — scoreboard, rachas, desbloqueos
```

Cada servicio es un proyecto Maven independiente (Spring Boot 3.3, Java 17)
con su propio `Dockerfile` multi-stage.
