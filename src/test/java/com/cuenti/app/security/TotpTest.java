package com.cuenti.app.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    // RFC 6238 appendix B, SHA-1 seed; the 6-digit codes are the last 6 of the 8-digit ones
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238TestVectors() {
        assertThat(Totp.code(RFC_KEY, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.code(RFC_KEY, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.code(RFC_KEY, 1234567890L / 30)).isEqualTo("005924");
        assertThat(Totp.code(RFC_KEY, 2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    void base32RoundTripsAndMatchesRfc4648() {
        assertThat(Totp.base32Encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(new String(Totp.base32Decode("MZXW6YTBOI"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        String secret = Totp.newSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.base32Decode(secret)).hasSize(20);
    }

    @Test
    void acceptsOneStepOfDriftAndReportsTheStep() {
        String secret = Totp.newSecret();
        byte[] key = Totp.base32Decode(secret);
        long now = 1_000_000;
        assertThat(Totp.matchingStep(secret, Totp.code(key, now), now)).isEqualTo(now);
        assertThat(Totp.matchingStep(secret, Totp.code(key, now - 1), now)).isEqualTo(now - 1);
        assertThat(Totp.matchingStep(secret, Totp.code(key, now + 1), now)).isEqualTo(now + 1);
        assertThat(Totp.matchingStep(secret, Totp.code(key, now - 2), now)).isEqualTo(-1);
        assertThat(Totp.matchingStep(secret, "12345", now)).isEqualTo(-1);
        assertThat(Totp.matchingStep(secret, null, now)).isEqualTo(-1);
    }

    @Test
    void otpauthUriHasWhatAppsNeed() {
        assertThat(Totp.otpauthUri("Cuenti", "max mustermann", "ABC"))
                .isEqualTo("otpauth://totp/Cuenti:max%20mustermann?secret=ABC&issuer=Cuenti"
                        + "&algorithm=SHA1&digits=6&period=30");
    }
}
