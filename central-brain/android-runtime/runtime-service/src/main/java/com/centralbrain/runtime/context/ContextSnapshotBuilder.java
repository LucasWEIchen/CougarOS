package com.centralbrain.runtime.context;

import com.centralbrain.runtime.context.ContextFieldPolicy.AreaScope;
import com.centralbrain.runtime.context.ContextFieldPolicy.FieldRequirement;
import com.centralbrain.runtime.context.ContextSnapshot.ContextField;
import com.centralbrain.runtime.context.ContextSnapshot.DrivingState;
import com.centralbrain.runtime.context.ContextSnapshot.FieldKey;
import com.centralbrain.runtime.context.ContextSnapshot.FieldState;
import com.centralbrain.runtime.context.ContextSnapshot.SeatZone;
import com.centralbrain.runtime.context.ContextSnapshot.SourceMode;
import com.centralbrain.runtime.context.ContextSnapshot.TrustLevel;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;
import com.centralbrain.runtime.vehicle.twin.ReportedStateRecord;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Builds deterministic fail-closed Context snapshots without reading a provider or hardware. */
public final class ContextSnapshotBuilder {
    private static final double MOVING_SPEED_THRESHOLD_KPH = 0.5;

    public ContextSnapshot build(
            DigitalTwinSnapshot twin,
            SafetyVehicleStateSnapshot runtimeState,
            ContextFieldPolicy policy,
            SeatZone seatZone,
            boolean profileMemoryAvailable) {
        Objects.requireNonNull(twin, "twin");
        Objects.requireNonNull(runtimeState, "runtimeState");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(seatZone, "seatZone");
        long capturedAt = twin.getCapturedElapsedRealtimeMs();
        if (runtimeState.getCapturedAtElapsedRealtimeMs() > capturedAt) {
            throw new IllegalArgumentException(
                    "CB_CONTEXT: Runtime state is newer than Twin snapshot");
        }
        boolean runtimeStateFresh = capturedAt - runtimeState.getCapturedAtElapsedRealtimeMs()
                <= policy.getRuntimeStateMaximumAgeMs();

        List<ContextField> fields = new ArrayList<>();
        List<FieldKey> missingRequired = new ArrayList<>();
        List<FieldKey> stale = new ArrayList<>();
        List<FieldKey> conflict = new ArrayList<>();
        List<FieldKey> nonProductionTrusted = new ArrayList<>();
        boolean requiredFreshnessComplete = true;

        for (FieldRequirement requirement : policy.getRequirements()) {
            String area = resolveArea(requirement.getAreaScope(), seatZone);
            ContextField field = buildField(twin, requirement, area);
            fields.add(field);
            FieldKey key = field.getKey();
            if (field.getState() == FieldState.STALE) {
                stale.add(key);
            } else if (field.getState() == FieldState.CONFLICT) {
                conflict.add(key);
            }
            if (field.getValue().isPresent()
                    && field.getTrustLevel() != TrustLevel.UNKNOWN) {
                nonProductionTrusted.add(key);
            }
            if (requirement.isRequired() && !field.isUsableForDecision()) {
                requiredFreshnessComplete = false;
                if (field.getState() == FieldState.MISSING
                        || field.getState() == FieldState.UNAVAILABLE
                        || field.getState() == FieldState.ERROR) {
                    missingRequired.add(key);
                }
            }
        }

        DrivingState signalDrivingState = deriveSignalDrivingState(fields);
        DrivingResolution driving = reconcileDrivingState(
                signalDrivingState,
                runtimeState.getMotionState(),
                runtimeStateFresh);
        SafetyVehicleStateSnapshot.SafetyState safetyState = runtimeStateFresh
                ? runtimeState.getSafetyState()
                : SafetyVehicleStateSnapshot.SafetyState.UNKNOWN;
        boolean restricted = !runtimeStateFresh
                || safetyState != SafetyVehicleStateSnapshot.SafetyState.NORMAL
                || driving.state == DrivingState.UNKNOWN
                || driving.conflict
                || !requiredFreshnessComplete;
        SourceMode sourceMode = sourceMode(fields);
        boolean productionTrusted = false;

        String digest = digest(
                twin,
                runtimeState,
                policy,
                seatZone,
                profileMemoryAvailable,
                fields,
                driving,
                safetyState,
                sourceMode,
                runtimeStateFresh,
                requiredFreshnessComplete,
                restricted,
                productionTrusted);
        String contextId = "ctx-" + digest.substring(0, 24);
        return new ContextSnapshot(
                contextId,
                policy.getPolicyId(),
                policy.getVersion(),
                twin.getRevision(),
                capturedAt,
                runtimeState.getRevision(),
                seatZone,
                driving.state,
                safetyState,
                sourceMode,
                profileMemoryAvailable,
                runtimeStateFresh,
                driving.conflict,
                requiredFreshnessComplete,
                restricted,
                productionTrusted,
                digest,
                fields,
                missingRequired,
                stale,
                conflict,
                nonProductionTrusted);
    }

    private static String resolveArea(AreaScope scope, SeatZone seatZone) {
        switch (scope) {
            case GLOBAL:
                return "global";
            case CABIN:
                return "cabin";
            case SELECTED_SEAT:
                return seatZone.vehicleArea().orElse("");
            default:
                throw new IllegalStateException("CB_CONTEXT: unknown area scope");
        }
    }

    private static ContextField buildField(
            DigitalTwinSnapshot twin,
            FieldRequirement requirement,
            String area) {
        FieldKey key = new FieldKey(requirement.getPath(), area);
        if (area.isEmpty()) {
            return new ContextField(
                    key,
                    requirement.isRequired(),
                    FieldState.MISSING,
                    null,
                    null,
                    TrustLevel.UNKNOWN);
        }
        Optional<ReportedStateRecord> record = twin.reported(requirement.getPath(), area);
        if (record.isEmpty()) {
            return new ContextField(
                    key,
                    requirement.isRequired(),
                    FieldState.MISSING,
                    null,
                    null,
                    TrustLevel.UNKNOWN);
        }
        ReportedStateRecord reported = record.get();
        SignalQuality quality = reported.getEffectiveQuality();
        return new ContextField(
                key,
                requirement.isRequired(),
                state(quality),
                reported.getValue(),
                quality,
                trust(reported.getValue().getSource()));
    }

    private static FieldState state(SignalQuality quality) {
        switch (quality) {
            case VALID:
                return FieldState.AVAILABLE;
            case STALE:
                return FieldState.STALE;
            case UNAVAILABLE:
                return FieldState.UNAVAILABLE;
            case ERROR:
                return FieldState.ERROR;
            case CONFLICT:
                return FieldState.CONFLICT;
            default:
                throw new IllegalStateException("CB_CONTEXT: unknown signal quality");
        }
    }

    private static TrustLevel trust(SignalSource source) {
        switch (source) {
            case SIMULATED:
                return TrustLevel.SIMULATED;
            case AAOS:
            case VENDOR:
                return TrustLevel.PLATFORM_UNVERIFIED;
            case DERIVED:
                return TrustLevel.DERIVED_UNVERIFIED;
            default:
                throw new IllegalStateException("CB_CONTEXT: unknown signal source");
        }
    }

    private static DrivingState deriveSignalDrivingState(List<ContextField> fields) {
        Optional<SignalValue> speed = usableValue(fields, VehicleSignalPath.VEHICLE_SPEED);
        Optional<SignalValue> gear = usableValue(fields, VehicleSignalPath.CURRENT_GEAR);
        Optional<SignalValue> brake = usableValue(
                fields,
                VehicleSignalPath.PARKING_BRAKE_ENGAGED);
        if (speed.isEmpty() || gear.isEmpty() || brake.isEmpty()) {
            return DrivingState.UNKNOWN;
        }
        if (speed.get().getDecimalValue() > MOVING_SPEED_THRESHOLD_KPH) {
            return DrivingState.MOVING;
        }
        String gearValue = gear.get().getTextValue();
        boolean parkGear = "P".equalsIgnoreCase(gearValue)
                || "PARK".equalsIgnoreCase(gearValue);
        if (parkGear && brake.get().getBooleanValue()) {
            return DrivingState.PARKED;
        }
        return DrivingState.UNKNOWN;
    }

    private static Optional<SignalValue> usableValue(
            List<ContextField> fields,
            VehicleSignalPath path) {
        for (ContextField field : fields) {
            if (field.getKey().getPath() == path && field.isUsableForDecision()) {
                return field.getValue();
            }
        }
        return Optional.empty();
    }

    private static DrivingResolution reconcileDrivingState(
            DrivingState signalState,
            SafetyVehicleStateSnapshot.MotionState runtimeMotion,
            boolean runtimeStateFresh) {
        if (!runtimeStateFresh
                || runtimeMotion == SafetyVehicleStateSnapshot.MotionState.UNKNOWN) {
            return new DrivingResolution(DrivingState.UNKNOWN, false);
        }
        DrivingState runtimeState = runtimeMotion == SafetyVehicleStateSnapshot.MotionState.MOVING
                ? DrivingState.MOVING
                : DrivingState.PARKED;
        if (signalState == DrivingState.UNKNOWN) {
            return runtimeState == DrivingState.MOVING
                    ? new DrivingResolution(DrivingState.MOVING, false)
                    : new DrivingResolution(DrivingState.UNKNOWN, false);
        }
        if (signalState != runtimeState) {
            DrivingState conservative = signalState == DrivingState.MOVING
                    || runtimeState == DrivingState.MOVING
                    ? DrivingState.MOVING
                    : DrivingState.UNKNOWN;
            return new DrivingResolution(conservative, true);
        }
        return new DrivingResolution(signalState, false);
    }

    private static SourceMode sourceMode(List<ContextField> fields) {
        SourceMode mode = SourceMode.UNKNOWN;
        for (ContextField field : fields) {
            if (field.getValue().isEmpty()) {
                continue;
            }
            SourceMode current;
            switch (field.getTrustLevel()) {
                case SIMULATED:
                    current = SourceMode.SIMULATED;
                    break;
                case PLATFORM_UNVERIFIED:
                    current = SourceMode.PLATFORM_UNVERIFIED;
                    break;
                case DERIVED_UNVERIFIED:
                    current = SourceMode.DERIVED_UNVERIFIED;
                    break;
                case UNKNOWN:
                default:
                    current = SourceMode.UNKNOWN;
                    break;
            }
            if (mode == SourceMode.UNKNOWN) {
                mode = current;
            } else if (mode != current) {
                return SourceMode.MIXED;
            }
        }
        return mode;
    }

    private static String digest(
            DigitalTwinSnapshot twin,
            SafetyVehicleStateSnapshot runtimeState,
            ContextFieldPolicy policy,
            SeatZone seatZone,
            boolean profileMemoryAvailable,
            List<ContextField> fields,
            DrivingResolution driving,
            SafetyVehicleStateSnapshot.SafetyState safetyState,
            SourceMode sourceMode,
            boolean runtimeStateFresh,
            boolean requiredFreshnessComplete,
            boolean restricted,
            boolean productionTrusted) {
        MessageDigest digest = sha256();
        update(digest, "central-brain-context-v1");
        update(digest, Integer.toString(ContextSnapshot.SCHEMA_VERSION));
        update(digest, policy.getPolicyId());
        update(digest, Integer.toString(policy.getVersion()));
        update(digest, Long.toString(twin.getRevision()));
        update(digest, Long.toString(twin.getCapturedElapsedRealtimeMs()));
        update(digest, runtimeState.getSourceId());
        update(digest, Long.toString(runtimeState.getRevision()));
        update(digest, Long.toString(runtimeState.getCapturedAtElapsedRealtimeMs()));
        update(digest, runtimeState.getSafetyState().name());
        update(digest, runtimeState.getMotionState().name());
        update(digest, runtimeState.getSourceAssurance().name());
        update(digest, Boolean.toString(runtimeState.isHardwareBacked()));
        update(digest, seatZone.name());
        update(digest, Boolean.toString(profileMemoryAvailable));
        update(digest, driving.state.name());
        update(digest, Boolean.toString(driving.conflict));
        update(digest, safetyState.name());
        update(digest, sourceMode.name());
        update(digest, Boolean.toString(runtimeStateFresh));
        update(digest, Boolean.toString(requiredFreshnessComplete));
        update(digest, Boolean.toString(restricted));
        update(digest, Boolean.toString(productionTrusted));
        update(digest, Integer.toString(fields.size()));
        for (ContextField field : fields) {
            update(digest, field.getKey().getPath().getCanonicalPath());
            update(digest, field.getKey().getArea());
            update(digest, Boolean.toString(field.isRequired()));
            update(digest, field.getState().name());
            update(digest, field.getTrustLevel().name());
            Optional<SignalValue> value = field.getValue();
            if (value.isEmpty()) {
                update(digest, "");
                continue;
            }
            SignalValue signal = value.get();
            update(digest, signal.getQuality().name());
            update(digest, field.getEffectiveQuality().orElseThrow().name());
            update(digest, signal.getSource().name());
            update(digest, Long.toString(signal.getRevision()));
            update(digest, Long.toString(signal.getTimestamp().getSourceEpochMs()));
            update(digest, Long.toString(
                    signal.getTimestamp().getReceivedElapsedRealtimeMs()));
            update(digest, signal.getScalarType().name());
            update(digest, scalarValue(signal));
        }
        return toHex(digest.digest());
    }

    private static String scalarValue(SignalValue value) {
        if (!value.hasValue()) {
            return "";
        }
        switch (value.getScalarType()) {
            case BOOLEAN:
                return Boolean.toString(value.getBooleanValue());
            case INTEGER:
                return Long.toString(value.getIntegerValue());
            case DECIMAL:
                return Double.toHexString(value.getDecimalValue());
            case TEXT:
                return value.getTextValue();
            default:
                throw new IllegalStateException("CB_CONTEXT: unknown scalar type");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("CB_CONTEXT: SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String toHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            hex[index * 2] = alphabet[value >>> 4];
            hex[index * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(hex);
    }

    private static final class DrivingResolution {
        private final DrivingState state;
        private final boolean conflict;

        private DrivingResolution(DrivingState state, boolean conflict) {
            this.state = state;
            this.conflict = conflict;
        }
    }
}
