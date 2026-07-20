package com.mortalonline.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Envio del codigo por correo con degradacion en cascada (API -> SMTP -> log).
 * Se prueban las vias que no dependen de un servidor externo.
 */
@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock ObjectProvider<JavaMailSender> smtpProvider;

    private final ObjectMapper json = new ObjectMapper();

    private MailService mailService(String apiKey) {
        return new MailService(smtpProvider, json, "no-reply@mortalonline.gg", apiKey,
                "https://api.brevo.com/v3/smtp/email");
    }

    @Test
    void sinApiKeyNiSmtpCaeAlLogSinLanzarExcepcion() {
        when(smtpProvider.getIfAvailable()).thenReturn(null); // no hay SMTP configurado
        MailService mail = mailService(""); // sin clave de API

        assertDoesNotThrow(() -> mail.sendLoginCode("a@b.com", "juan", "123456", 10));
        assertDoesNotThrow(() -> mail.sendResetCode("a@b.com", "juan", "654321", 10));
    }

    @Test
    void sinApiKeyUsaSmtpCuandoEstaDisponible() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        when(smtpProvider.getIfAvailable()).thenReturn(smtp);
        MailService mail = mailService("");

        mail.sendLoginCode("a@b.com", "juan", "123456", 10);

        verify(smtp).send(any(SimpleMailMessage.class)); // envio real por SMTP
    }

    @Test
    void siElSmtpFallaNoPropagaLaExcepcion() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        doThrow(new org.springframework.mail.MailSendException("smtp caido"))
                .when(smtp).send(any(SimpleMailMessage.class));
        when(smtpProvider.getIfAvailable()).thenReturn(smtp);
        MailService mail = mailService("");

        // no debe romper el flujo de registro/login aunque el correo falle
        assertDoesNotThrow(() -> mail.sendResetCode("a@b.com", "juan", "654321", 10));
    }
}
