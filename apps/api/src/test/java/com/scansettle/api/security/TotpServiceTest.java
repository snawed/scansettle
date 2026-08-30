package com.scansettle.api.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    @Test
    void secretIsUniquePerCall() {
        assertThat(totpService.generateSecret()).isNotEqualTo(totpService.generateSecret());
    }

    @Test
    void otpAuthUriCarriesTheSecretAndAccount() {
        String secret = totpService.generateSecret();
        String uri = totpService.buildOtpAuthUri(secret, "owner@example.com");

        assertThat(uri).startsWith("otpauth://totp/ScanSettle:owner@example.com");
        assertThat(uri).contains("secret=" + secret);
    }

    @Test
    void wrongCodeIsRejected() {
        String secret = totpService.generateSecret();
        assertThat(totpService.verify(secret, "000000")).isFalse();
    }

    @Test
    void codeGeneratedForTheCurrentSecretVerifiesCorrectly() throws Exception {
        // Round-trips the base32 encode/decode + HMAC-SHA1 code generation against
        // verify()'s own drift-tolerant check for the same time step. generateCode
        // is private (no public "compute the current code" API is needed by callers),
        // so reflection is the pragmatic way to exercise it directly.
        String secret = totpService.generateSecret();
        var method = TotpService.class.getDeclaredMethod("generateCode", String.class, long.class);
        method.setAccessible(true);
        long currentStep = System.currentTimeMillis() / 1000 / 30;
        String code = (String) method.invoke(totpService, secret, currentStep);

        assertThat(code).hasSize(6);
        assertThat(totpService.verify(secret, code)).isTrue();
    }
}
