package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.context.ContextSnapshot;
import com.centralbrain.runtime.events.BoundedEventRuntime;
import com.centralbrain.runtime.graph.AgentGraphRuntime;
import com.centralbrain.runtime.graph.GraphRunState;
import com.centralbrain.runtime.persistence.DurableDigest;
import com.centralbrain.runtime.scenario.ScenarioPlanCompiler.CompileRequest;
import com.centralbrain.runtime.scenario.ScenarioResolver.CapabilitySnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Debug-only Session/Plan/Event projection around {@link SimulatedScenarioGraph}. */
public final class SimulatedScenarioRuntime {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID =
            "android13-p4-d4b-simulated-scenario-runtime-v1";
    public static final String SCHEMA_PLAN_PUBLISHED =
            "cougaros.sim.plan.published.v1";
    public static final String SCHEMA_OUTCOME_SUPPLIED =
            "cougaros.sim.outcome.supplied.v1";
    public static final String SCHEMA_PENDING_APPROVAL =
            "cougaros.sim.pending.approval.v1";
    public static final String SCHEMA_PENDING_EFFECT =
            "cougaros.sim.pending.effect.v1";
    public static final String SCHEMA_PENDING_READBACK =
            "cougaros.sim.pending.readback.v1";
    public static final String SCHEMA_SESSION_COMPLETED =
            "cougaros.sim.session.completed.v1";
    public static final String SCHEMA_SESSION_FAILED =
            "cougaros.sim.session.failed.v1";
    public static final String SCHEMA_SESSION_CANCELLED =
            "cougaros.sim.session.cancelled.v1";

    private static final String EVENT_DIGEST_DOMAIN =
            "central-brain-p4-d4b-simulated-event-v1";
    private static final String PROJECTION_DIGEST_DOMAIN =
            "central-brain-p4-d4b-session-projection-v1";

    public enum SessionState {
        WAITING_APPROVAL,
        WAITING_EFFECT,
        WAITING_READBACK,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /** Immutable HMI-facing metadata projection. It contains no raw request or vehicle payload. */
    public static final class Snapshot {
        private final String runId;
        private final String sessionId;
        private final String scenarioId;
        private final String planDigest;
        private final int planRevision;
        private final GraphRunState graphState;
        private final SessionState sessionState;
        private final long graphRevision;
        private final int automaticProjectionCount;
        private final int suppliedOutcomeCount;
        private final SimulatedScenarioGraph.PendingNode pendingNode;
        private final long lastEventSequence;
        private final int projectedEventCount;
        private final String projectionDigest;

        private Snapshot(
                RunRecord record,
                SimulatedScenarioGraph.Snapshot graph,
                BoundedEventRuntime.Snapshot events) {
            this.runId = graph.getRunId();
            this.sessionId = graph.getSessionId();
            this.scenarioId = graph.getScenarioId();
            this.planDigest = graph.getPlanDigest();
            this.planRevision = graph.getPlanRevision();
            this.graphState = graph.getGraphState();
            this.sessionState = sessionState(graph);
            this.graphRevision = graph.getGraphRevision();
            this.automaticProjectionCount = graph.getAutomaticProjectionCount();
            this.suppliedOutcomeCount = graph.getSuppliedOutcomeCount();
            this.pendingNode = graph.getPendingNode();
            this.lastEventSequence = record.lastEventSequence;
            this.projectedEventCount = record.projectedEventCount;
            this.projectionDigest = DurableDigest.sha256(
                    PROJECTION_DIGEST_DOMAIN,
                    PROFILE_ID,
                    runId,
                    sessionId,
                    scenarioId,
                    planDigest,
                    Integer.toString(planRevision),
                    graphState.name(),
                    sessionState.name(),
                    Long.toString(graphRevision),
                    Integer.toString(automaticProjectionCount),
                    Integer.toString(suppliedOutcomeCount),
                    pendingNode == null ? "" : pendingNode.getNodeId(),
                    pendingNode == null ? "" : pendingNode.getCapabilityId(),
                    pendingNode == null
                            ? SimulatedScenarioGraph.PendingStage.NONE.name()
                            : pendingNode.getStage().name(),
                    Long.toString(lastEventSequence),
                    Integer.toString(projectedEventCount),
                    Long.toString(events.getLatestSequence()),
                    Long.toString(events.getPublishedCount()),
                    graph.getProjectionDigest());
        }

        public String getRunId() {
            return runId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getScenarioId() {
            return scenarioId;
        }

        public String getPlanDigest() {
            return planDigest;
        }

        public int getPlanRevision() {
            return planRevision;
        }

        public GraphRunState getGraphState() {
            return graphState;
        }

        public SessionState getSessionState() {
            return sessionState;
        }

        public long getGraphRevision() {
            return graphRevision;
        }

        public int getAutomaticProjectionCount() {
            return automaticProjectionCount;
        }

        public int getSuppliedOutcomeCount() {
            return suppliedOutcomeCount;
        }

        public SimulatedScenarioGraph.PendingNode getPendingNode() {
            return pendingNode;
        }

        public long getLastEventSequence() {
            return lastEventSequence;
        }

        public int getProjectedEventCount() {
            return projectedEventCount;
        }

        public String getProjectionDigest() {
            return projectionDigest;
        }

        public boolean isPlanPublished() {
            return true;
        }

        public boolean isDebugRuntimeWired() {
            return true;
        }

        public boolean isSessionProjectionEnabled() {
            return true;
        }

        public boolean isEventProjectionEnabled() {
            return true;
        }

        public boolean isProcessLocal() {
            return true;
        }

        public boolean isAndroidServicePublished() {
            return false;
        }

        public boolean isSessionEventBinderPublished() {
            return false;
        }

        public boolean isClient2Wired() {
            return false;
        }

        public boolean isEffectDispatchEnabled() {
            return false;
        }

        public boolean isReadbackAccessed() {
            return false;
        }

        public boolean isApprovalAuthorityAvailable() {
            return false;
        }

        public boolean isProductionRegistered() {
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

    private static final class RunRecord {
        private SimulatedScenarioGraph.Snapshot graph;
        private long lastEventSequence;
        private int projectedEventCount;

        private RunRecord(SimulatedScenarioGraph.Snapshot graph) {
            this.graph = graph;
        }
    }

    private final SimulatedScenarioGraph graph;
    private final BoundedEventRuntime events;
    private final Map<String, RunRecord> runs = new LinkedHashMap<>();

    public SimulatedScenarioRuntime(AgentGraphRuntime.Clock clock) {
        this(clock, () -> UUID.randomUUID().toString());
    }

    public static SimulatedScenarioRuntime createForContractTest(
            AgentGraphRuntime.Clock clock,
            Supplier<String> subscriptionIdGenerator) {
        return new SimulatedScenarioRuntime(clock, subscriptionIdGenerator);
    }

    private SimulatedScenarioRuntime(
            AgentGraphRuntime.Clock clock,
            Supplier<String> subscriptionIdGenerator) {
        Objects.requireNonNull(clock, "clock");
        this.graph = new SimulatedScenarioGraph(clock);
        this.events = BoundedEventRuntime.createForContractTest(
                new BoundedEventRuntime.Limits(64, 8, 4, 32, 16),
                clock::elapsedRealtimeMs,
                Objects.requireNonNull(subscriptionIdGenerator, "subscriptionIdGenerator"));
    }

    public synchronized Snapshot start(
            CompileRequest request,
            ScenarioResolution resolution,
            ContextSnapshot context,
            CapabilitySnapshot capabilities) {
        SimulatedScenarioGraph.Snapshot graphSnapshot = graph.start(
                request, resolution, context, capabilities);
        RunRecord record = new RunRecord(graphSnapshot);
        runs.put(graphSnapshot.getRunId(), record);
        publish(record, graphSnapshot, BoundedEventRuntime.TOPIC_TASK_STATE,
                SCHEMA_PLAN_PUBLISHED, "PLAN_PUBLISHED");
        publishGraphProjection(record, graphSnapshot);
        return snapshot(record);
    }

    public synchronized Snapshot supplyPendingOutcome(
            String runId,
            AgentGraphRuntime.NodeExecutionOutcome outcome) {
        RunRecord record = requireRun(runId);
        SimulatedScenarioGraph.PendingNode pending = record.graph.getPendingNode();
        if (pending == null) {
            throw violation("run has no pending external node");
        }
        AgentGraphRuntime.NodeExecutionOutcome requiredOutcome =
                Objects.requireNonNull(outcome, "outcome");
        SimulatedScenarioGraph.Snapshot previous = record.graph;
        record.graph = graph.supplyPendingOutcome(runId, requiredOutcome);
        publish(
                record,
                previous,
                pending.getStage() == SimulatedScenarioGraph.PendingStage.APPROVAL
                        ? BoundedEventRuntime.TOPIC_POLICY_DECISION
                        : BoundedEventRuntime.TOPIC_TASK_STATE,
                SCHEMA_OUTCOME_SUPPLIED,
                pending.getStage().name() + ':' + requiredOutcome.name());
        publishGraphProjection(record, record.graph);
        return snapshot(record);
    }

    public synchronized Snapshot cancel(String runId) {
        RunRecord record = requireRun(runId);
        record.graph = graph.cancel(runId);
        publishGraphProjection(record, record.graph);
        return snapshot(record);
    }

    public synchronized Snapshot get(String runId) {
        RunRecord record = requireRun(runId);
        record.graph = graph.get(runId);
        return snapshot(record);
    }

    public synchronized int size() {
        return runs.size();
    }

    public BoundedEventRuntime.SubscribeResult subscribe(
            BoundedEventRuntime.TrustedSubscription request,
            BoundedEventRuntime.EventObserver observer) {
        return events.subscribe(request, observer);
    }

    public BoundedEventRuntime.DispatchResult dispatchOwned(
            String subscriptionId,
            String ownerFingerprint,
            int maxEvents) {
        return events.dispatchOwned(subscriptionId, ownerFingerprint, maxEvents);
    }

    public BoundedEventRuntime.CancelResult cancelSubscriptionOwned(
            String subscriptionId,
            String ownerFingerprint) {
        return events.cancelOwned(subscriptionId, ownerFingerprint);
    }

    public BoundedEventRuntime.Snapshot eventRuntimeSnapshot() {
        return events.snapshot();
    }

    private Snapshot snapshot(RunRecord record) {
        return new Snapshot(record, record.graph, events.snapshot());
    }

    private void publishGraphProjection(
            RunRecord record,
            SimulatedScenarioGraph.Snapshot graphSnapshot) {
        String schemaId;
        String topic = BoundedEventRuntime.TOPIC_TASK_STATE;
        String projectionCode;
        if (graphSnapshot.getPendingNode() != null) {
            SimulatedScenarioGraph.PendingStage stage =
                    graphSnapshot.getPendingNode().getStage();
            switch (stage) {
                case APPROVAL:
                    schemaId = SCHEMA_PENDING_APPROVAL;
                    topic = BoundedEventRuntime.TOPIC_POLICY_DECISION;
                    break;
                case EFFECT:
                    schemaId = SCHEMA_PENDING_EFFECT;
                    break;
                case READBACK:
                    schemaId = SCHEMA_PENDING_READBACK;
                    break;
                default:
                    throw violation("pending node stage is unsupported");
            }
            projectionCode = "PENDING:" + stage.name();
        } else {
            switch (graphSnapshot.getGraphState()) {
                case COMPLETED:
                    schemaId = SCHEMA_SESSION_COMPLETED;
                    projectionCode = "TERMINAL:COMPLETED";
                    break;
                case FAILED:
                    schemaId = SCHEMA_SESSION_FAILED;
                    projectionCode = "TERMINAL:FAILED";
                    break;
                case CANCELLED:
                    schemaId = SCHEMA_SESSION_CANCELLED;
                    projectionCode = "TERMINAL:CANCELLED";
                    break;
                default:
                    throw violation("graph projection has no pending or terminal state");
            }
        }
        publish(record, graphSnapshot, topic, schemaId, projectionCode);
    }

    private void publish(
            RunRecord record,
            SimulatedScenarioGraph.Snapshot graphSnapshot,
            String topic,
            String schemaId,
            String projectionCode) {
        String payloadDigest = DurableDigest.sha256(
                EVENT_DIGEST_DOMAIN,
                PROFILE_ID,
                graphSnapshot.getRunId(),
                graphSnapshot.getSessionId(),
                graphSnapshot.getScenarioId(),
                graphSnapshot.getPlanDigest(),
                Integer.toString(graphSnapshot.getPlanRevision()),
                graphSnapshot.getGraphState().name(),
                Long.toString(graphSnapshot.getGraphRevision()),
                projectionCode,
                graphSnapshot.getProjectionDigest());
        BoundedEventRuntime.PublishResult result = events.publish(
                BoundedEventRuntime.TrustedPublication.fromRuntimePolicy(
                        topic, schemaId, payloadDigest));
        if (result.getOutcome() != BoundedEventRuntime.PublishOutcome.PUBLISHED
                || result.getEvent() == null) {
            throw new IllegalStateException("CB_SIM_SCENARIO_RUNTIME: event publish failed");
        }
        record.lastEventSequence = result.getEvent().getSequence();
        record.projectedEventCount++;
    }

    private RunRecord requireRun(String runId) {
        RunRecord record = runs.get(runId);
        if (record == null) {
            throw violation("run is unknown");
        }
        return record;
    }

    private static SessionState sessionState(SimulatedScenarioGraph.Snapshot graph) {
        if (graph.getPendingNode() != null) {
            switch (graph.getPendingNode().getStage()) {
                case APPROVAL:
                    return SessionState.WAITING_APPROVAL;
                case EFFECT:
                    return SessionState.WAITING_EFFECT;
                case READBACK:
                    return SessionState.WAITING_READBACK;
                default:
                    throw violation("pending node stage is unsupported");
            }
        }
        switch (graph.getGraphState()) {
            case COMPLETED:
                return SessionState.COMPLETED;
            case FAILED:
                return SessionState.FAILED;
            case CANCELLED:
                return SessionState.CANCELLED;
            default:
                throw violation("graph projection has no pending or terminal state");
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_SIM_SCENARIO_RUNTIME: " + message);
    }
}
