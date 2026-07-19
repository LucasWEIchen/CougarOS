package com.centralbrain.runtime.model;

import java.util.Arrays;

/** Process-local target credential store for controlled hardware-test builds. */
public final class OpenClawCredentialStore {
    private static final int MIN_TOKEN_LENGTH = 8;
    private static final int MAX_TOKEN_LENGTH = 256;

    private static char[] token;

    private OpenClawCredentialStore() {
    }

    public static synchronized void provision(char[] candidate) {
        validate(candidate);
        clearLocked();
        token = Arrays.copyOf(candidate, candidate.length);
    }

    public static synchronized String requireToken() {
        if (token == null) {
            throw new IllegalStateException("OpenClaw credential is not provisioned");
        }
        return new String(token);
    }

    public static synchronized boolean isProvisioned() {
        return token != null;
    }

    public static synchronized void clear() {
        clearLocked();
    }

    private static void validate(char[] candidate) {
        if (candidate == null
                || candidate.length < MIN_TOKEN_LENGTH
                || candidate.length > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("OpenClaw credential length is invalid");
        }
        for (char value : candidate) {
            if (value < 0x21 || value > 0x7e) {
                throw new IllegalArgumentException("OpenClaw credential must be printable ASCII");
            }
        }
    }

    private static void clearLocked() {
        if (token != null) {
            Arrays.fill(token, '\0');
            token = null;
        }
    }
}
