package com.mortalonline.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Envio del codigo de verificacion por CORREO (2FA), con TRES vias en orden:
 *
 *  1) API HTTP en la nube (Brevo) — RECOMENDADA: viaja por HTTPS (puerto 443),
 *     que NUNCA esta bloqueado; las redes universitarias/corporativas suelen
 *     bloquear el SMTP saliente (587/465) como medida antispam.
 *  2) SMTP clasico (spring-mail) — si hay servidor configurado y la red lo permite.
 *  3) Respaldo: el codigo queda en el log del servicio (desarrollo/emergencia).
 *
 * Nota TLS: la conexion HTTPS valida siempre el certificado del servidor. Si en
 * una maquina de desarrollo un antivirus/proxy intercepta TLS, la CA corporativa
 * debe importarse al truststore de la JVM (nunca se desactiva la validacion).
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> smtpProvider;
    private final ObjectMapper json;
    private final String from;
    private final String apiKey;
    private final String apiUrl;
    private final HttpClient httpClient;

    public MailService(ObjectProvider<JavaMailSender> smtpProvider, ObjectMapper json,
                       @Value("${security.otp.mail-from:no-reply@mortalonline.gg}") String from,
                       @Value("${mail.api.key:}") String apiKey,
                       @Value("${mail.api.url:https://api.brevo.com/v3/smtp/email}") String apiUrl) {
        this.smtpProvider = smtpProvider;
        this.json = json;
        this.from = from;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiUrl = apiUrl;
        // cliente HTTP unico e inmutable (thread-safe); valida el certificado TLS
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    /** Codigo para VERIFICAR el correo de la cuenta (al registrarse). */
    public void sendLoginCode(String to, String username, String code, long ttlMinutes) {
        String text = "Hola " + username + ",\n\n"
                + "Tu codigo de verificacion es: " + code + "\n\n"
                + "Expira en " + ttlMinutes + " minutos. Si no creaste esta cuenta, ignora este correo.\n\n"
                + "— Mortal Online";
        send(to, username, "Tu codigo de verificacion de Mortal Online", text, "2FA", code);
    }

    /** Codigo para RECUPERAR la contrasena olvidada. */
    public void sendResetCode(String to, String username, String code, long ttlMinutes) {
        String text = "Hola " + username + ",\n\n"
                + "Tu codigo para cambiar la contrasena es: " + code + "\n\n"
                + "Expira en " + ttlMinutes + " minutos. Si no pediste cambiarla, ignora este correo:"
                + " tu contrasena actual sigue siendo valida.\n\n"
                + "— Mortal Online";
        send(to, username, "Recupera tu contrasena de Mortal Online", text, "RESET", code);
    }

    private void send(String to, String username, String subject, String text, String label, String code) {
        // 1) API HTTP en la nube (HTTPS 443: pasa cualquier firewall)
        if (!apiKey.isBlank()) {
            try {
                sendViaApi(to, username, subject, text);
                log.info("Codigo {} enviado por correo (API) a {}", label, to);
                return;
            } catch (IOException | RuntimeException e) {
                log.error("Fallo el envio por API a {}: {}", to, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // preservar el flag de interrupcion
                log.error("Envio por API interrumpido para {}", to);
            }
        }

        // 2) SMTP clasico (si esta configurado y la red no lo bloquea)
        JavaMailSender smtp = smtpProvider.getIfAvailable();
        if (smtp != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(text);
                smtp.send(message);
                log.info("Codigo {} enviado por correo (SMTP) a {}", label, to);
                return;
            } catch (RuntimeException e) {
                log.error("Fallo el envio por SMTP a {}: {}", to, e.getMessage());
            }
        }

        // 3) Respaldo: el operador puede leer el codigo del log (no bloquear el flujo)
        log.warn("Correo NO enviado. Codigo {} para {} <{}>: {}", label, username, to, code);
    }

    /** POST a la API transaccional de Brevo (o compatible via mail.api.url). */
    private void sendViaApi(String to, String username, String subject, String text)
            throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", "Mortal Online", "email", from),
                "to", List.of(Map.of("email", to, "name", username)),
                "subject", subject,
                "textContent", text);
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(8))
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " — " + response.body());
        }
    }
}
