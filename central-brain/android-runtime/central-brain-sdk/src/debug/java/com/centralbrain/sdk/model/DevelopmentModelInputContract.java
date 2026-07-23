package com.centralbrain.sdk.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/** Bounds, validation, and digest rules for debug-only multimodal model input. */
public final class DevelopmentModelInputContract {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_TEXT_CHARS = 64;
    public static final long MAX_IMAGE_BYTES = 6L * 1024L * 1024L;

    private DevelopmentModelInputContract() {}

    public static void validateMetadata(DevelopmentModelInput input) {
        Objects.requireNonNull(input, "input");
        if (input.schemaVersion != SCHEMA_VERSION) {
            throw violation("unsupported schema version");
        }
        requireUuid(input.sessionId);
        requireIdentifier(input.scenarioId, 96, "scenarioId");
        requireText(input.inputText);
        requireMimeType(input.imageMimeType);
        requireFileName(input.imageFileName);
        if (input.imageByteCount < 1L || input.imageByteCount > MAX_IMAGE_BYTES) {
            throw violation("imageByteCount is outside the bound");
        }
        requireDigest(input.imageSha256, "imageSha256");
        if (input.imageFd == null) {
            throw violation("imageFd is required");
        }
    }

    public static void validateReceipt(DevelopmentModelInputReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        if (receipt.schemaVersion != SCHEMA_VERSION) {
            throw violation("unsupported receipt schema version");
        }
        requireUuid(receipt.sessionId);
        requireIdentifier(receipt.scenarioId, 96, "scenarioId");
        requireText(receipt.inputText);
        requireMimeType(receipt.imageMimeType);
        requireFileName(receipt.imageFileName);
        if (receipt.imageByteCount < 1L || receipt.imageByteCount > MAX_IMAGE_BYTES) {
            throw violation("receipt imageByteCount is outside the bound");
        }
        requireDigest(receipt.imageSha256, "imageSha256");
        requireDigest(receipt.inputAggregateDigest, "inputAggregateDigest");
        if (receipt.acceptedAtEpochMs <= 0L) {
            throw violation("acceptedAtEpochMs must be positive");
        }
        requireDigest(receipt.receiptDigest, "receiptDigest");
        if (!receipt.inputAggregateDigest.equals(calculateInputAggregateDigest(receipt))) {
            throw violation("input aggregate digest mismatch");
        }
        if (!receipt.receiptDigest.equals(calculateReceiptDigest(receipt))) {
            throw violation("receipt digest mismatch");
        }
    }

    public static String calculateInputAggregateDigest(DevelopmentModelInputReceipt receipt) {
        MessageDigest digest = sha256();
        update(digest, "central-brain-development-model-input-v1");
        update(digest, receipt.sessionId);
        update(digest, receipt.scenarioId);
        update(digest, receipt.inputText);
        update(digest, receipt.imageMimeType);
        update(digest, receipt.imageFileName);
        update(digest, Long.toString(receipt.imageByteCount));
        update(digest, receipt.imageSha256);
        return hex(digest.digest());
    }

    public static String calculateReceiptDigest(DevelopmentModelInputReceipt receipt) {
        MessageDigest digest = sha256();
        update(digest, "central-brain-development-model-input-receipt-v1");
        update(digest, receipt.inputAggregateDigest);
        update(digest, Long.toString(receipt.acceptedAtEpochMs));
        return hex(digest.digest());
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
        String safe = value == null ? "" : value;
        if (safe.isEmpty() || safe.length() > max
                || !safe.matches("[a-z][a-z0-9_.-]*")) {
            throw violation(field + " is invalid");
        }
    }

    private static void requireText(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isEmpty() || safe.length() > MAX_TEXT_CHARS) {
            throw violation("inputText is outside the bound");
        }
        for (int index = 0; index < safe.length(); index++) {
            if (Character.isISOControl(safe.charAt(index))) {
                throw violation("inputText contains control characters");
            }
        }
    }

    private static void requireMimeType(String value) {
        if (!"image/png".equals(value) && !"image/jpeg".equals(value)) {
            throw violation("imageMimeType is not allowlisted");
        }
    }

    private static void requireFileName(String value) {
        String safe = value == null ? "" : value;
        if (safe.isEmpty() || safe.length() > 96
                || !safe.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw violation("imageFileName is invalid");
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
        byte[] bytes = Objects.requireNonNull(value, "digest value")
                .getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        char[] output = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = alphabet[value >>> 4];
            output[index * 2 + 1] = alphabet[value & 0xf];
        }
        return new String(output);
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_DEVELOPMENT_MODEL_INPUT: " + message);
    }
}
