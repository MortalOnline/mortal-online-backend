package com.mortalonline.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
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
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> smtpProvider;
    private final ObjectMapper json;
    private final String from;
    private final String apiKey;
    private final String apiUrl;
    private final boolean insecureTrust;
    private volatile HttpClient httpClient;

    public MailService(ObjectProvider<JavaMailSender> smtpProvider, ObjectMapper json,
                       @Value("${security.otp.mail-from:no-reply@mortalonline.gg}") String from,
                       @Value("${mail.api.key:}") String apiKey,
                       @Value("${mail.api.url:https://api.brevo.com/v3/smtp/email}") String apiUrl,
                       @Value("${mail.api.insecure-trust:false}") boolean insecureTrust) {
        this.smtpProvider = smtpProvider;
        this.json = json;
        this.from = from;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiUrl = apiUrl;
        this.insecureTrust = insecureTrust;
    }

    public void sendLoginCode(String to, String username, String code, long ttlMinutes) {
        String subject = "Tu codigo de acceso a Mortal Online";
        String text = "Hola " + username + ",\n\n"
                + "Tu codigo de verificacion es: " + code + "\n\n"
                + "Expira en " + ttlMinutes + " minutos. Si no intentaste iniciar sesion, ignora este correo.\n\n"
                + "— Mortal Online";

        // 1) API HTTP en la nube (HTTPS 443: pasa cualquier firewall)
        if (!apiKey.isBlank()) {
            try {
                sendViaApi(to, username, subject, text);
                log.info("Codigo 2FA enviado por correo (API) a {}", to);
                return;
            } catch (Exception e) {
                log.error("Fallo el envio por API a {}: {}", to, e.getMessage());
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
                log.info("Codigo 2FA enviado por correo (SMTP) a {}", to);
                return;
            } catch (Exception e) {
                log.error("Fallo el envio por SMTP a {}: {}", to, e.getMessage());
            }
        }

        // 3) Respaldo: el operador puede leer el codigo del log (no bloquear el login)
        log.warn("Correo NO enviado. Codigo 2FA para {} <{}>: {}", username, to, code);
    }

    /** POST a la API transaccional de Brevo (o compatible via mail.api.url). */
    private void sendViaApi(String to, String username, String subject, String text) throws Exception {
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
        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " — " + response.body());
        }
    }

    private HttpClient client() throws Exception {
        if (httpClient == null) {
            HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8));
            if (insecureTrust) {
                // SOLO desarrollo: maquinas con antivirus/proxy que intercepta TLS
                // (el certificado presentado no es el real y la JVM lo rechazaria)
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[]{new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String a) { }
                    public void checkServerTrusted(X509Certificate[] c, String a) { }
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }}, null);
                builder.sslContext(ctx);
            }
            httpClient = builder.build();
        }
        return httpClient;
    }
}
