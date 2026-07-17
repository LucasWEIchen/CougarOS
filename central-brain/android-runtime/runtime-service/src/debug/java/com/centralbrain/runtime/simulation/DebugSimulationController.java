package com.centralbrain.runtime.simulation;

import com.centralbrain.runtime.context.ContextSnapshot.DrivingState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded process-local state controller for Android debug/test simulation only. */
public final class DebugSimulationController {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_AUDIT_ENTRIES = 128;
    public static final long DEFAULT_INITIAL_ELAPSED_REALTIME_MS = 1_000L;
    private static final long SYNTHETIC_SOURCE_EPOCH_BASE_MS = 1_710_000_000_000L;

    public enum Command {
        SET_DRIVING_STATE,
        SET_SIGNAL,
        SET_ADAPTER_FAULT,
        ADVANCE_CLOCK,
        RESET
    }

    public enum Outcome {
        APPLIED,
        REJECTED
    }

    public static final class AuditEntry {
        private final long sequence;
        private final Command command;
        private final Outcome outcome;
        private final String targetDigest;
        private final long controllerRevision;
        private final long elapsedRealtimeMs;

        private AuditEntry(
                long sequence,
                Command command,
                Outcome outcome,
                String targetDigest,
                long controllerRevision,
                long elapsedRealtimeMs) {
            this.sequence = sequence;
            this.command = command;
            this.outcome = outcome;
            this.targetDigest = targetDigest;
            this.controllerRevision = controllerRevision;
            this.elapsedRealtimeMs = elapsedRealtimeMs;
        }

        public long getSequence() {
            return sequence;
        }

        public Command getCommand() {
            return command;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public String getTargetDigest() {
            return targetDigest;
        }

        public long getControllerRevision() {
            return controllerRevision;
        }

        public long getElapsedRealtimeMs() {
            return elapsedRealtimeMs;
        }
    }

    public static final class Snapshot {
        private final long revision;
        private final DrivingState drivingState;
        private final Map<String, SignalValue> signals;
        private final Map<String, FaultInjectionProfile> faults;
        private final long elapsedRealtimeMs;
        private final int auditEntryCount;
        private final String digest;

        private Snapshot(
                long revision,
                DrivingState drivingState,
                Map<String, SignalValue> signals,
                Map<String, FaultInjectionProfile> faults,
                long elapsedRealtimeMs,
                int auditEntryCount,
                String digest) {
            this.revision = revision;
            this.drivingState = drivingState;
            this.signals = Collections.unmodifiableMap(new LinkedHashMap<>(signals));
            this.faults = Collections.unmodifiableMap(new LinkedHashMap<>(faults));
            this.elapsedRealtimeMs = elapsedRealtimeMs;
            this.auditEntryCount = auditEntryCount;
            this.digest = digest;
        }

        public long getRevision() {
            return revision;
        }

        public DrivingState getDrivingState() {
            return drivingState;
        }

        public Map<String, SignalValue> getSignals() {
            return signals;
        }

        public Map<String, FaultInjectionProfile> getFaults() {
            return faults;
        }

        public long getElapsedRealtimeMs() {
            return elapsedRealtimeMs;
        }

        public int getAuditEntryCount() {
            return auditEntryCount;
        }

        public String getDigest() {
            return digest;
        }

        public boolean isSimulationOnly() {
            return true;
        }

        public boolean isProductionTrusted() {
            return false;
        }
    }

    private final long initialElapsedRealtimeMs;
    private final SimulationClock clock;
    private final Map<String, SimulatedEffectAdapter> adapters = new LinkedHashMap<>();
    private final Map<String, SignalValue> signals = new LinkedHashMap<>();
    private final Map<String, FaultInjectionProfile> faults = new LinkedHashMap<>();
    private final Deque<AuditEntry> audit = new ArrayDeque<>();
    private DrivingState drivingState = DrivingState.UNKNOWN;
    private volatile long revision;
    private long auditSequence;

    public DebugSimulationController() {
        this(DEFAULT_INITIAL_ELAPSED_REALTIME_MS);
    }

    public DebugSimulationController(long initialElapsedRealtimeMs) {
        this.initialElapsedRealtimeMs = requireInitialTime(initialElapsedRealtimeMs);
        this.clock = new SimulationClock(this.initialElapsedRealtimeMs);
        register(new SimulatedHvacEffectAdapter(clock, FaultInjectionProfile.none()));
        register(new SimulatedSeatEffectAdapter(
                clock,
                FaultInjectionProfile.none(),
                this::failClosedSafetySnapshot,
                this::failClosedOccupantSnapshot,
                (approvalDigest, actionId, area, safetyRevision, occupantRevision) -> false));
        register(new SimulatedMediaEffectAdapter(clock, FaultInjectionProfile.none()));
        register(new SimulatedNavigationEffectAdapter(clock, FaultInjectionProfile.none()));
    }

    public synchronized long setDrivingState(DrivingState state) {
        String target = state == null ? "null" : state.name();
        try {
            drivingState = Objects.requireNonNull(state, "state");
            revision++;
            appendAudit(Command.SET_DRIVING_STATE, Outcome.APPLIED, target);
            return revision;
        } catch (RuntimeException exception) {
            appendAudit(Command.SET_DRIVING_STATE, Outcome.REJECTED, target);
            throw exception;
        }
    }

    public synchronized long setSignal(
            VehicleSignalPath path,
            String area,
            SignalValue.ScalarType scalarType,
            boolean booleanValue,
            long integerValue,
            double decimalValue,
            String textValue) {
        String target = String.valueOf(path) + "|" + String.valueOf(area)
                + "|" + String.valueOf(scalarType);
        try {
            VehicleSignalPath requiredPath = Objects.requireNonNull(path, "path");
            SignalValue.ScalarType requiredType = Objects.requireNonNull(scalarType, "scalarType");
            if (requiredPath.getScalarType() != requiredType) {
                throw new IllegalArgumentException(
                        "CB_DEBUG_SIM: scalar type does not match canonical path");
            }
            long nextRevision = revision + 1;
            SignalTimestamp timestamp = new SignalTimestamp(
                    SYNTHETIC_SOURCE_EPOCH_BASE_MS + clock.nowElapsedRealtimeMs(),
                    clock.nowElapsedRealtimeMs());
            SignalValue value = newSignalValue(
                    requiredPath,
                    Objects.requireNonNull(area, "area"),
                    requiredType,
                    booleanValue,
                    integerValue,
                    decimalValue,
                    textValue,
                    timestamp,
                    nextRevision);
            signals.put(signalKey(requiredPath, area), value);
            revision = nextRevision;
            appendAudit(Command.SET_SIGNAL, Outcome.APPLIED, target + "|" + scalarDigest(value));
            return revision;
        } catch (RuntimeException exception) {
            appendAudit(Command.SET_SIGNAL, Outcome.REJECTED, target);
            throw exception;
        }
    }

    public synchronized long setAdapterFault(
            String adapterId,
            FaultInjectionProfile profile) {
        String target = String.valueOf(adapterId) + "|"
                + (profile == null ? "null" : profile.getDigest());
        try {
            SimulatedEffectAdapter adapter = adapters.get(adapterId);
            if (adapter == null) {
                throw new IllegalArgumentException("CB_DEBUG_SIM: adapter is not allowlisted");
            }
            FaultInjectionProfile requiredProfile = Objects.requireNonNull(profile, "profile");
            adapter.setFaultInjectionProfile(requiredProfile);
            if (requiredProfile.getMode() == FaultInjectionProfile.Mode.NONE) {
                faults.remove(adapterId);
            } else {
                faults.put(adapterId, requiredProfile);
            }
            revision++;
            appendAudit(Command.SET_ADAPTER_FAULT, Outcome.APPLIED, target);
            return revision;
        } catch (RuntimeException exception) {
            appendAudit(Command.SET_ADAPTER_FAULT, Outcome.REJECTED, target);
            throw exception;
        }
    }

    public synchronized long advanceSimulationClock(long durationMs) {
        try {
            clock.advanceBy(durationMs);
            revision++;
            appendAudit(Command.ADVANCE_CLOCK, Outcome.APPLIED, Long.toString(durationMs));
            return revision;
        } catch (RuntimeException exception) {
            appendAudit(Command.ADVANCE_CLOCK, Outcome.REJECTED, Long.toString(durationMs));
            throw exception;
        }
    }

    public synchronized long reset() {
        for (SimulatedEffectAdapter adapter : adapters.values()) {
            adapter.reset();
            adapter.setFaultInjectionProfile(FaultInjectionProfile.none());
        }
        signals.clear();
        faults.clear();
        drivingState = DrivingState.UNKNOWN;
        clock.resetTo(initialElapsedRealtimeMs);
        revision++;
        appendAudit(Command.RESET, Outcome.APPLIED, "simulation-state");
        return revision;
    }

    public synchronized Snapshot snapshot() {
        Map<String, SignalValue> signalCopy = new LinkedHashMap<>(signals);
        Map<String, FaultInjectionProfile> faultCopy = new LinkedHashMap<>(faults);
        long now = clock.nowElapsedRealtimeMs();
        return new Snapshot(
                revision,
                drivingState,
                signalCopy,
                faultCopy,
                now,
                audit.size(),
                snapshotDigest(signalCopy, faultCopy, now));
    }

    public synchronized List<AuditEntry> auditEntries() {
        return Collections.unmodifiableList(new ArrayList<>(audit));
    }

    public synchronized Optional<SignalValue> getSignal(
            VehicleSignalPath path, String area) {
        return Optional.ofNullable(signals.get(signalKey(path, area)));
    }

    public synchronized FaultInjectionProfile getAdapterFault(String adapterId) {
        SimulatedEffectAdapter adapter = adapters.get(adapterId);
        if (adapter == null) {
            throw new IllegalArgumentException("CB_DEBUG_SIM: adapter is not allowlisted");
        }
        return adapter.getFaultInjectionProfile();
    }

    public synchronized int getAdapterCount() {
        return adapters.size();
    }

    public boolean isSimulationOnly() {
        return true;
    }

    public boolean isProductionAuthorized() {
        return false;
    }

    private void register(SimulatedEffectAdapter adapter) {
        String adapterId = adapter.simulationDescriptor().getAdapterId();
        if (adapters.put(adapterId, adapter) != null
                || !adapter.simulationDescriptor().isSimulation()
                || adapter.simulationDescriptor().isProductionAuthorized()) {
            throw new IllegalArgumentException("CB_DEBUG_SIM: unsafe or duplicate adapter");
        }
    }

    private SafetyVehicleStateSnapshot failClosedSafetySnapshot() {
        return new SafetyVehicleStateSnapshot(
                "debug.simulation.controller",
                Math.max(1, revision),
                clock.nowElapsedRealtimeMs(),
                SafetyVehicleStateSnapshot.SafetyState.UNKNOWN,
                SafetyVehicleStateSnapshot.MotionState.UNKNOWN,
                false,
                SafetyVehicleStateSnapshot.SourceAssurance.RUNTIME_OWNED_STUB,
                false);
    }

    private SimulatedSeatEffectAdapter.SeatOccupantSnapshot failClosedOccupantSnapshot(
            String area) {
        return new SimulatedSeatEffectAdapter.SeatOccupantSnapshot(
                area,
                Math.max(1, revision),
                clock.nowElapsedRealtimeMs(),
                false,
                true);
    }

    private void appendAudit(Command command, Outcome outcome, String targetMaterial) {
        auditSequence++;
        if (audit.size() == MAX_AUDIT_ENTRIES) {
            audit.removeFirst();
        }
        audit.addLast(new AuditEntry(
                auditSequence,
                command,
                outcome,
                digest("central-brain-debug-simulation-audit-v1", targetMaterial),
                revision,
                clock.nowElapsedRealtimeMs()));
    }

    private String snapshotDigest(
            Map<String, SignalValue> signalSnapshot,
            Map<String, FaultInjectionProfile> faultSnapshot,
            long now) {
        List<String> material = new ArrayList<>();
        material.add("schema=" + SCHEMA_VERSION);
        material.add("revision=" + revision);
        material.add("driving=" + drivingState.name());
        material.add("elapsed=" + now);
        material.add("auditSequence=" + auditSequence);
        signalSnapshot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> material.add(
                        "signal=" + entry.getKey() + "|" + scalarDigest(entry.getValue())));
        faultSnapshot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> material.add(
                        "fault=" + entry.getKey() + "|" + entry.getValue().getDigest()));
        return digest("central-brain-debug-simulation-snapshot-v1", String.join("\n", material));
    }

    private static SignalValue newSignalValue(
            VehicleSignalPath path,
            String area,
            SignalValue.ScalarType scalarType,
            boolean booleanValue,
            long integerValue,
            double decimalValue,
            String textValue,
            SignalTimestamp timestamp,
            long revision) {
        switch (scalarType) {
            case BOOLEAN:
                requireUnused(integerValue == 0 && decimalValue == 0.0 && empty(textValue));
                return SignalValue.ofBoolean(
                        path, booleanValue, path.getUnit(), area, timestamp,
                        SignalQuality.VALID, SignalSource.SIMULATED, revision);
            case INTEGER:
                requireUnused(!booleanValue && decimalValue == 0.0 && empty(textValue));
                return SignalValue.ofInteger(
                        path, integerValue, path.getUnit(), area, timestamp,
                        SignalQuality.VALID, SignalSource.SIMULATED, revision);
            case DECIMAL:
                requireUnused(!booleanValue && integerValue == 0 && empty(textValue));
                return SignalValue.ofDecimal(
                        path, decimalValue, path.getUnit(), area, timestamp,
                        SignalQuality.VALID, SignalSource.SIMULATED, revision);
            case TEXT:
                requireUnused(!booleanValue && integerValue == 0 && decimalValue == 0.0);
                return SignalValue.ofText(
                        path, Objects.requireNonNull(textValue, "textValue"), path.getUnit(), area,
                        timestamp, SignalQuality.VALID, SignalSource.SIMULATED, revision);
            default:
                throw new IllegalArgumentException("CB_DEBUG_SIM: scalar type is unsupported");
        }
    }

    private static void requireUnused(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException(
                    "CB_DEBUG_SIM: unused scalar fields must carry canonical defaults");
        }
    }

    private static boolean empty(String value) {
        return value != null && value.isEmpty();
    }

    private static String signalKey(VehicleSignalPath path, String area) {
        return Objects.requireNonNull(path, "path").getCanonicalPath()
                + "|" + Objects.requireNonNull(area, "area");
    }

    private static String scalarDigest(SignalValue value) {
        String scalar;
        switch (value.getScalarType()) {
            case BOOLEAN:
                scalar = Boolean.toString(value.getBooleanValue());
                break;
            case INTEGER:
                scalar = Long.toString(value.getIntegerValue());
                break;
            case DECIMAL:
                scalar = Long.toHexString(Double.doubleToLongBits(value.getDecimalValue()));
                break;
            case TEXT:
                scalar = value.getTextValue();
                break;
            default:
                throw new IllegalStateException("CB_DEBUG_SIM: scalar type is unsupported");
        }
        return digest(
                "central-brain-debug-simulation-signal-v1",
                value.getPath().getCanonicalPath(),
                value.getArea(),
                value.getScalarType().name(),
                scalar,
                Long.toString(value.getRevision()));
    }

    private static String digest(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = Objects.requireNonNull(value, "digest value")
                        .getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            StringBuilder output = new StringBuilder(64);
            for (byte value : digest.digest()) {
                output.append(String.format("%02x", value & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("CB_DEBUG_SIM: SHA-256 unavailable", exception);
        }
    }

    private static long requireInitialTime(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("CB_DEBUG_SIM: initial time is invalid");
        }
        return value;
    }
}
