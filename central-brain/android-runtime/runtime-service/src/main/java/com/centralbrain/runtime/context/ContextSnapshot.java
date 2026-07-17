package com.centralbrain.runtime.context;

import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable typed Context view derived from one atomic Digital Twin revision. */
public final class ContextSnapshot {
    public static final int SCHEMA_VERSION = 1;

    public enum SeatZone {
        UNSPECIFIED,
        ROW1_DRIVER,
        ROW1_PASSENGER,
        ROW2_LEFT,
        ROW2_RIGHT,
        CABIN;

        public Optional<String> vehicleArea() {
            switch (this) {
                case ROW1_DRIVER:
                    return Optional.of("row1.driver");
                case ROW1_PASSENGER:
                    return Optional.of("row1.passenger");
                case ROW2_LEFT:
                    return Optional.of("row2.left");
                case ROW2_RIGHT:
                    return Optional.of("row2.right");
                case UNSPECIFIED:
                case CABIN:
                    return Optional.empty();
                default:
                    throw new IllegalStateException("CB_CONTEXT: unknown seat zone");
            }
        }
    }

    public enum DrivingState {
        PARKED,
        MOVING,
        UNKNOWN
    }

    public enum FieldState {
        AVAILABLE,
        MISSING,
        STALE,
        UNAVAILABLE,
        ERROR,
        CONFLICT
    }

    public enum TrustLevel {
        SIMULATED,
        PLATFORM_UNVERIFIED,
        DERIVED_UNVERIFIED,
        UNKNOWN
    }

    public enum SourceMode {
        SIMULATED,
        PLATFORM_UNVERIFIED,
        DERIVED_UNVERIFIED,
        MIXED,
        UNKNOWN
    }

    public static final class FieldKey {
        private final VehicleSignalPath path;
        private final String area;

        FieldKey(VehicleSignalPath path, String area) {
            this.path = Objects.requireNonNull(path, "path");
            this.area = Objects.requireNonNull(area, "area");
            if (!area.isEmpty() && !path.getAreas().contains(area)) {
                throw new IllegalArgumentException("CB_CONTEXT: field area is not allowed");
            }
        }

        public VehicleSignalPath getPath() {
            return path;
        }

        public String getArea() {
            return area;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FieldKey)) {
                return false;
            }
            FieldKey key = (FieldKey) other;
            return path == key.path && area.equals(key.area);
        }

        @Override
        public int hashCode() {
            return 31 * path.hashCode() + area.hashCode();
        }
    }

    public static final class ContextField {
        private final FieldKey key;
        private final boolean required;
        private final FieldState state;
        private final SignalValue value;
        private final SignalQuality effectiveQuality;
        private final TrustLevel trustLevel;

        ContextField(
                FieldKey key,
                boolean required,
                FieldState state,
                SignalValue value,
                SignalQuality effectiveQuality,
                TrustLevel trustLevel) {
            this.key = Objects.requireNonNull(key, "key");
            this.required = required;
            this.state = Objects.requireNonNull(state, "state");
            this.trustLevel = Objects.requireNonNull(trustLevel, "trustLevel");
            validateState(state, value, effectiveQuality);
            this.value = value;
            this.effectiveQuality = effectiveQuality;
        }

        private static void validateState(
                FieldState state,
                SignalValue value,
                SignalQuality quality) {
            if (state == FieldState.MISSING) {
                if (value != null || quality != null) {
                    throw new IllegalArgumentException(
                            "CB_CONTEXT: missing field cannot carry an observation");
                }
                return;
            }
            if (value == null || quality == null) {
                throw new IllegalArgumentException(
                        "CB_CONTEXT: observed field metadata is missing");
            }
            boolean valid = state == FieldState.AVAILABLE && quality == SignalQuality.VALID
                    || state == FieldState.STALE && quality == SignalQuality.STALE
                    || state == FieldState.UNAVAILABLE && quality == SignalQuality.UNAVAILABLE
                    || state == FieldState.ERROR && quality == SignalQuality.ERROR
                    || state == FieldState.CONFLICT && quality == SignalQuality.CONFLICT;
            if (!valid) {
                throw new IllegalArgumentException(
                        "CB_CONTEXT: field state and quality do not match");
            }
        }

        public FieldKey getKey() {
            return key;
        }

        public boolean isRequired() {
            return required;
        }

        public FieldState getState() {
            return state;
        }

        public Optional<SignalValue> getValue() {
            return Optional.ofNullable(value);
        }

        public Optional<SignalQuality> getEffectiveQuality() {
            return Optional.ofNullable(effectiveQuality);
        }

        public Optional<SignalSource> getSource() {
            return value == null ? Optional.empty() : Optional.of(value.getSource());
        }

        public TrustLevel getTrustLevel() {
            return trustLevel;
        }

        public boolean isUsableForDecision() {
            return state == FieldState.AVAILABLE;
        }
    }

    private final String contextId;
    private final int schemaVersion;
    private final String policyId;
    private final int policyVersion;
    private final long twinRevision;
    private final long capturedAtElapsedRealtimeMs;
    private final long runtimeStateRevision;
    private final SeatZone seatZone;
    private final DrivingState drivingState;
    private final SafetyVehicleStateSnapshot.SafetyState safetyState;
    private final SourceMode sourceMode;
    private final boolean profileMemoryAvailable;
    private final boolean runtimeStateFresh;
    private final boolean motionConflict;
    private final boolean requiredFreshnessComplete;
    private final boolean restricted;
    private final boolean productionTrusted;
    private final String digest;
    private final List<ContextField> fields;
    private final List<FieldKey> missingRequiredFields;
    private final List<FieldKey> staleFields;
    private final List<FieldKey> conflictFields;
    private final List<FieldKey> nonProductionTrustedFields;

    ContextSnapshot(
            String contextId,
            String policyId,
            int policyVersion,
            long twinRevision,
            long capturedAtElapsedRealtimeMs,
            long runtimeStateRevision,
            SeatZone seatZone,
            DrivingState drivingState,
            SafetyVehicleStateSnapshot.SafetyState safetyState,
            SourceMode sourceMode,
            boolean profileMemoryAvailable,
            boolean runtimeStateFresh,
            boolean motionConflict,
            boolean requiredFreshnessComplete,
            boolean restricted,
            boolean productionTrusted,
            String digest,
            List<ContextField> fields,
            List<FieldKey> missingRequiredFields,
            List<FieldKey> staleFields,
            List<FieldKey> conflictFields,
            List<FieldKey> nonProductionTrustedFields) {
        if (contextId == null || !contextId.matches("ctx-[0-9a-f]{24}")) {
            throw new IllegalArgumentException("CB_CONTEXT: context ID is invalid");
        }
        if (policyId == null || policyId.isEmpty() || policyVersion < 1
                || twinRevision < 0 || capturedAtElapsedRealtimeMs < 0
                || runtimeStateRevision < 1) {
            throw new IllegalArgumentException("CB_CONTEXT: snapshot metadata is invalid");
        }
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CB_CONTEXT: snapshot digest is invalid");
        }
        this.contextId = contextId;
        this.schemaVersion = SCHEMA_VERSION;
        this.policyId = policyId;
        this.policyVersion = policyVersion;
        this.twinRevision = twinRevision;
        this.capturedAtElapsedRealtimeMs = capturedAtElapsedRealtimeMs;
        this.runtimeStateRevision = runtimeStateRevision;
        this.seatZone = Objects.requireNonNull(seatZone, "seatZone");
        this.drivingState = Objects.requireNonNull(drivingState, "drivingState");
        this.safetyState = Objects.requireNonNull(safetyState, "safetyState");
        this.sourceMode = Objects.requireNonNull(sourceMode, "sourceMode");
        this.profileMemoryAvailable = profileMemoryAvailable;
        this.runtimeStateFresh = runtimeStateFresh;
        this.motionConflict = motionConflict;
        this.requiredFreshnessComplete = requiredFreshnessComplete;
        this.restricted = restricted;
        this.productionTrusted = productionTrusted;
        this.digest = digest;
        this.fields = immutableCopy(fields);
        this.missingRequiredFields = immutableCopy(missingRequiredFields);
        this.staleFields = immutableCopy(staleFields);
        this.conflictFields = immutableCopy(conflictFields);
        this.nonProductionTrustedFields = immutableCopy(nonProductionTrustedFields);
        if (productionTrusted) {
            throw new IllegalArgumentException(
                    "CB_CONTEXT: P2-W04 cannot claim production trust");
        }
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        Objects.requireNonNull(values, "values");
        List<T> copy = new ArrayList<>(values.size());
        for (T value : values) {
            copy.add(Objects.requireNonNull(value, "value"));
        }
        return Collections.unmodifiableList(copy);
    }

    public String getContextId() {
        return contextId;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getPolicyId() {
        return policyId;
    }

    public int getPolicyVersion() {
        return policyVersion;
    }

    public long getTwinRevision() {
        return twinRevision;
    }

    public long getCapturedAtElapsedRealtimeMs() {
        return capturedAtElapsedRealtimeMs;
    }

    public long getRuntimeStateRevision() {
        return runtimeStateRevision;
    }

    public SeatZone getSeatZone() {
        return seatZone;
    }

    public DrivingState getDrivingState() {
        return drivingState;
    }

    public SafetyVehicleStateSnapshot.SafetyState getSafetyState() {
        return safetyState;
    }

    public SourceMode getSourceMode() {
        return sourceMode;
    }

    public boolean isProfileMemoryAvailable() {
        return profileMemoryAvailable;
    }

    public boolean isRuntimeStateFresh() {
        return runtimeStateFresh;
    }

    public boolean hasMotionConflict() {
        return motionConflict;
    }

    public boolean isRequiredFreshnessComplete() {
        return requiredFreshnessComplete;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public boolean isProductionTrusted() {
        return productionTrusted;
    }

    public String getDigest() {
        return digest;
    }

    public List<ContextField> getFields() {
        return fields;
    }

    public List<FieldKey> getMissingRequiredFields() {
        return missingRequiredFields;
    }

    public List<FieldKey> getStaleFields() {
        return staleFields;
    }

    public List<FieldKey> getConflictFields() {
        return conflictFields;
    }

    public List<FieldKey> getNonProductionTrustedFields() {
        return nonProductionTrustedFields;
    }

    public Optional<ContextField> field(VehicleSignalPath path, String area) {
        FieldKey key = new FieldKey(path, area);
        for (ContextField field : fields) {
            if (field.key.equals(key)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }
}
