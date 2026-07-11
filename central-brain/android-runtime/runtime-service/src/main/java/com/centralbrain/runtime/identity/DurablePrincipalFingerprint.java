package com.centralbrain.runtime.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable digest for durable ownership; it is not a replacement for live Binder authorization. */
public final class DurablePrincipalFingerprint {
    private static final String DOMAIN = "central-brain-principal-v1";

    private DurablePrincipalFingerprint() {
    }

    public static String from(CallerIdentitySnapshot identity) {
        if (identity == null || !identity.isResolved()) {
            throw new SecurityException("resolved caller identity is required");
        }
        MessageDigest digest = sha256();
        updateField(digest, DOMAIN);
        updateField(digest, Long.toString(identity.getAndroidUserSerial()));
        for (CallerIdentitySnapshot.PackageIdentity packageIdentity : identity.getPackages()) {
            updateField(digest, packageIdentity.getPackageName());
            updateField(
                    digest,
                    Integer.toString(packageIdentity.getCurrentSignerSha256().size()));
            for (String signerDigest : packageIdentity.getCurrentSignerSha256()) {
                updateField(digest, signerDigest);
            }
        }
        return toHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateField(MessageDigest digest, String value) {
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
