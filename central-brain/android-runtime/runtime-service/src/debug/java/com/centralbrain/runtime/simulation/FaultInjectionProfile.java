package com.centralbrain.runtime.simulation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Immutable bounded fault selection for one simulated adapter invocation. */
public final class FaultInjectionProfile {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_DURATION_MS = 60_000L;

    public enum Mode {
        NONE,
        DELAY,
        TIMEOUT,
        RETRYABLE_FAILURE,
        TERMINAL_FAILURE,
        READBACK_MISMATCH
    }

    private final Mode mode;
    private final long durationMs;
    private final String digest;

    private FaultInjectionProfile(Mode mode, long durationMs) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if ((mode == Mode.DELAY || mode == Mode.TIMEOUT)
                != (durationMs >= 1 && durationMs <= MAX_DURATION_MS)) {
            throw new IllegalArgumentException(
                    "CB_SIM_FAULT: duration does not match fault mode");
        }
        if (mode != Mode.DELAY && mode != Mode.TIMEOUT && durationMs != 0) {
            throw new IllegalArgumentException(
                    "CB_SIM_FAULT: non-timing mode cannot carry duration");
        }
        this.durationMs = durationMs;
        this.digest = digest(mode, durationMs);
    }

    public static FaultInjectionProfile none() {
        return new FaultInjectionProfile(Mode.NONE, 0);
    }

    public static FaultInjectionProfile delay(long durationMs) {
        return new FaultInjectionProfile(Mode.DELAY, durationMs);
    }

    public static FaultInjectionProfile timeout(long durationMs) {
        return new FaultInjectionProfile(Mode.TIMEOUT, durationMs);
    }

    public static FaultInjectionProfile retryableFailure() {
        return new FaultInjectionProfile(Mode.RETRYABLE_FAILURE, 0);
    }

    public static FaultInjectionProfile terminalFailure() {
        return new FaultInjectionProfile(Mode.TERMINAL_FAILURE, 0);
    }

    public static FaultInjectionProfile readbackMismatch() {
        return new FaultInjectionProfile(Mode.READBACK_MISMATCH, 0);
    }

    public Mode getMode() {
        return mode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getDigest() {
        return digest;
    }

    public boolean isSimulationOnly() {
        return true;
    }

    public boolean isProductionAuthorized() {
        return false;
    }

    private static String digest(Mode mode, long durationMs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "central-brain-simulation-fault-v1");
            update(digest, mode.name());
            update(digest, Long.toString(durationMs));
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("CB_SIM_FAULT: SHA-256 unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String toHex(byte[] bytes) {
        char[] output = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int current = bytes[index] & 0xff;
            output[index * 2] = digits[current >>> 4];
            output[index * 2 + 1] = digits[current & 0x0f];
        }
        return new String(output);
    }
}
