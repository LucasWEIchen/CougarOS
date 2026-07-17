package com.centralbrain.runtime.simulation;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.governance.SafetyVehicleStateProvider;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DesiredStateRecord;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;
import com.centralbrain.runtime.vehicle.twin.VehicleDigitalTwinStore;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Debug-only typed Seat adapter with dispatch-time recline safety revalidation. */
public final class SimulatedSeatEffectAdapter extends SimulatedEffectAdapter {
    public static final String ADAPTER_ID = "debug.simulated.seat.v1";
    public static final String DESTINATION = "vehicle.seat";
    public static final int TARGET_SCHEMA_VERSION = 1;
    public static final long DESIRED_TTL_MS = 180_000L;
    public static final long MAX_SAFETY_AGE_MS = 1_000L;
    public static final long MAX_OCCUPANT_AGE_MS = 1_000L;

    private static final int TARGET_MAGIC = 0x43425356;
    private static final int TARGET_FIXED_BYTES = Integer.BYTES * 6 + Long.BYTES;
    private static final long SYNTHETIC_SOURCE_EPOCH_BASE_MS = 1_710_000_000_000L;
    private static final Set<VehicleCapability.CapabilityId> SUPPORTED_CAPABILITIES =
            Collections.unmodifiableSet(EnumSet.of(
                    VehicleCapability.CapabilityId.SEAT_HEATING_LEVEL,
                    VehicleCapability.CapabilityId.SEAT_VENTILATION_LEVEL,
                    VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE));

    public interface SeatOccupantStateProvider {
        SeatOccupantSnapshot currentSnapshot(String area);
    }

    public interface SeatApprovalVerifier {
        boolean isApproved(
                String approvalDigest,
                String actionId,
                String area,
                long safetyRevision,
                long occupantRevision);

        default boolean isSimulationOnly() {
            return true;
        }

        default boolean isProductionAuthorized() {
            return false;
        }
    }

    public static final class SeatOccupantSnapshot {
        private final String area;
        private final long revision;
        private final long capturedAtElapsedRealtimeMs;
        private final boolean occupied;
        private final boolean belted;

        public SeatOccupantSnapshot(
                String area,
                long revision,
                long capturedAtElapsedRealtimeMs,
                boolean occupied,
                boolean belted) {
            this.area = requireArea(area);
            if (revision < 1 || capturedAtElapsedRealtimeMs < 0) {
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: occupant snapshot metadata is invalid");
            }
            this.revision = revision;
            this.capturedAtElapsedRealtimeMs = capturedAtElapsedRealtimeMs;
            this.occupied = occupied;
            this.belted = belted;
        }

        public String getArea() {
            return area;
        }

        public long getRevision() {
            return revision;
        }

        public long getCapturedAtElapsedRealtimeMs() {
            return capturedAtElapsedRealtimeMs;
        }

        public boolean isOccupied() {
            return occupied;
        }

        public boolean isBelted() {
            return belted;
        }

        public SignalSource getSource() {
            return SignalSource.SIMULATED;
        }

        public boolean isProductionTrusted() {
            return false;
        }
    }

    public static final class SeatTarget {
        private static final int KIND_INTEGER = 1;
        private static final int KIND_DECIMAL = 2;

        private final VehicleCapability.CapabilityId capabilityId;
        private final String area;
        private final int kind;
        private final long encodedValue;
        private final String approvalDigest;

        private SeatTarget(
                VehicleCapability.CapabilityId capabilityId,
                String area,
                int kind,
                long encodedValue,
                String approvalDigest) {
            this.capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
            this.area = requireArea(area);
            this.kind = kind;
            this.encodedValue = encodedValue;
            this.approvalDigest = approvalDigest == null ? "" : approvalDigest;
            if (!this.approvalDigest.isEmpty()
                    && !this.approvalDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: approval digest is invalid");
            }
        }

        public static SeatTarget heatingLevel(String area, long level) {
            return new SeatTarget(
                    VehicleCapability.CapabilityId.SEAT_HEATING_LEVEL,
                    area,
                    KIND_INTEGER,
                    level,
                    "");
        }

        public static SeatTarget ventilationLevel(String area, long level) {
            return new SeatTarget(
                    VehicleCapability.CapabilityId.SEAT_VENTILATION_LEVEL,
                    area,
                    KIND_INTEGER,
                    level,
                    "");
        }

        public static SeatTarget reclineAngle(
                String area,
                double angle,
                String approvalDigest) {
            if (!Double.isFinite(angle)) {
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: recline angle is not finite");
            }
            return new SeatTarget(
                    VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE,
                    area,
                    KIND_DECIMAL,
                    Double.doubleToLongBits(angle),
                    approvalDigest);
        }

        public VehicleCapability.CapabilityId getCapabilityId() {
            return capabilityId;
        }

        public String getArea() {
            return area;
        }

        public long getIntegerValue() {
            requireKind(KIND_INTEGER);
            return encodedValue;
        }

        public double getDecimalValue() {
            requireKind(KIND_DECIMAL);
            return Double.longBitsToDouble(encodedValue);
        }

        public String getApprovalDigest() {
            return approvalDigest;
        }

        public byte[] toCanonicalPayload() {
            byte[] areaBytes = area.getBytes(StandardCharsets.UTF_8);
            byte[] approvalBytes = approvalDigest.getBytes(StandardCharsets.US_ASCII);
            ByteBuffer output = ByteBuffer.allocate(
                    TARGET_FIXED_BYTES + areaBytes.length + approvalBytes.length);
            output.putInt(TARGET_MAGIC);
            output.putInt(TARGET_SCHEMA_VERSION);
            output.putInt(capabilityCode(capabilityId));
            output.putInt(areaBytes.length);
            output.put(areaBytes);
            output.putInt(kind);
            output.putLong(encodedValue);
            output.putInt(approvalBytes.length);
            output.put(approvalBytes);
            return output.array();
        }

        public static SeatTarget fromCanonicalPayload(byte[] payload) {
            Objects.requireNonNull(payload, "payload");
            if (payload.length < TARGET_FIXED_BYTES
                    || payload.length > TARGET_FIXED_BYTES + 32 + 64) {
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: payload size is invalid");
            }
            ByteBuffer input = ByteBuffer.wrap(payload);
            if (input.getInt() != TARGET_MAGIC
                    || input.getInt() != TARGET_SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: payload header is invalid");
            }
            VehicleCapability.CapabilityId capabilityId =
                    capabilityForCode(input.getInt());
            int areaLength = input.getInt();
            if (areaLength < 1
                    || areaLength > 32
                    || input.remaining() < areaLength + Integer.BYTES
                            + Long.BYTES + Integer.BYTES) {
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: payload area length is invalid");
            }
            byte[] areaBytes = new byte[areaLength];
            input.get(areaBytes);
            String area = decodeArea(areaBytes);
            int kind = input.getInt();
            long value = input.getLong();
            int approvalLength = input.getInt();
            if ((approvalLength != 0 && approvalLength != 64)
                    || input.remaining() != approvalLength) {
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: payload approval length is invalid");
            }
            byte[] approvalBytes = new byte[approvalLength];
            input.get(approvalBytes);
            String approval = new String(approvalBytes, StandardCharsets.US_ASCII);
            switch (capabilityId) {
                case SEAT_HEATING_LEVEL:
                    requireIntegerWithoutApproval(kind, approval);
                    return new SeatTarget(capabilityId, area, kind, value, "");
                case SEAT_VENTILATION_LEVEL:
                    requireIntegerWithoutApproval(kind, approval);
                    return new SeatTarget(capabilityId, area, kind, value, "");
                case SEAT_RECLINE_ANGLE:
                    if (kind != KIND_DECIMAL
                            || !Double.isFinite(Double.longBitsToDouble(value))
                            || !approval.matches("[0-9a-f]{64}")) {
                        throw new IllegalArgumentException(
                                "CB_SIM_SEAT: recline payload is invalid");
                    }
                    return new SeatTarget(capabilityId, area, kind, value, approval);
                default:
                    throw new IllegalArgumentException(
                            "CB_SIM_SEAT: payload capability is unsupported");
            }
        }

        private void requireKind(int expected) {
            if (kind != expected) {
                throw new IllegalStateException(
                        "CB_SIM_SEAT: target accessor type mismatch");
            }
        }

        private static void requireIntegerWithoutApproval(int kind, String approval) {
            if (kind != KIND_INTEGER || !approval.isEmpty()) {
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: level payload is invalid");
            }
        }

        private static int capabilityCode(VehicleCapability.CapabilityId id) {
            switch (id) {
                case SEAT_HEATING_LEVEL:
                    return 1;
                case SEAT_VENTILATION_LEVEL:
                    return 2;
                case SEAT_RECLINE_ANGLE:
                    return 3;
                default:
                    throw new IllegalArgumentException(
                            "CB_SIM_SEAT: capability is not Seat");
            }
        }

        private static VehicleCapability.CapabilityId capabilityForCode(int code) {
            switch (code) {
                case 1:
                    return VehicleCapability.CapabilityId.SEAT_HEATING_LEVEL;
                case 2:
                    return VehicleCapability.CapabilityId.SEAT_VENTILATION_LEVEL;
                case 3:
                    return VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE;
                default:
                    throw new IllegalArgumentException(
                            "CB_SIM_SEAT: capability code is unknown");
            }
        }
    }

    public static final class SeatProgressObservation {
        private final String idempotencyToken;
        private final int percent;
        private final double projectedAngle;
        private final boolean terminal;
        private final long observedAtElapsedRealtimeMs;

        private SeatProgressObservation(
                String idempotencyToken,
                int percent,
                double projectedAngle,
                boolean terminal,
                long observedAtElapsedRealtimeMs) {
            this.idempotencyToken = idempotencyToken;
            this.percent = percent;
            this.projectedAngle = projectedAngle;
            this.terminal = terminal;
            this.observedAtElapsedRealtimeMs = observedAtElapsedRealtimeMs;
        }

        public String getIdempotencyToken() {
            return idempotencyToken;
        }

        public int getPercent() {
            return percent;
        }

        public double getProjectedAngle() {
            return projectedAngle;
        }

        public boolean isTerminal() {
            return terminal;
        }

        public long getObservedAtElapsedRealtimeMs() {
            return observedAtElapsedRealtimeMs;
        }

        public SignalSource getSource() {
            return SignalSource.SIMULATED;
        }

        public boolean isProductionTrusted() {
            return false;
        }
    }

    private static final class SeatOperation {
        private final SeatTarget target;
        private final long startedAt;
        private final long durationMs;
        private final double initialAngle;

        private SeatOperation(
                SeatTarget target,
                long startedAt,
                long durationMs,
                double initialAngle) {
            this.target = target;
            this.startedAt = startedAt;
            this.durationMs = durationMs;
            this.initialAngle = initialAngle;
        }
    }

    private final CapabilityCatalog capabilityCatalog = CapabilityCatalog.stage2Defaults();
    private final SafetyVehicleStateProvider safetyProvider;
    private final SeatOccupantStateProvider occupantProvider;
    private final SeatApprovalVerifier approvalVerifier;
    private final Map<String, SeatOperation> operations = new LinkedHashMap<>();
    private VehicleDigitalTwinStore twinStore = new VehicleDigitalTwinStore();
    private long signalRevision;

    public SimulatedSeatEffectAdapter(
            SimulationClock clock,
            FaultInjectionProfile initialProfile,
            SafetyVehicleStateProvider safetyProvider,
            SeatOccupantStateProvider occupantProvider,
            SeatApprovalVerifier approvalVerifier) {
        super(ADAPTER_ID, DESTINATION, clock, initialProfile);
        this.safetyProvider = Objects.requireNonNull(safetyProvider, "safetyProvider");
        this.occupantProvider = Objects.requireNonNull(occupantProvider, "occupantProvider");
        this.approvalVerifier = Objects.requireNonNull(approvalVerifier, "approvalVerifier");
        if (!approvalVerifier.isSimulationOnly()
                || approvalVerifier.isProductionAuthorized()) {
            throw new IllegalArgumentException(
                    "CB_SIM_SEAT: approval verifier crosses simulation boundary");
        }
    }

    public Set<VehicleCapability.CapabilityId> getSupportedCapabilities() {
        return SUPPORTED_CAPABILITIES;
    }

    public synchronized DigitalTwinSnapshot getDigitalTwinSnapshot() {
        return twinStore.snapshot(
                Set.of(
                        VehicleSignalPath.SEAT_HEATING_LEVEL,
                        VehicleSignalPath.SEAT_VENTILATION_LEVEL,
                        VehicleSignalPath.SEAT_RECLINE_ANGLE),
                nowSimulationElapsedRealtimeMs());
    }

    public synchronized long getDigitalTwinRevision() {
        return twinStore.getRevision();
    }

    public synchronized SeatProgressObservation querySeatProgress(
            String idempotencyToken) {
        SeatOperation operation = operations.get(idempotencyToken);
        if (operation == null
                || operation.target.getCapabilityId()
                        != VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE) {
            throw new IllegalArgumentException(
                    "CB_SIM_SEAT: recline operation is not found");
        }
        EffectAdapter.StatusResult status = queryStatus(idempotencyToken);
        long now = nowSimulationElapsedRealtimeMs();
        int percent;
        if (status.getState() == EffectAdapter.DeliveryState.APPLIED) {
            percent = 100;
        } else if (status.getState() == EffectAdapter.DeliveryState.REJECTED) {
            percent = boundedPercent(operation, now, 99);
        } else {
            percent = boundedPercent(operation, now, 99);
        }
        double target = operation.target.getDecimalValue();
        double projected = operation.initialAngle
                + (target - operation.initialAngle) * percent / 100.0;
        boolean terminal = status.getState() == EffectAdapter.DeliveryState.APPLIED
                || status.getState() == EffectAdapter.DeliveryState.REJECTED;
        return new SeatProgressObservation(
                idempotencyToken, percent, projected, terminal, now);
    }

    @Override
    protected void validateSimulationInvocation(EffectAdapter.Invocation invocation) {
        SeatTarget target = decodeAndValidate(invocation);
        VehicleCapability capability = capabilityCatalog.require(target.getCapabilityId());
        if (!capability.getAvailability().isWritable()
                || !capability.getAvailability().isSimulatable()
                || capability.getAvailability().canUseProduction()) {
            throw new IllegalStateException(
                    "CB_SIM_SEAT: capability availability is unsafe");
        }
        if (!capability.getAreas().contains(target.getArea())) {
            throw new IllegalArgumentException(
                    "CB_SIM_SEAT: target area is unsupported");
        }
        validateRange(capability, target);
        SeatOccupantSnapshot occupant = requireOccupant(target.getArea());
        if (!occupant.isOccupied()) {
            throw new IllegalStateException("CB_SIM_SEAT: seat is not occupied");
        }
        if (target.getCapabilityId()
                == VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE) {
            requireReclineAllowed(invocation, target, occupant, false);
        }
    }

    @Override
    protected synchronized void onSimulationAdmitted(
            EffectAdapter.Invocation invocation,
            FaultInjectionProfile profile) {
        SeatTarget target = decodeAndValidate(invocation);
        SeatOccupantSnapshot occupant = requireOccupant(target.getArea());
        if (!occupant.isOccupied()) {
            throw new IllegalStateException("CB_SIM_SEAT: seat is not occupied");
        }
        if (target.getCapabilityId()
                == VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE) {
            requireReclineAllowed(invocation, target, occupant, false);
        }
        long now = nowSimulationElapsedRealtimeMs();
        twinStore.setDesired(
                desiredRecord(target, now, safeAdd(now, DESIRED_TTL_MS)),
                now);
        operations.put(
                invocation.getIdempotencyToken(),
                new SeatOperation(
                        target,
                        now,
                        profile.getMode() == FaultInjectionProfile.Mode.DELAY
                                ? profile.getDurationMs() : 0,
                        initialReclineAngle(target)));
    }

    @Override
    protected void validateSimulationDispatch(
            EffectAdapter.Invocation invocation,
            FaultInjectionProfile profile) {
        SeatTarget target = decodeAndValidate(invocation);
        if (target.getCapabilityId()
                != VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE) {
            return;
        }
        SeatOccupantSnapshot occupant;
        try {
            occupant = requireOccupant(target.getArea());
            requireReclineAllowed(invocation, target, occupant, true);
        } catch (RuntimeException unsafe) {
            rejectSimulationDispatch("SEAT_RECLINE_SAFETY_CHANGED");
        }
    }

    @Override
    protected synchronized void onSimulationApplied(
            EffectAdapter.Invocation invocation,
            FaultInjectionProfile profile) {
        SeatTarget desired = decodeAndValidate(invocation);
        SeatTarget reported = profile.getMode()
                == FaultInjectionProfile.Mode.READBACK_MISMATCH
                ? mismatchFor(desired) : desired;
        long now = nowSimulationElapsedRealtimeMs();
        long revision = nextSignalRevision();
        twinStore.updateReported(
                signalValue(
                        reported,
                        new SignalTimestamp(
                                safeAdd(SYNTHETIC_SOURCE_EPOCH_BASE_MS, revision),
                                now),
                        revision),
                now);
    }

    @Override
    protected synchronized void onSimulationReset() {
        operations.clear();
        twinStore = new VehicleDigitalTwinStore();
        signalRevision = 0;
    }

    private SeatTarget decodeAndValidate(EffectAdapter.Invocation invocation) {
        if (!DESTINATION.equals(invocation.getDestination())) {
            throw new IllegalArgumentException(
                    "CB_SIM_SEAT: destination is invalid");
        }
        SeatTarget target = SeatTarget.fromCanonicalPayload(
                invocation.getCanonicalPayload());
        if (!target.getCapabilityId().getCanonicalId()
                .equals(invocation.getActionId())) {
            throw new IllegalArgumentException(
                    "CB_SIM_SEAT: action does not match target capability");
        }
        return target;
    }

    private SeatOccupantSnapshot requireOccupant(String area) {
        SeatOccupantSnapshot snapshot = Objects.requireNonNull(
                occupantProvider.currentSnapshot(area),
                "occupant snapshot");
        long now = nowSimulationElapsedRealtimeMs();
        if (!area.equals(snapshot.getArea())
                || snapshot.getCapturedAtElapsedRealtimeMs() > now
                || now - snapshot.getCapturedAtElapsedRealtimeMs()
                        > MAX_OCCUPANT_AGE_MS) {
            throw new IllegalStateException(
                    "CB_SIM_SEAT: occupant snapshot is stale or mismatched");
        }
        return snapshot;
    }

    private void requireReclineAllowed(
            EffectAdapter.Invocation invocation,
            SeatTarget target,
            SeatOccupantSnapshot occupant,
            boolean dispatchTime) {
        SafetyVehicleStateSnapshot safety = Objects.requireNonNull(
                safetyProvider.currentSnapshot(),
                "safety snapshot");
        long now = nowSimulationElapsedRealtimeMs();
        if (safety.getCapturedAtElapsedRealtimeMs() > now
                || now - safety.getCapturedAtElapsedRealtimeMs()
                        > MAX_SAFETY_AGE_MS
                || safety.getSafetyState()
                        != SafetyVehicleStateSnapshot.SafetyState.NORMAL
                || safety.getMotionState()
                        != SafetyVehicleStateSnapshot.MotionState.PARKED
                || target.getArea().equals("row1.driver")
                        && !safety.isDriverAvailable()
                || !occupant.isOccupied()
                || occupant.isBelted()) {
            if (dispatchTime) {
                rejectSimulationDispatch("SEAT_RECLINE_SAFETY_DENIED");
            }
            throw new IllegalStateException(
                    "CB_SIM_SEAT: recline safety gate denied");
        }
        if (!approvalVerifier.isApproved(
                target.getApprovalDigest(),
                invocation.getActionId(),
                target.getArea(),
                safety.getRevision(),
                occupant.getRevision())) {
            if (dispatchTime) {
                rejectSimulationDispatch("SEAT_RECLINE_APPROVAL_STALE");
            }
            throw new IllegalStateException(
                    "CB_SIM_SEAT: recline approval is absent or stale");
        }
    }

    private static void validateRange(
            VehicleCapability capability,
            SeatTarget target) {
        switch (target.getCapabilityId()) {
            case SEAT_HEATING_LEVEL:
            case SEAT_VENTILATION_LEVEL:
                capability.getTargetRange().validateInteger(target.getIntegerValue());
                return;
            case SEAT_RECLINE_ANGLE:
                capability.getTargetRange().validateDecimal(target.getDecimalValue());
                return;
            default:
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: capability is unsupported");
        }
    }

    private static DesiredStateRecord desiredRecord(
            SeatTarget target,
            long requestedAt,
            long expiresAt) {
        switch (target.getCapabilityId()) {
            case SEAT_HEATING_LEVEL:
                return DesiredStateRecord.ofInteger(
                        VehicleSignalPath.SEAT_HEATING_LEVEL,
                        target.getIntegerValue(),
                        "level",
                        target.getArea(),
                        requestedAt,
                        expiresAt);
            case SEAT_VENTILATION_LEVEL:
                return DesiredStateRecord.ofInteger(
                        VehicleSignalPath.SEAT_VENTILATION_LEVEL,
                        target.getIntegerValue(),
                        "level",
                        target.getArea(),
                        requestedAt,
                        expiresAt);
            case SEAT_RECLINE_ANGLE:
                return DesiredStateRecord.ofDecimal(
                        VehicleSignalPath.SEAT_RECLINE_ANGLE,
                        target.getDecimalValue(),
                        "degree",
                        target.getArea(),
                        requestedAt,
                        expiresAt);
            default:
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: capability is unsupported");
        }
    }

    private static SignalValue signalValue(
            SeatTarget target,
            SignalTimestamp timestamp,
            long revision) {
        switch (target.getCapabilityId()) {
            case SEAT_HEATING_LEVEL:
                return SignalValue.ofInteger(
                        VehicleSignalPath.SEAT_HEATING_LEVEL,
                        target.getIntegerValue(),
                        "level",
                        target.getArea(),
                        timestamp,
                        SignalQuality.VALID,
                        SignalSource.SIMULATED,
                        revision);
            case SEAT_VENTILATION_LEVEL:
                return SignalValue.ofInteger(
                        VehicleSignalPath.SEAT_VENTILATION_LEVEL,
                        target.getIntegerValue(),
                        "level",
                        target.getArea(),
                        timestamp,
                        SignalQuality.VALID,
                        SignalSource.SIMULATED,
                        revision);
            case SEAT_RECLINE_ANGLE:
                return SignalValue.ofDecimal(
                        VehicleSignalPath.SEAT_RECLINE_ANGLE,
                        target.getDecimalValue(),
                        "degree",
                        target.getArea(),
                        timestamp,
                        SignalQuality.VALID,
                        SignalSource.SIMULATED,
                        revision);
            default:
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: capability is unsupported");
        }
    }

    private SeatTarget mismatchFor(SeatTarget target) {
        switch (target.getCapabilityId()) {
            case SEAT_HEATING_LEVEL:
                long heat = target.getIntegerValue();
                return SeatTarget.heatingLevel(
                        target.getArea(), heat < 3 ? heat + 1 : heat - 1);
            case SEAT_VENTILATION_LEVEL:
                long vent = target.getIntegerValue();
                return SeatTarget.ventilationLevel(
                        target.getArea(), vent < 3 ? vent + 1 : vent - 1);
            case SEAT_RECLINE_ANGLE:
                double angle = target.getDecimalValue();
                return SeatTarget.reclineAngle(
                        target.getArea(),
                        angle < 60.0 ? angle + 1.0 : angle - 1.0,
                        target.getApprovalDigest());
            default:
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: capability is unsupported");
        }
    }

    private double initialReclineAngle(SeatTarget target) {
        if (target.getCapabilityId()
                != VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE) {
            return 0;
        }
        return twinStore.reported(
                VehicleSignalPath.SEAT_RECLINE_ANGLE,
                target.getArea(),
                nowSimulationElapsedRealtimeMs())
                .map(record -> record.getValue().getDecimalValue())
                .orElse(0.0);
    }

    private static int boundedPercent(
            SeatOperation operation,
            long now,
            int maximum) {
        if (operation.durationMs <= 0) {
            return maximum;
        }
        long elapsed = Math.max(0, now - operation.startedAt);
        return (int) Math.min(maximum, elapsed * 100 / operation.durationMs);
    }

    private long nextSignalRevision() {
        if (signalRevision == Long.MAX_VALUE) {
            throw new IllegalStateException(
                    "CB_SIM_SEAT: signal revision exhausted");
        }
        signalRevision++;
        return signalRevision;
    }

    private static String requireArea(String area) {
        if (area == null || area.isEmpty() || area.length() > 32) {
            throw new IllegalArgumentException(
                    "CB_SIM_SEAT: target area is invalid");
        }
        for (int index = 0; index < area.length(); index++) {
            if (Character.isISOControl(area.charAt(index))) {
                throw new IllegalArgumentException(
                        "CB_SIM_SEAT: target area contains a control character");
            }
        }
        return area;
    }

    private static String decodeArea(byte[] bytes) {
        String area = new String(bytes, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(bytes, area.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException(
                    "CB_SIM_SEAT: target area is not canonical UTF-8");
        }
        return requireArea(area);
    }

    private static long safeAdd(long left, long right) {
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
            throw new IllegalArgumentException(
                    "CB_SIM_SEAT: simulated time overflow");
        }
        return left + right;
    }
}
