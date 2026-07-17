package com.centralbrain.runtime.graph;

import com.centralbrain.sdk.plan.PlanContract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Shared bounded validation and canonical digest rules for typed node execution contracts. */
final class NodeExecutionContract {
    static final int SCHEMA_VERSION = 1;
    static final int MAX_SCHEMA_ID_CHARS = 64;
    static final int MAX_MESSAGE_KEY_CHARS = 96;

    private static final Pattern LOCAL_ID =
            Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern QUALIFIED_ID =
            Pattern.compile("[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern RESOURCE_KEY =
            Pattern.compile("[a-z][a-z0-9_-]*(?::[a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private NodeExecutionContract() {}

    static String canonicalUuid(String value, String label) {
        requireBounded(value, 36, label);
        try {
            String canonical = UUID.fromString(value).toString();
            if (!canonical.equals(value)) {
                throw violation(label + " must be a canonical UUID");
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw violation(label + " must be a canonical UUID");
        }
    }

    static String requireNodeId(String value, String label) {
        return requirePattern(value, PlanContract.MAX_NODE_ID_CHARS, LOCAL_ID, label);
    }

    static String requireNodeType(String value) {
        requireBounded(value, PlanContract.MAX_NODE_TYPE_CHARS, "nodeType");
        if (!PlanContract.allowedNodeTypes().contains(value)) {
            throw violation("nodeType is not allowlisted");
        }
        return value;
    }

    static String requireQualifiedId(String value, int maxChars, String label) {
        return requirePattern(value, maxChars, QUALIFIED_ID, label);
    }

    static String requireResourceKey(String value, String label) {
        return requirePattern(value, PlanContract.MAX_RESOURCE_KEY_CHARS, RESOURCE_KEY, label);
    }

    static String requireMessageKey(String value, String label) {
        return requirePattern(value, MAX_MESSAGE_KEY_CHARS, QUALIFIED_ID, label);
    }

    static String requireDigest(String value, String label) {
        return requirePattern(value, 64, SHA_256, label);
    }

    static String requireSchemaId(String value, String label) {
        return requirePattern(value, MAX_SCHEMA_ID_CHARS, QUALIFIED_ID, label);
    }

    static String requireBounded(String value, int maxChars, String label) {
        if (value == null || value.isEmpty() || value.length() > maxChars) {
            throw violation(label + " is empty or exceeds " + maxChars);
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isSurrogate(character)) {
                throw violation(label + " contains unsupported characters");
            }
        }
        return value;
    }

    static String digest(String domain, String... values) {
        requireSchemaId(domain, "digest domain");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, domain);
            for (String value : values) {
                update(digest, Objects.requireNonNull(value, "digest value"));
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String requirePattern(
            String value,
            int maxChars,
            Pattern pattern,
            String label) {
        requireBounded(value, maxChars, label);
        if (!pattern.matcher(value).matches()) {
            throw violation(label + " is not canonical");
        }
        return value;
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }

    static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_NODE_CONTRACT: " + message);
    }
}
