package io.github.guhankesav.ledger.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Fingerprints a transfer request so a reused idempotency key with a different body is detected. */
public final class RequestHasher {

    private RequestHasher() {
    }

    public static String transferHash(UUID from, UUID to, long amountMinor, String currency) {
        String canonical = from + "|" + to + "|" + amountMinor + "|" + currency;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
