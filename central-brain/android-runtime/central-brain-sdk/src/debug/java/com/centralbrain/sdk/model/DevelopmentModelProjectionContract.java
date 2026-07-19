package com.centralbrain.sdk.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/** Validation and digest rules for the bounded debug-only model UX projection. */
public final class DevelopmentModelProjectionContract {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ASSISTANT_DISPLAY_CHARS = 256;
    public static final long MAX_LATENCY_MS = 120_000L;

    private DevelopmentModelProjectionContract() {}

    public static void validate(DevelopmentModelProjection projection) {
        Objects.requireNonNull(projection, "projection");
        if (projection.schemaVersion != SCHEMA_VERSION) {
            throw violation("unsupported schema version");
        }
        requireUuid(projection.sessionId);
        requireIdentifier(projection.scenarioId, 96, "scenarioId");
        requireIdentifier(projection.providerId, 96, "providerId");
        String displayText = safe(projection.assistantDisplayText);
        if (displayText.isEmpty() || displayText.length() > MAX_ASSISTANT_DISPLAY_CHARS) {
            throw violation("assistant display text is outside the bound");
        }
        for (int index = 0; index < displayText.length(); index++) {
            if (Character.isISOControl(displayText.charAt(index))) {
                throw violation("assistant display text contains control characters");
            }
        }
        if (projection.latencyMs < 0L || projection.latencyMs > MAX_LATENCY_MS) {
            throw violation("latency is outside the bound");
        }
        requireDigest(projection.outputDigest, "outputDigest");
        requireDigest(projection.projectionDigest, "projectionDigest");
        if (projection.completedAtEpochMs <= 0L) {
            throw violation("completion time must be positive");
        }
        if (!projection.projectionDigest.equals(calculateDigest(projection))) {
            throw violation("projection digest mismatch");
        }
    }

    public static String calculateDigest(DevelopmentModelProjection projection) {
        Objects.requireNonNull(projection, "projection");
        MessageDigest digest = sha256();
        update(digest, "central-brain-development-model-projection-v1");
        update(digest, Integer.toString(projection.schemaVersion));
        update(digest, safe(projection.sessionId));
        update(digest, safe(projection.scenarioId));
        update(digest, safe(projection.providerId));
        update(digest, safe(projection.assistantDisplayText));
        update(digest, Long.toString(projection.latencyMs));
        update(digest, safe(projection.outputDigest));
        update(digest, Long.toString(projection.completedAtEpochMs));
        return toHex(digest.digest());
    }

    private static void requireUuid(String value) {
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException failure) {
            throw violation("sessionId must be a canonical UUID");
        }
    }

    private static void requireIdentifier(String value, int max, String field) {
        String safeValue = safe(value);
        if (safeValue.isEmpty()
                || safeValue.length() > max
                || !safeValue.matches("[a-z][a-z0-9_.-]*")) {
            throw violation(field + " is invalid");
        }
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw violation(field + " must be a lowercase SHA-256");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String toHex(byte[] bytes) {
        char[] output = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = alphabet[value >>> 4];
            output[index * 2 + 1] = alphabet[value & 0xf];
        }
        return new String(output);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_DEVELOPMENT_MODEL_PROJECTION: " + message);
    }
}
