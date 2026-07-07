package com.mortalonline.auth.service;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * 2FA con TOTP (RFC 6238): codigos de 6 digitos que rotan cada 30 segundos,
 * compatibles con Google Authenticator / Authy. El secreto se genera en el
 * registro y se entrega una unica vez como URL otpauth://.
 */
@Service
public class TotpService {

    private final TimeBasedOneTimePasswordGenerator totp = new TimeBasedOneTimePasswordGenerator();
    private final SecureRandom random = new SecureRandom();
    private final Base32 base32 = new Base32();

    /** Genera un secreto TOTP nuevo (20 bytes aleatorios, en Base32). */
    public String generateSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32.encodeToString(bytes).replace("=", "");
    }

    /**
     * Verifica el codigo aceptando una ventana de +/- 1 paso de tiempo (30s)
     * para tolerar desfase de reloj entre el servidor y el telefono.
     */
    public boolean verify(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        Key key = new SecretKeySpec(base32.decode(secret), totp.getAlgorithm());
        Instant now = Instant.now();
        try {
            for (int step = -1; step <= 1; step++) {
                Instant at = now.plus(totp.getTimeStep().multipliedBy(step));
                String expected = String.format("%06d", totp.generateOneTimePassword(key, at));
                if (expected.equals(code)) return true;
            }
        } catch (InvalidKeyException e) {
            return false;
        }
        return false;
    }

    /** URL para registrar la cuenta en la app de autenticacion (codigo QR). */
    public String otpauthUrl(String username, String secret) {
        return "otpauth://totp/MortalOnline:" + username + "?secret=" + secret + "&issuer=MortalOnline";
    }
}
