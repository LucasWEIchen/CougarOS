package com.centralbrain.runtime.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Domain-separated length-framed SHA-256 for data that may only be persisted as a digest. */
public final class DurableDigest {
    private DurableDigest() {
    }

    public static String sha256(String domain, String... fields) {
        if (domain == null || domain.trim().isEmpty()) {
            throw new IllegalArgumentException("digest domain is required");
        }
        MessageDigest digest = newDigest();
        update(digest, domain);
        if (fields != null) {
            for (String field : fields) {
                update(digest, field == null ? "" : field);
            }
        }
        return toHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
        digest.update(bytes);
    }

    private static String toHex(byte[] bytes) {
        char[] output = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = digits[value >>> 4];
            output[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(output);
    }
}
