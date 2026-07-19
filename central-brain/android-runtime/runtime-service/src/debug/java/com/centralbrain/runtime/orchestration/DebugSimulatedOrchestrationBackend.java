package com.centralbrain.runtime.orchestration;

import android.content.Context;
import android.os.SystemClock;

import com.centralbrain.runtime.graph.AgentGraphRuntime;
import com.centralbrain.runtime.graph.NodeRunState;
import com.centralbrain.runtime.scenario.ScenarioCatalog;
import com.centralbrain.runtime.scenario.SimulatedScenarioEffectComposition;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.DrivingProfile;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.ScenarioKind;
import com.centralbrain.runtime.scenario.SimulatedScenarioRuntime;
import com.centralbrain.runtime.simulation.SimulationClock;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.orchestration.ApprovalResponse;
import com.centralbrain.sdk.orchestration.ICentralBrainOrchestration;
import com.centralbrain.sdk.orchestration.OrchestrationContract;
import com.centralbrain.sdk.orchestration.OrchestrationEffect;
import com.centralbrain.sdk.orchestration.OrchestrationNode;
import com.centralbrain.sdk.orchestration.OrchestrationSnapshot;
import com.centralbrain.sdk.orchestration.OrchestrationStartRequest;
import com.centralbrain.sdk.orchestration.UndoRequest;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Debug-only adapter from the production orchestration contract to the fixed simulator. */
final class DebugSimulatedOrchestrationBackend implements OrchestrationBackend {
    private static final int MAX_ASSET_BYTES = 64 * 1024;
    private static final int MAX_RUNS = 16;

    private final FailClosedOrchestrationBackend failClosed =
            new FailClosedOrchestrationBackend();
    private final DebugDecisionCompositionBoundary decisionComposition;
    private final DebugRuntimeCompositionBoundary runtimeComposition;
    private final SimulatedScenarioEffectComposition composition;
    private final SimulatedScenarioInputFactory inputs;
    private final Map<String, RunRecord> bySession = new LinkedHashMap<>();

    DebugSimulatedOrchestrationBackend(Context context) {
        Objects.requireNonNull(context, "context");
        AgentGraphRuntime.Clock clock = new AgentGraphRuntime.Clock() {
            @Override
            public long epochTimeMs() {
                return System.currentTimeMillis();
            }

            @Override
            public long elapsedRealtimeMs() {
                return SystemClock.elapsedRealtime();
            }
        };
        composition = new SimulatedScenarioEffectComposition(
                clock, new SimulationClock(SystemClock.elapsedRealtime()));
        ScenarioCatalog catalog = loadCatalog(context);
        decisionComposition = new DebugDecisionCompositionBoundary(catalog);
        runtimeComposition = new DebugRuntimeCompositionBoundary();
        inputs = new SimulatedScenarioInputFactory(
                catalog,
                clock::epochTimeMs,
                clock::elapsedRealtimeMs,
                () -> UUID.randomUUID().toString());
    }

    @Override
    public synchronized Result start(
            SessionDescriptor session,
            OrchestrationStartRequest request) {
        if (request.executionProfile != ICentralBrainOrchestration.PROFILE_DEBUG_SIMULATION) {
            return failClosed.start(session, request);
        }
        ScenarioKind scenario = scenario(request.scenarioId);
        DrivingProfile driving = driving(request.simulationMotionState);
        if (scenario == null) {
            return OrchestrationProjectionFactory.blocked(
                    session, "SCENARIO_SIMULATION_UNAVAILABLE", System.currentTimeMillis());
        }
        if (driving == null) {
            return OrchestrationProjectionFactory.blocked(
                    session, "SIMULATION_MOTION_REQUIRED", System.currentTimeMillis());
        }

        RunRecord existing = bySession.get(session.getSessionId());
        String requestDigest = OrchestrationProjectionFactory.digest(
                "central-brain-debug-orchestration-start-v1",
                request.requestId,
                request.sessionId,
                request.scenarioId,
                Integer.toString(request.executionProfile),
                Integer.toString(request.simulationMotionState));
        if (existing != null) {
            if (!existing.requestId.equals(request.requestId)
                    || !existing.requestDigest.equals(requestDigest)) {
                throw violation("Session already owns a different orchestration request");
            }
            return result(existing, composition.get(existing.runId));
        }
        if (bySession.size() >= MAX_RUNS) {
            throw violation("debug orchestration capacity exhausted");
        }

        DebugDecisionCompositionBoundary.Evidence decisionEvidence =
                decisionComposition.prepare(session, request.scenarioId, requestDigest);
        final DebugRuntimeCompositionBoundary.Evidence compositionEvidence;
        try {
            compositionEvidence =
                    runtimeComposition.prepare(session, request.scenarioId, requestDigest);
        } catch (RuntimeException failure) {
            decisionComposition.complete(session, decisionEvidence);
            throw failure;
        }
        final SimulatedScenarioEffectComposition.Snapshot started;
        try {
            started = composition.start(
                    inputs.createForSession(
                            scenario,
                            driving,
                            session.getSessionId(),
                            session.getDeadlineEpochMs()),
                    scenario,
                    driving);
        } catch (RuntimeException failure) {
            runtimeComposition.complete(session, compositionEvidence);
            decisionComposition.complete(session, decisionEvidence);
            throw failure;
        }
        RunRecord record = new RunRecord(
                session,
                request.requestId,
                requestDigest,
                request.simulationMotionState,
                started.getRunId(),
                compositionEvidence,
                decisionEvidence);
        bySession.put(session.getSessionId(), record);
        return result(record, started);
    }

    @Override
    public synchronized Result get(SessionDescriptor session) {
        RunRecord record = bySession.get(session.getSessionId());
        return record == null
                ? failClosed.get(session)
                : result(record, composition.get(record.runId));
    }

    @Override
    public synchronized Result respondToApproval(
            SessionDescriptor session,
            ApprovalResponse response) {
        RunRecord record = requireRun(session.getSessionId());
        String responseDigest = OrchestrationProjectionFactory.digest(
                "central-brain-debug-orchestration-approval-v1",
                response.requestId,
                response.sessionId,
                response.approvalId,
                response.expectedProjectionDigest,
                Integer.toString(response.decision));
        if (response.requestId.equals(record.lastApprovalRequestId)) {
            if (!responseDigest.equals(record.lastApprovalRequestDigest)) {
                throw violation("approval requestId idempotency conflict");
            }
            return result(record, composition.get(record.runId));
        }
        SimulatedScenarioEffectComposition.Snapshot current = composition.get(record.runId);
        OrchestrationSnapshot projection = stableProjection(record, current);
        if (projection.pendingStage != ICentralBrainOrchestration.PENDING_APPROVAL
                || !projection.approvalId.equals(response.approvalId)
                || !projection.projectionDigest.equals(response.expectedProjectionDigest)) {
            throw violation("approval response is stale or not bound to the pending projection");
        }
        AgentGraphRuntime.NodeExecutionOutcome outcome =
                response.decision == ICentralBrainOrchestration.DECISION_APPROVE
                        ? AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED
                        : AgentGraphRuntime.NodeExecutionOutcome.SKIPPED;
        SimulatedScenarioEffectComposition.Snapshot updated =
                composition.supplyApprovalOutcome(record.runId, outcome);
        record.lastApprovalRequestId = response.requestId;
        record.lastApprovalRequestDigest = responseDigest;
        return result(record, updated);
    }

    @Override
    public synchronized Result requestUndo(SessionDescriptor session, UndoRequest request) {
        RunRecord record = requireRun(session.getSessionId());
        SimulatedScenarioEffectComposition.Snapshot current = composition.get(record.runId);
        OrchestrationSnapshot projection = stableProjection(record, current);
        if (!projection.projectionDigest.equals(request.expectedProjectionDigest)) {
            throw violation("Undo request is stale");
        }
        OrchestrationSnapshot response = OrchestrationProjectionFactory.copySnapshot(projection);
        response.detailCode = "UNDO_AUTHORITY_UNAVAILABLE";
        response.projectionDigest = "";
        OrchestrationProjectionFactory.seal(response);
        return new Result(response, current.toScenarioPlan(), current.getManifestDigest());
    }

    @Override
    public synchronized Result cancel(SessionDescriptor session, int reasonCode) {
        RunRecord record = bySession.get(session.getSessionId());
        return record == null
                ? failClosed.cancel(session, reasonCode)
                : result(record, composition.cancel(record.runId));
    }

    @Override
    public synchronized void close() {
        runtimeComposition.close();
        decisionComposition.close();
    }

    private Result result(
            RunRecord record,
            SimulatedScenarioEffectComposition.Snapshot source) {
        OrchestrationSnapshot snapshot = stableProjection(record, source);
        if (isTerminal(source.getSessionState()) && !record.compositionTerminal) {
            runtimeComposition.complete(
                    record.sessionDescriptor, record.compositionEvidence);
            decisionComposition.complete(
                    record.sessionDescriptor, record.decisionEvidence);
            record.compositionTerminal = true;
        }
        return new Result(snapshot, source.toScenarioPlan(), source.getManifestDigest());
    }

    private static OrchestrationSnapshot stableProjection(
            RunRecord record,
            SimulatedScenarioEffectComposition.Snapshot source) {
        OrchestrationSnapshot snapshot = project(source, record.boundEvidenceDigest);
        if (source.getProjectionDigest().equals(record.sourceProjectionDigest)) {
            snapshot.updatedAtEpochMs = record.projectionUpdatedAtEpochMs;
            snapshot.projectionDigest = "";
            OrchestrationProjectionFactory.seal(snapshot);
        } else {
            record.sourceProjectionDigest = source.getProjectionDigest();
            record.projectionUpdatedAtEpochMs = snapshot.updatedAtEpochMs;
        }
        record.latestProjectionDigest = snapshot.projectionDigest;
        return snapshot;
    }

    private static OrchestrationSnapshot project(
            SimulatedScenarioEffectComposition.Snapshot source,
            String compositionEvidenceDigest) {
        ScenarioPlan plan = source.toScenarioPlan();
        Map<String, PlanNode> planNodes = new LinkedHashMap<>();
        for (PlanNode node : plan.nodes) {
            planNodes.put(node.nodeId, node);
        }
        Set<String> verifiedCapabilities = new LinkedHashSet<>();
        for (AgentGraphRuntime.NodeRunSnapshot sourceNode : source.getNodeSnapshots()) {
            PlanNode planNode = planNodes.get(sourceNode.getNodeId());
            if (planNode != null
                    && "effect.verify".equals(planNode.nodeType)
                    && sourceNode.getState() == NodeRunState.SUCCEEDED) {
                verifiedCapabilities.add(planNode.capabilityId);
            }
        }

        List<OrchestrationNode> nodes = new ArrayList<>();
        List<OrchestrationEffect> effects = new ArrayList<>();
        for (AgentGraphRuntime.NodeRunSnapshot sourceNode : source.getNodeSnapshots()) {
            PlanNode planNode = planNodes.get(sourceNode.getNodeId());
            if (planNode == null) {
                throw violation("graph node is absent from typed Plan");
            }
            OrchestrationNode node = new OrchestrationNode();
            node.schemaVersion = OrchestrationContract.SCHEMA_VERSION;
            node.nodeId = sourceNode.getNodeId();
            node.nodeType = sourceNode.getNodeType();
            node.capabilityId = planNode.capabilityId;
            node.state = nodeState(sourceNode.getState());
            node.attemptCount = sourceNode.getAttempt();
            node.required = sourceNode.isRequired();
            node.evidenceDigest = OrchestrationProjectionFactory.digest(
                    "central-brain-debug-orchestration-node-v1",
                    source.getProjectionDigest(),
                    compositionEvidenceDigest,
                    node.nodeId,
                    Integer.toString(node.state),
                    Integer.toString(node.attemptCount));
            nodes.add(node);

            if ("effect.execute".equals(node.nodeType) && node.attemptCount > 0) {
                OrchestrationEffect effect = new OrchestrationEffect();
                effect.schemaVersion = OrchestrationContract.SCHEMA_VERSION;
                effect.effectId = deterministicUuid(
                        "central-brain-debug-effect-v1", plan.sessionId, node.nodeId);
                effect.nodeId = node.nodeId;
                effect.capabilityId = node.capabilityId;
                effect.state = effectState(
                        sourceNode.getState(),
                        verifiedCapabilities.contains(node.capabilityId));
                effect.attemptCount = Math.max(1, node.attemptCount);
                effect.simulated = true;
                effect.sourceId = "debug.simulation.adapter";
                effect.evidenceDigest = OrchestrationProjectionFactory.digest(
                        "central-brain-debug-orchestration-effect-v1",
                        source.getProjectionDigest(),
                        compositionEvidenceDigest,
                        effect.effectId,
                        Integer.toString(effect.state));
                effects.add(effect);
            }
        }

        OrchestrationSnapshot snapshot = new OrchestrationSnapshot();
        snapshot.schemaVersion = OrchestrationContract.SCHEMA_VERSION;
        snapshot.sessionId = source.getSessionId();
        snapshot.scenarioId = source.getScenarioId();
        snapshot.planId = source.getRunId();
        snapshot.planRevision = source.getPlanRevision();
        snapshot.planDigest = source.getPlanDigest();
        snapshot.state = state(source.getSessionState());
        snapshot.graphRevision = source.getGraphRevision();
        snapshot.nodes = nodes.toArray(new OrchestrationNode[0]);
        snapshot.effects = effects.toArray(new OrchestrationEffect[0]);
        snapshot.pendingStage = pendingStage(source);
        snapshot.pendingNodeId = source.getPendingNode() == null
                ? "" : source.getPendingNode().getNodeId();
        snapshot.pendingCapabilityId = source.getPendingNode() == null
                ? "" : source.getPendingNode().getCapabilityId();
        snapshot.approvalId = snapshot.pendingStage
                        == ICentralBrainOrchestration.PENDING_APPROVAL
                ? deterministicUuid(
                        "central-brain-debug-approval-v1",
                        snapshot.sessionId,
                        snapshot.planId,
                        snapshot.pendingNodeId)
                : "";
        snapshot.undoId = "";
        snapshot.detailCode = detailCode(source.getSessionState());
        snapshot.simulated = true;
        snapshot.effectDispatchEnabled = true;
        snapshot.readbackAvailable = source.getReadbackAttemptCount() > 0;
        snapshot.approvalResponseAvailable = snapshot.pendingStage
                == ICentralBrainOrchestration.PENDING_APPROVAL;
        snapshot.approvalAuthorityTrusted = false;
        snapshot.undoAvailable = false;
        snapshot.hardwareAccessed = false;
        snapshot.productionReady = false;
        snapshot.targetHardwareValidated = false;
        snapshot.updatedAtEpochMs = System.currentTimeMillis();
        OrchestrationProjectionFactory.seal(snapshot);
        return snapshot;
    }

    private static int state(SimulatedScenarioRuntime.SessionState state) {
        switch (state) {
            case WAITING_APPROVAL:
                return ICentralBrainOrchestration.STATE_WAITING_APPROVAL;
            case WAITING_EFFECT:
            case WAITING_READBACK:
                return ICentralBrainOrchestration.STATE_RUNNING;
            case COMPLETED:
                return ICentralBrainOrchestration.STATE_COMPLETED;
            case PARTIAL:
                return ICentralBrainOrchestration.STATE_PARTIAL;
            case FAILED:
                return ICentralBrainOrchestration.STATE_FAILED;
            case CANCELLED:
                return ICentralBrainOrchestration.STATE_CANCELLED;
            case STUCK:
                return ICentralBrainOrchestration.STATE_STUCK;
            default:
                throw violation("unsupported simulated Session state");
        }
    }

    private static int pendingStage(SimulatedScenarioEffectComposition.Snapshot source) {
        if (source.getPendingNode() == null) {
            return ICentralBrainOrchestration.PENDING_NONE;
        }
        switch (source.getPendingNode().getStage()) {
            case APPROVAL:
                return ICentralBrainOrchestration.PENDING_APPROVAL;
            case EFFECT:
                return ICentralBrainOrchestration.PENDING_EFFECT;
            case READBACK:
                return ICentralBrainOrchestration.PENDING_READBACK;
            default:
                return ICentralBrainOrchestration.PENDING_NONE;
        }
    }

    private static int nodeState(NodeRunState state) {
        switch (state) {
            case PENDING: return ICentralBrainOrchestration.NODE_PENDING;
            case READY: return ICentralBrainOrchestration.NODE_READY;
            case EXECUTING: return ICentralBrainOrchestration.NODE_EXECUTING;
            case WAITING: return ICentralBrainOrchestration.NODE_WAITING;
            case SUCCEEDED: return ICentralBrainOrchestration.NODE_SUCCEEDED;
            case FAILED: return ICentralBrainOrchestration.NODE_FAILED;
            case SKIPPED: return ICentralBrainOrchestration.NODE_SKIPPED;
            case CANCELLED: return ICentralBrainOrchestration.NODE_CANCELLED;
            case COMPENSATING: return ICentralBrainOrchestration.NODE_COMPENSATING;
            case COMPENSATED: return ICentralBrainOrchestration.NODE_COMPENSATED;
            case STUCK: return ICentralBrainOrchestration.NODE_STUCK;
            default: throw violation("unsupported graph node state");
        }
    }

    private static int effectState(
            NodeRunState state,
            boolean readbackVerified) {
        switch (state) {
            case SUCCEEDED:
                return readbackVerified
                        ? EffectContract.STATE_VERIFIED : EffectContract.STATE_DELIVERED;
            case FAILED:
            case STUCK:
                return EffectContract.STATE_FAILED_TERMINAL;
            case CANCELLED:
                return EffectContract.STATE_CANCELLED;
            case EXECUTING:
            case WAITING:
                return EffectContract.STATE_DISPATCHED;
            default:
                return EffectContract.STATE_PROPOSED;
        }
    }

    private static String detailCode(SimulatedScenarioRuntime.SessionState state) {
        switch (state) {
            case WAITING_APPROVAL: return "SIMULATION_WAITING_APPROVAL";
            case WAITING_EFFECT: return "SIMULATION_WAITING_EFFECT";
            case WAITING_READBACK: return "SIMULATION_WAITING_READBACK";
            case COMPLETED: return "SIMULATION_COMPLETED";
            case PARTIAL: return "SIMULATION_PARTIAL";
            case FAILED: return "SIMULATION_FAILED";
            case CANCELLED: return "SIMULATION_CANCELLED";
            case STUCK: return "SIMULATION_STUCK";
            default: throw violation("unsupported detail state");
        }
    }

    private static boolean isTerminal(SimulatedScenarioRuntime.SessionState state) {
        return state == SimulatedScenarioRuntime.SessionState.COMPLETED
                || state == SimulatedScenarioRuntime.SessionState.PARTIAL
                || state == SimulatedScenarioRuntime.SessionState.FAILED
                || state == SimulatedScenarioRuntime.SessionState.CANCELLED
                || state == SimulatedScenarioRuntime.SessionState.STUCK;
    }

    private static ScenarioKind scenario(String scenarioId) {
        if ("scene.comfort.cold.v1".equals(scenarioId)) {
            return ScenarioKind.COLD;
        }
        if ("scene.fatigue.assist.v1".equals(scenarioId)) {
            return ScenarioKind.FATIGUE;
        }
        return null;
    }

    private static DrivingProfile driving(int state) {
        if (state == ICentralBrainOrchestration.MOTION_PARKED) {
            return DrivingProfile.PARKED;
        }
        if (state == ICentralBrainOrchestration.MOTION_MOVING) {
            return DrivingProfile.MOVING;
        }
        return null;
    }

    private RunRecord requireRun(String sessionId) {
        RunRecord record = bySession.get(sessionId);
        if (record == null) {
            throw violation("orchestration run is unknown");
        }
        return record;
    }

    private static String deterministicUuid(String domain, String... parts) {
        StringBuilder value = new StringBuilder(domain);
        for (String part : parts) {
            value.append('\u0000').append(part);
        }
        return UUID.nameUUIDFromBytes(
                value.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    static ScenarioCatalog loadCatalog(Context context) {
        try {
            Map<String, byte[]> assets = new LinkedHashMap<>();
            for (String name : new String[] {
                    "scene.comfort.cold.v1.json",
                    "scene.fatigue.assist.v1.json",
                    "scene.rest.nap.v1.json"
            }) {
                assets.put(name, readAsset(context, "scenarios/" + name));
            }
            return ScenarioCatalog.load(assets);
        } catch (IOException failure) {
            throw new IllegalStateException("built-in scenario catalog unavailable", failure);
        }
    }

    private static byte[] readAsset(Context context, String path) throws IOException {
        try (InputStream input = context.getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ASSET_BYTES) {
                    throw new IOException("scenario asset exceeds limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_DEBUG_ORCHESTRATION: " + message);
    }

    private static final class RunRecord {
        private final String requestId;
        private final String requestDigest;
        private final int motionState;
        private final String runId;
        private final SessionDescriptor sessionDescriptor;
        private final DebugRuntimeCompositionBoundary.Evidence compositionEvidence;
        private final DebugDecisionCompositionBoundary.Evidence decisionEvidence;
        private final String boundEvidenceDigest;
        private String latestProjectionDigest = "";
        private String sourceProjectionDigest = "";
        private long projectionUpdatedAtEpochMs;
        private String lastApprovalRequestId = "";
        private String lastApprovalRequestDigest = "";
        private boolean compositionTerminal;

        private RunRecord(
                SessionDescriptor sessionDescriptor,
                String requestId,
                String requestDigest,
                int motionState,
                String runId,
                DebugRuntimeCompositionBoundary.Evidence compositionEvidence,
                DebugDecisionCompositionBoundary.Evidence decisionEvidence) {
            this.sessionDescriptor = sessionDescriptor;
            this.requestId = requestId;
            this.requestDigest = requestDigest;
            this.motionState = motionState;
            this.runId = runId;
            this.compositionEvidence = compositionEvidence;
            this.decisionEvidence = decisionEvidence;
            this.boundEvidenceDigest = DebugDecisionCompositionBoundary.combine(
                    decisionEvidence, compositionEvidence);
        }
    }
}
