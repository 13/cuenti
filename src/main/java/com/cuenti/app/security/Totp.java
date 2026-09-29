package com.cuenti.app.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Time-based one-time passwords (RFC 6238): HMAC-SHA1, 6 digits, 30 s steps,
 * the defaults every authenticator app supports. Secrets are Base32 (RFC 4648)
 * without padding.
 */
public final class Totp {

    static final int DIGITS = 6;
    static final long STEP_SECONDS = 30;
    /** Codes from one step before and after are accepted for clock drift. */
    static final int WINDOW = 1;

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    /** A new 160-bit secret, Base32 encoded. */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** The URI authenticator apps read from the QR code. */
    public static String otpauthUri(String issuer, String account, String secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    public static long currentStep() {
        return System.currentTimeMillis() / 1000 / STEP_SECONDS;
    }

    /**
     * The time step the code belongs to, or -1 if it matches none within the
     * window. Callers reject steps at or before the last one accepted, so a code
     * cannot be used twice.
     */
    public static long matchingStep(String secret, String code, long step) {
        if (code == null) {
            return -1;
        }
        String digits = code.replace(" ", "");
        if (!digits.matches("\\d{" + DIGITS + "}")) {
            return -1;
        }
        byte[] key = base32Decode(secret);
        for (long s = step - WINDOW; s <= step + WINDOW; s++) {
            if (java.security.MessageDigest.isEqual(code(key, s).getBytes(StandardCharsets.US_ASCII),
                    digits.getBytes(StandardCharsets.US_ASCII))) {
                return s;
            }
        }
        return -1;
    }

    static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] base32Decode(String s) {
        String clean = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not Base32: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
