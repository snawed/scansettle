package com.scansettle.api.common.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private final EncryptionService encryptionService = new EncryptionService(
            Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void roundTripsPlaintext() {
        String ciphertext = encryptionService.encrypt("12345678");
        assertThat(ciphertext).isNotEqualTo("12345678");
        assertThat(encryptionService.decrypt(ciphertext)).isEqualTo("12345678");
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        // A fresh random IV per call — ciphertext must not be a stable fingerprint
        // of the plaintext (would otherwise leak equality between two rows).
        String first = encryptionService.encrypt("60-16-13 12345678");
        String second = encryptionService.encrypt("60-16-13 12345678");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void wrongLengthKeyIsRejectedAtConstruction() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new EncryptionService(tooShort)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tamperedCiphertextFailsToDecrypt() {
        String ciphertext = encryptionService.encrypt("sensitive-value");
        byte[] bytes = Base64.getDecoder().decode(ciphertext);
        bytes[bytes.length - 1] ^= 0x01; // flip a bit in the GCM tag/ciphertext
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> encryptionService.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }
}
