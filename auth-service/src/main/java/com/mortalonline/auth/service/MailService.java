package com.mortalonline.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envio del codigo de verificacion por CORREO (2FA). El servidor SMTP se
 * configura por variables de entorno (SPRING_MAIL_HOST, SPRING_MAIL_USERNAME,
 * SPRING_MAIL_PASSWORD...). Si NO hay SMTP configurado (desarrollo), el codigo
 * se escribe en el log del servicio para poder probar el flujo completo.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> senderProvider,
                       @Value("${security.otp.mail-from:${spring.mail.username:no-reply@mortalonline.gg}}") String from) {
        this.senderProvider = senderProvider;
        this.from = from;
    }

    public void sendLoginCode(String to, String username, String code, long ttlMinutes) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null) {
            // SMTP no configurado (desarrollo): dejar el codigo en el log
            log.warn("SMTP no configurado. Codigo 2FA para {} <{}>: {}", username, to, code);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("Tu codigo de acceso a Mortal Online");
            message.setText("Hola " + username + ",\n\n"
                    + "Tu codigo de verificacion es: " + code + "\n\n"
                    + "Expira en " + ttlMinutes + " minutos. Si no intentaste iniciar sesion, ignora este correo.\n\n"
                    + "— Mortal Online");
            sender.send(message);
            log.info("Codigo 2FA enviado por correo a {}", to);
        } catch (Exception e) {
            // SMTP mal configurado o caido: dejar el codigo en el log para que
            // el operador pueda desbloquear al usuario (no bloquear el login)
            log.error("No se pudo enviar el correo de 2FA a {} ({}). Codigo de respaldo para {}: {}",
                    to, e.getMessage(), username, code);
        }
    }
}
