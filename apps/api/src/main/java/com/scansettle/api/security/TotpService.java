package com.scansettle.api.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * TOTP (RFC 6238) for merchant MFA — docs/security.md. Hand-rolled rather than a
 * new dependency: the algorithm is ~30 lines of well-specified HMAC-SHA1 and not
 * worth pulling in a library for.
 */
@Service
public class TotpService {

    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int ALLOWED_DRIFT_STEPS = 1; // ±30s clock drift tolerance

    private final SecureRandom secureRandom = new SecureRandom();

    /** A fresh random secret, base32-encoded for `otpauth://` URIs and manual entry. */
    public String generateSecret() {
        byte[] bytes = new byte[20]; // 160 bits, standard for HMAC-SHA1 TOTP
        secureRandom.nextBytes(bytes);
        return Base32.encode(bytes);
    }

    public String buildOtpAuthUri(String secret, String accountEmail) {
        return "otpauth://totp/ScanSettle:" + accountEmail
                + "?secret=" + secret
                + "&issuer=ScanSettle&digits=" + CODE_DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    public boolean verify(String secret, String code) {
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (int drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
            if (generateCode(secret, currentStep + drift).equals(code)) {
                return true;
            }
        }
        return false;
    }

    private String generateCode(String base32Secret, long timeStep) {
        try {
            byte[] key = Base32.decode(base32Secret);
            byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int code = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", code);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    /** Minimal RFC 4648 base32 — just enough for TOTP secrets (uppercase, no padding on encode). */
    private static final class Base32 {
        private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

        static String encode(byte[] data) {
            StringBuilder sb = new StringBuilder();
            int bits = 0, value = 0;
            for (byte b : data) {
                value = (value << 8) | (b & 0xFF);
                bits += 8;
                while (bits >= 5) {
                    sb.append(ALPHABET.charAt((value >>> (bits - 5)) & 0x1F));
                    bits -= 5;
                }
            }
            if (bits > 0) {
                sb.append(ALPHABET.charAt((value << (5 - bits)) & 0x1F));
            }
            return sb.toString();
        }

        static byte[] decode(String encoded) {
            String clean = encoded.trim().toUpperCase().replace("=", "");
            int bits = 0, value = 0;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            for (char c : clean.toCharArray()) {
                int idx = ALPHABET.indexOf(c);
                if (idx < 0) continue;
                value = (value << 5) | idx;
                bits += 5;
                if (bits >= 8) {
                    out.write((value >>> (bits - 8)) & 0xFF);
                    bits -= 8;
                }
            }
            return out.toByteArray();
        }
    }
}
