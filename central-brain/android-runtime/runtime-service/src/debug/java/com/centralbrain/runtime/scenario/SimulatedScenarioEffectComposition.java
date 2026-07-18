package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot;
import com.centralbrain.runtime.graph.AgentGraphRuntime;
import com.centralbrain.runtime.persistence.DurableDigest;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.DrivingProfile;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.Input;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.ScenarioKind;
import com.centralbrain.runtime.simulation.FaultInjectionProfile;
import com.centralbrain.runtime.simulation.SimulatedEffectAdapter;
import com.centralbrain.runtime.simulation.SimulatedHvacEffectAdapter;
import com.centralbrain.runtime.simulation.SimulatedMediaEffectAdapter;
import com.centralbrain.runtime.simulation.SimulatedNavigationEffectAdapter;
import com.centralbrain.runtime.simulation.SimulatedSeatEffectAdapter;
import com.centralbrain.runtime.simulation.SimulationClock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Debug-only composition that drives fixed scenario Effects through simulated adapters. */
public final class SimulatedScenarioEffectComposition {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID =
            "android13-p4-d4d-simulated-effect-composition-v1";
    public static final int ADAPTER_COUNT = 4;

    private static final String APPROVAL_DOMAIN =
            "central-brain-p4-d4d-simulated-approval-v1";
    private static final String IDEMPOTENCY_DOMAIN =
            "central-brain-p4-d4d-simulated-idempotency-v1";
    private static final String PROJECTION_DOMAIN =
            "central-brain-p4-d4d-simulated-projection-v1";
    private static final byte[] ENVELOPE_PREFIX =
            "central-brain-p4-d4d-envelope-v1".getBytes(StandardCharsets.US_ASCII);

    /** Metadata-only view for Binder/HMI. No target values or vehicle payload are exposed. */
    public static final class Snapshot {
        private final SimulatedScenarioRuntime.Snapshot runtime;
        private final int effectDispatchCount;
        private final int readbackAttemptCount;
        private final int readbackMatchCount;
        private final int approvalInputCount;
        private final int failureCount;
        private final String projectionDigest;

        private Snapshot(RunRecord record) {
            runtime = record.runtime;
            effectDispatchCount = record.effectDispatchCount;
            readbackAttemptCount = record.readbackAttemptCount;
            readbackMatchCount = record.readbackMatchCount;
            approvalInputCount = record.approvalInputCount;
            failureCount = record.failureCount;
            projectionDigest = DurableDigest.sha256(
                    PROJECTION_DOMAIN,
                    PROFILE_ID,
                    runtime.getProjectionDigest(),
                    Integer.toString(effectDispatchCount),
                    Integer.toString(readbackAttemptCount),
                    Integer.toString(readbackMatchCount),
                    Integer.toString(approvalInputCount),
                    Integer.toString(failureCount));
        }

        SimulatedScenarioRuntime.Snapshot getRuntimeSnapshot() {
            return runtime;
        }

        public String getRunId() {
            return runtime.getRunId();
        }

        public SimulatedScenarioRuntime.SessionState getSessionState() {
            return runtime.getSessionState();
        }

        public SimulatedScenarioGraph.PendingNode getPendingNode() {
            return runtime.getPendingNode();
        }

        public int getEffectDispatchCount() {
            return effectDispatchCount;
        }

        public int getReadbackAttemptCount() {
            return readbackAttemptCount;
        }

        public int getReadbackMatchCount() {
            return readbackMatchCount;
        }

        public int getApprovalInputCount() {
            return approvalInputCount;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public String getProjectionDigest() {
            return projectionDigest;
        }

        public boolean isSimulatedEffectDispatchEnabled() {
            return true;
        }

        public boolean isReadbackAccessed() {
            return readbackAttemptCount > 0;
        }

        public boolean isApprovalAuthorityAvailable() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }

        public boolean isProductionReady() {
            return false;
        }

        public boolean isTargetHardwareValidated() {
            return false;
        }
    }

    private static final class EffectBinding {
        private final SimulatedEffectAdapter adapter;
        private final String token;

        private EffectBinding(SimulatedEffectAdapter adapter, String token) {
            this.adapter = adapter;
            this.token = token;
        }
    }

    private static final class RunRecord {
        private final DrivingProfile driving;
        private final Map<String, EffectBinding> effects = new LinkedHashMap<>();
        private SimulatedScenarioRuntime.Snapshot runtime;
        private String approvalDigest = "";
        private int effectDispatchCount;
        private int readbackAttemptCount;
        private int readbackMatchCount;
        private int approvalInputCount;
        private int failureCount;

        private RunRecord(
                DrivingProfile driving,
                SimulatedScenarioRuntime.Snapshot runtime) {
            this.driving = driving;
            this.runtime = runtime;
        }
    }

    private final SimulatedScenarioRuntime runtime;
    private final SimulationClock simulationClock;
    private final SimulatedHvacEffectAdapter hvac;
    private final SimulatedSeatEffectAdapter seat;
    private final SimulatedMediaEffectAdapter media;
    private final SimulatedNavigationEffectAdapter navigation;
    private final Map<String, RunRecord> runs = new LinkedHashMap<>();
    private RunRecord activeRun;

    public SimulatedScenarioEffectComposition(
            AgentGraphRuntime.Clock graphClock,
            SimulationClock simulationClock) {
        runtime = new SimulatedScenarioRuntime(
                Objects.requireNonNull(graphClock, "graphClock"));
        this.simulationClock = Objects.requireNonNull(simulationClock, "simulationClock");
        hvac = new SimulatedHvacEffectAdapter(
                simulationClock, FaultInjectionProfile.none());
        seat = new SimulatedSeatEffectAdapter(
                simulationClock,
                FaultInjectionProfile.none(),
                this::activeSafetySnapshot,
                this::activeOccupantSnapshot,
                this::isActiveApprovalValid);
        media = new SimulatedMediaEffectAdapter(
                simulationClock, FaultInjectionProfile.none());
        navigation = new SimulatedNavigationEffectAdapter(
                simulationClock, FaultInjectionProfile.none());
    }

    public synchronized Snapshot start(
            Input input,
            ScenarioKind scenario,
            DrivingProfile driving) {
        Input requiredInput = Objects.requireNonNull(input, "input");
        ScenarioKind requiredScenario = Objects.requireNonNull(scenario, "scenario");
        DrivingProfile requiredDriving = Objects.requireNonNull(driving, "driving");
        SimulatedScenarioRuntime.Snapshot started = runtime.start(
                requiredInput.getRequest(),
                requiredInput.getResolution(),
                requiredInput.getContext(),
                requiredInput.getCapabilities());
        if (!requiredScenario.getScenarioId().equals(started.getScenarioId())) {
            runtime.cancel(started.getRunId());
            throw violation("scenario input binding mismatch");
        }
        RunRecord record = new RunRecord(requiredDriving, started);
        runs.put(started.getRunId(), record);
        return advance(record);
    }

    public synchronized Snapshot supplyApprovalOutcome(
            String runId,
            AgentGraphRuntime.NodeExecutionOutcome outcome) {
        RunRecord record = requireRun(runId);
        SimulatedScenarioGraph.PendingNode pending = record.runtime.getPendingNode();
        if (pending == null
                || pending.getStage() != SimulatedScenarioGraph.PendingStage.APPROVAL) {
            throw violation("run is not waiting for approval input");
        }
        AgentGraphRuntime.NodeExecutionOutcome requiredOutcome =
                Objects.requireNonNull(outcome, "outcome");
        record.approvalInputCount++;
        if (requiredOutcome == AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED) {
            record.approvalDigest = DurableDigest.sha256(
                    APPROVAL_DOMAIN,
                    record.runtime.getRunId(),
                    record.runtime.getPlanDigest(),
                    pending.getNodeId(),
                    Integer.toString(record.approvalInputCount));
        } else {
            record.approvalDigest = "";
        }
        record.runtime = runtime.supplyPendingOutcome(runId, requiredOutcome);
        return advance(record);
    }

    public synchronized Snapshot get(String runId) {
        RunRecord record = requireRun(runId);
        record.runtime = runtime.get(runId);
        return new Snapshot(record);
    }

    public synchronized Snapshot cancel(String runId) {
        RunRecord record = requireRun(runId);
        record.runtime = runtime.cancel(runId);
        return new Snapshot(record);
    }

    public synchronized int size() {
        return runs.size();
    }

    public synchronized void setFaultForContractTest(
            String capabilityId,
            FaultInjectionProfile profile) {
        adapterFor(requireCapability(capabilityId)).setFaultInjectionProfile(
                Objects.requireNonNull(profile, "profile"));
    }

    private Snapshot advance(RunRecord record) {
        while (!record.runtime.getGraphState().isTerminal()) {
            SimulatedScenarioGraph.PendingNode pending = record.runtime.getPendingNode();
            if (pending == null) {
                throw violation("non-terminal run has no pending node");
            }
            switch (pending.getStage()) {
                case APPROVAL:
                    return new Snapshot(record);
                case EFFECT:
                    dispatch(record, pending);
                    break;
                case READBACK:
                    verify(record, pending);
                    break;
                default:
                    throw violation("pending node stage is unsupported");
            }
        }
        return new Snapshot(record);
    }

    private void dispatch(
            RunRecord record,
            SimulatedScenarioGraph.PendingNode pending) {
        AgentGraphRuntime.NodeExecutionOutcome outcome =
                AgentGraphRuntime.NodeExecutionOutcome.FAILED;
        try {
            EffectAdapter.Invocation invocation = invocation(record, pending);
            SimulatedEffectAdapter adapter = adapterFor(pending.getCapabilityId());
            activeRun = record;
            EffectAdapter.ApplyResult result = adapter.apply(invocation);
            record.effectDispatchCount++;
            if (result.getState() == EffectAdapter.ApplyState.APPLIED) {
                record.effects.put(
                        pending.getCapabilityId(),
                        new EffectBinding(adapter, invocation.getIdempotencyToken()));
                outcome = AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED;
            } else {
                record.failureCount++;
            }
        } catch (RuntimeException failure) {
            record.effectDispatchCount++;
            record.failureCount++;
        } finally {
            activeRun = null;
        }
        record.runtime = runtime.supplyPendingOutcome(
                record.runtime.getRunId(), outcome);
    }

    private void verify(
            RunRecord record,
            SimulatedScenarioGraph.PendingNode pending) {
        AgentGraphRuntime.NodeExecutionOutcome outcome =
                AgentGraphRuntime.NodeExecutionOutcome.FAILED;
        record.readbackAttemptCount++;
        try {
            EffectBinding binding = record.effects.remove(pending.getCapabilityId());
            if (binding == null) {
                throw violation("readback has no bound simulated Effect");
            }
            activeRun = record;
            SimulatedEffectAdapter.SimulationObservation observation =
                    binding.adapter.querySimulationObservation(binding.token);
            if (observation.getState() == SimulatedEffectAdapter.ReadbackState.MATCHED
                    && observation.getSource()
                            == com.centralbrain.runtime.vehicle.schema.SignalSource.SIMULATED
                    && !observation.isProductionTrusted()) {
                record.readbackMatchCount++;
                outcome = AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED;
            } else {
                record.failureCount++;
            }
        } catch (RuntimeException failure) {
            record.failureCount++;
        } finally {
            activeRun = null;
        }
        record.runtime = runtime.supplyPendingOutcome(
                record.runtime.getRunId(), outcome);
    }

    private EffectAdapter.Invocation invocation(
            RunRecord record,
            SimulatedScenarioGraph.PendingNode pending) {
        String capabilityId = requireCapability(pending.getCapabilityId());
        SimulatedEffectAdapter adapter = adapterFor(capabilityId);
        byte[] payload = payload(record, capabilityId);
        byte[] envelope = (new String(ENVELOPE_PREFIX, StandardCharsets.US_ASCII)
                + '|' + record.runtime.getRunId()
                + '|' + pending.getNodeId()
                + '|' + pending.getInputDigest()).getBytes(StandardCharsets.US_ASCII);
        String token = DurableDigest.sha256(
                IDEMPOTENCY_DOMAIN,
                record.runtime.getRunId(),
                pending.getNodeId(),
                pending.getIdempotencyKey(),
                pending.getInputDigest());
        return new EffectAdapter.Invocation(
                "sim-effect-" + pending.getNodeId(),
                "sim-outbox-" + pending.getNodeId(),
                token,
                adapter.descriptor().getDestination(),
                capabilityId,
                sha256(payload),
                sha256(envelope),
                1,
                pending.getMaxAttempts(),
                payload,
                envelope);
    }

    private byte[] payload(RunRecord record, String capabilityId) {
        switch (capabilityId) {
            case "vehicle.hvac.power":
                return SimulatedHvacEffectAdapter.HvacTarget.power(true)
                        .toCanonicalPayload();
            case "vehicle.hvac.target_temperature":
                return SimulatedHvacEffectAdapter.HvacTarget.targetTemperature(
                        "row1.driver", 23.0).toCanonicalPayload();
            case "vehicle.hvac.fan_level":
                return SimulatedHvacEffectAdapter.HvacTarget.fanLevel(
                        "cabin", 3).toCanonicalPayload();
            case "vehicle.seat.heating":
                return SimulatedSeatEffectAdapter.SeatTarget.heatingLevel(
                        "row1.driver", 2).toCanonicalPayload();
            case "vehicle.seat.recline":
                if (record.approvalDigest.isEmpty()) {
                    throw violation("seat recline has no explicit simulation approval");
                }
                return SimulatedSeatEffectAdapter.SeatTarget.reclineAngle(
                        "row1.driver", 30.0, record.approvalDigest)
                        .toCanonicalPayload();
            case "media.playback":
                return SimulatedMediaEffectAdapter.MediaTarget.playback(
                        SimulatedMediaEffectAdapter.PlaybackCommand.PAUSE)
                        .toCanonicalPayload();
            case "navigation.poi":
                return SimulatedNavigationEffectAdapter.NavigationTarget.poi(
                        "nearby rest area").toCanonicalPayload();
            default:
                throw violation("capability has no fixed simulated target");
        }
    }

    private SimulatedEffectAdapter adapterFor(String capabilityId) {
        switch (capabilityId) {
            case "vehicle.hvac.power":
            case "vehicle.hvac.target_temperature":
            case "vehicle.hvac.fan_level":
                return hvac;
            case "vehicle.seat.heating":
            case "vehicle.seat.ventilation":
            case "vehicle.seat.recline":
                return seat;
            case "media.playback":
                return media;
            case "navigation.poi":
                return navigation;
            default:
                throw violation("capability has no simulated adapter");
        }
    }

    private SafetyVehicleStateSnapshot activeSafetySnapshot() {
        RunRecord record = requireActiveRun();
        boolean parked = record.driving == DrivingProfile.PARKED;
        return new SafetyVehicleStateSnapshot(
                "debug.scenario.effect.composition",
                Math.max(1, record.runtime.getGraphRevision()),
                simulationClock.nowElapsedRealtimeMs(),
                SafetyVehicleStateSnapshot.SafetyState.NORMAL,
                parked
                        ? SafetyVehicleStateSnapshot.MotionState.PARKED
                        : SafetyVehicleStateSnapshot.MotionState.MOVING,
                true,
                SafetyVehicleStateSnapshot.SourceAssurance.RUNTIME_OWNED_STUB,
                false);
    }

    private SimulatedSeatEffectAdapter.SeatOccupantSnapshot activeOccupantSnapshot(
            String area) {
        RunRecord record = requireActiveRun();
        return new SimulatedSeatEffectAdapter.SeatOccupantSnapshot(
                area,
                Math.max(1, record.runtime.getGraphRevision()),
                simulationClock.nowElapsedRealtimeMs(),
                true,
                record.driving != DrivingProfile.PARKED);
    }

    private boolean isActiveApprovalValid(
            String approvalDigest,
            String actionId,
            String area,
            long safetyRevision,
            long occupantRevision) {
        RunRecord record = requireActiveRun();
        return record.driving == DrivingProfile.PARKED
                && "vehicle.seat.recline".equals(actionId)
                && "row1.driver".equals(area)
                && safetyRevision > 0
                && occupantRevision > 0
                && !record.approvalDigest.isEmpty()
                && record.approvalDigest.equals(approvalDigest);
    }

    private RunRecord requireRun(String runId) {
        if (runId == null || !runId.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            throw violation("run id is invalid");
        }
        RunRecord record = runs.get(runId);
        if (record == null) {
            throw violation("run is unknown");
        }
        return record;
    }

    private RunRecord requireActiveRun() {
        if (activeRun == null) {
            throw violation("simulated adapter escaped composition scope");
        }
        return activeRun;
    }

    private static String requireCapability(String capabilityId) {
        if (capabilityId == null
                || !capabilityId.matches("[a-z][a-z0-9_.]{2,127}")) {
            throw violation("capability id is invalid");
        }
        return capabilityId;
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            char[] output = new char[digest.length * 2];
            char[] digits = "0123456789abcdef".toCharArray();
            for (int index = 0; index < digest.length; index++) {
                int current = digest[index] & 0xff;
                output[index * 2] = digits[current >>> 4];
                output[index * 2 + 1] = digits[current & 0x0f];
            }
            return new String(output);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_SIM_SCENARIO_EFFECT: " + message);
    }
}
