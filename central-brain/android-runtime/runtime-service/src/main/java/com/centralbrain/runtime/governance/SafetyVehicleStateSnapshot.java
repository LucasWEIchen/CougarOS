package com.centralbrain.runtime.governance;

import java.util.Objects;

/** Immutable Runtime-owned Safety/Vehicle State policy input. */
public final class SafetyVehicleStateSnapshot {
    public enum SafetyState {
        NORMAL,
        DEGRADED,
        EMERGENCY,
        UNKNOWN
    }

    public enum MotionState {
        PARKED,
        MOVING,
        UNKNOWN
    }

    public enum SourceAssurance {
        RUNTIME_OWNED_STUB,
        PLATFORM_TRUSTED_ADAPTER
    }

    private final String sourceId;
    private final long revision;
    private final long capturedAtElapsedRealtimeMs;
    private final SafetyState safetyState;
    private final MotionState motionState;
    private final boolean driverAvailable;
    private final SourceAssurance sourceAssurance;
    private final boolean hardwareBacked;

    public SafetyVehicleStateSnapshot(
            String sourceId,
            long revision,
            long capturedAtElapsedRealtimeMs,
            SafetyState safetyState,
            MotionState motionState,
            boolean driverAvailable,
            SourceAssurance sourceAssurance,
            boolean hardwareBacked) {
        if (sourceId == null || !sourceId.matches("[a-z0-9][a-z0-9._-]{2,63}")) {
            throw new IllegalArgumentException("stable state sourceId is required");
        }
        if (revision <= 0 || capturedAtElapsedRealtimeMs < 0) {
            throw new IllegalArgumentException("state revision and capture time are invalid");
        }
        this.sourceId = sourceId;
        this.revision = revision;
        this.capturedAtElapsedRealtimeMs = capturedAtElapsedRealtimeMs;
        this.safetyState = Objects.requireNonNull(safetyState, "safetyState");
        this.motionState = Objects.requireNonNull(motionState, "motionState");
        this.driverAvailable = driverAvailable;
        this.sourceAssurance = Objects.requireNonNull(sourceAssurance, "sourceAssurance");
        this.hardwareBacked = hardwareBacked;
        if (sourceAssurance == SourceAssurance.RUNTIME_OWNED_STUB && hardwareBacked) {
            throw new IllegalArgumentException("Runtime stub cannot claim hardware backing");
        }
    }

    public String getSourceId() {
        return sourceId;
    }

    public long getRevision() {
        return revision;
    }

    public long getCapturedAtElapsedRealtimeMs() {
        return capturedAtElapsedRealtimeMs;
    }

    public SafetyState getSafetyState() {
        return safetyState;
    }

    public MotionState getMotionState() {
        return motionState;
    }

    public boolean isDriverAvailable() {
        return driverAvailable;
    }

    public SourceAssurance getSourceAssurance() {
        return sourceAssurance;
    }

    public boolean isHardwareBacked() {
        return hardwareBacked;
    }

    public boolean isCallerControlled() {
        return false;
    }

    public boolean isProductionTrusted() {
        return sourceAssurance == SourceAssurance.PLATFORM_TRUSTED_ADAPTER && hardwareBacked;
    }
}
