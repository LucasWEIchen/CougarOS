package com.centralbrain.client2;

import java.util.Objects;

/** Immutable debug-controller projection. It is never a production authorization source. */
public final class CockpitEngineerState {
    public enum ConnectionState { UNAVAILABLE, CONNECTING, CONNECTED, FAILED }

    public enum AdapterTarget {
        HVAC("debug.simulated.hvac.v1"),
        SEAT("debug.simulated.seat.v1");

        private final String adapterId;

        AdapterTarget(String adapterId) {
            this.adapterId = adapterId;
        }

        public String getAdapterId() {
            return adapterId;
        }
    }

    public enum FaultMode {
        NONE,
        DELAY,
        TIMEOUT,
        RETRYABLE_FAILURE,
        TERMINAL_FAILURE,
        READBACK_MISMATCH
    }

    private final ConnectionState connectionState;
    private final CockpitSeatState.DrivingState drivingState;
    private final CockpitSeatState.OccupancyState occupancyState;
    private final CockpitSeatState.BeltState beltState;
    private final AdapterTarget adapterTarget;
    private final FaultMode faultMode;
    private final long controllerRevision;
    private final String statusCode;

    private CockpitEngineerState(
            ConnectionState connectionState,
            CockpitSeatState.DrivingState drivingState,
            CockpitSeatState.OccupancyState occupancyState,
            CockpitSeatState.BeltState beltState,
            AdapterTarget adapterTarget,
            FaultMode faultMode,
            long controllerRevision,
            String statusCode) {
        this.connectionState = Objects.requireNonNull(connectionState, "connectionState");
        this.drivingState = Objects.requireNonNull(drivingState, "drivingState");
        this.occupancyState = Objects.requireNonNull(occupancyState, "occupancyState");
        this.beltState = Objects.requireNonNull(beltState, "beltState");
        this.adapterTarget = Objects.requireNonNull(adapterTarget, "adapterTarget");
        this.faultMode = Objects.requireNonNull(faultMode, "faultMode");
        this.controllerRevision = Math.max(0, controllerRevision);
        this.statusCode = bounded(statusCode, 64);
        if (connectionState != ConnectionState.CONNECTED && controllerRevision != 0) {
            throw new IllegalArgumentException("disconnected engineer state cannot retain revision");
        }
    }

    public static CockpitEngineerState unavailable() {
        return base(ConnectionState.UNAVAILABLE, "DEBUG_CONTROLLER_UNAVAILABLE");
    }

    public static CockpitEngineerState connecting() {
        return base(ConnectionState.CONNECTING, "CONNECTING");
    }

    private static CockpitEngineerState base(ConnectionState state, String statusCode) {
        return new CockpitEngineerState(
                state,
                CockpitSeatState.DrivingState.UNKNOWN_RESTRICTED,
                CockpitSeatState.OccupancyState.UNKNOWN,
                CockpitSeatState.BeltState.UNKNOWN,
                AdapterTarget.HVAC,
                FaultMode.NONE,
                0,
                statusCode);
    }

    CockpitEngineerState connected(
            long revision,
            CockpitSeatState.DrivingState drivingState) {
        return new CockpitEngineerState(
                ConnectionState.CONNECTED,
                drivingState,
                CockpitSeatState.OccupancyState.UNKNOWN,
                CockpitSeatState.BeltState.UNKNOWN,
                adapterTarget,
                FaultMode.NONE,
                revision,
                "READY");
    }

    CockpitEngineerState drivingApplied(
            CockpitSeatState.DrivingState value,
            long revision) {
        requireConnectedRevision(revision);
        return copy(value, occupancyState, beltState, adapterTarget, faultMode, revision, "APPLIED");
    }

    CockpitEngineerState occupancyApplied(
            CockpitSeatState.OccupancyState value,
            long revision) {
        if (value == CockpitSeatState.OccupancyState.UNKNOWN) {
            throw new IllegalArgumentException("occupancy command cannot be unknown");
        }
        requireConnectedRevision(revision);
        return copy(drivingState, value, beltState, adapterTarget, faultMode, revision, "APPLIED");
    }

    CockpitEngineerState beltApplied(CockpitSeatState.BeltState value, long revision) {
        if (value == CockpitSeatState.BeltState.UNKNOWN) {
            throw new IllegalArgumentException("belt command cannot be unknown");
        }
        requireConnectedRevision(revision);
        return copy(drivingState, occupancyState, value, adapterTarget, faultMode, revision, "APPLIED");
    }

    CockpitEngineerState adapterSelected(AdapterTarget value) {
        if (connectionState != ConnectionState.CONNECTED) {
            return this;
        }
        return copy(
                drivingState,
                occupancyState,
                beltState,
                Objects.requireNonNull(value, "value"),
                faultMode,
                controllerRevision,
                statusCode);
    }

    CockpitEngineerState faultApplied(FaultMode value, long revision) {
        requireConnectedRevision(revision);
        return copy(
                drivingState,
                occupancyState,
                beltState,
                adapterTarget,
                Objects.requireNonNull(value, "value"),
                revision,
                "APPLIED");
    }

    CockpitEngineerState resetApplied(long revision) {
        requireConnectedRevision(revision);
        return new CockpitEngineerState(
                ConnectionState.CONNECTED,
                CockpitSeatState.DrivingState.UNKNOWN_RESTRICTED,
                CockpitSeatState.OccupancyState.UNKNOWN,
                CockpitSeatState.BeltState.UNKNOWN,
                AdapterTarget.HVAC,
                FaultMode.NONE,
                revision,
                "RESET");
    }

    CockpitEngineerState failed(String code) {
        return base(ConnectionState.FAILED, bounded(code, 64));
    }

    CockpitSeatState.SafetyContext toSafetyContext() {
        if (connectionState != ConnectionState.CONNECTED
                || drivingState == CockpitSeatState.DrivingState.UNKNOWN_RESTRICTED
                || controllerRevision <= 0) {
            return CockpitSeatState.SafetyContext.unavailable();
        }
        return CockpitSeatState.SafetyContext.observed(
                drivingState,
                occupancyState,
                beltState,
                CockpitSeatState.EvidenceSource.SIMULATED,
                controllerRevision);
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public CockpitSeatState.DrivingState getDrivingState() {
        return drivingState;
    }

    public CockpitSeatState.OccupancyState getOccupancyState() {
        return occupancyState;
    }

    public CockpitSeatState.BeltState getBeltState() {
        return beltState;
    }

    public AdapterTarget getAdapterTarget() {
        return adapterTarget;
    }

    public FaultMode getFaultMode() {
        return faultMode;
    }

    public long getControllerRevision() {
        return controllerRevision;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public boolean isAvailable() {
        return connectionState == ConnectionState.CONNECTED;
    }

    public boolean isProductionAvailable() {
        return false;
    }

    public boolean isEffectAuthorizationSource() {
        return false;
    }

    private CockpitEngineerState copy(
            CockpitSeatState.DrivingState driving,
            CockpitSeatState.OccupancyState occupancy,
            CockpitSeatState.BeltState belt,
            AdapterTarget adapter,
            FaultMode fault,
            long revision,
            String status) {
        return new CockpitEngineerState(
                ConnectionState.CONNECTED,
                driving,
                occupancy,
                belt,
                adapter,
                fault,
                revision,
                status);
    }

    private void requireConnectedRevision(long revision) {
        if (connectionState != ConnectionState.CONNECTED || revision <= controllerRevision) {
            throw new IllegalArgumentException("engineer command revision must increase");
        }
    }

    private static String bounded(String value, int maxChars) {
        String safe = value == null ? "" : value;
        return safe.length() <= maxChars ? safe : safe.substring(0, maxChars);
    }
}
