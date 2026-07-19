package com.centralbrain.runtime.persistence;

import com.centralbrain.runtime.graph.GraphRestartReconciler;
import com.centralbrain.runtime.graph.GraphRestartReconciler.Evidence;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentNode;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentRun;
import com.centralbrain.runtime.graph.GraphRestartReconciler.Result;
import com.centralbrain.runtime.graph.GraphRunState;
import com.centralbrain.runtime.graph.NodeRunState;
import com.centralbrain.runtime.orchestration.OrchestrationBackend;
import com.centralbrain.runtime.orchestration.OrchestrationBackend.SessionDescriptor;
import com.centralbrain.sdk.event.EventContract;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.orchestration.ICentralBrainOrchestration;
import com.centralbrain.sdk.orchestration.OrchestrationContract;
import com.centralbrain.sdk.orchestration.OrchestrationNode;
import com.centralbrain.sdk.orchestration.OrchestrationSnapshot;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Room projection for orchestration recovery. It stores metadata/digests, never target values. */
public final class DurableOrchestrationProjectionRepository {
    public static final int MAX_SESSION_EVENTS = 8;

    private final CentralBrainDatabase database;
    private final RuntimeStateDao dao;
    private final DurableGraphRecoveryRepository graphRepository;

    public DurableOrchestrationProjectionRepository(CentralBrainDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
        dao = database.runtimeStateDao();
        graphRepository = new DurableGraphRecoveryRepository(database);
    }

    public Commit commitOwned(
            SessionDescriptor session,
            OrchestrationBackend.Result result) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(result, "result");
        OrchestrationSnapshot snapshot = result.getSnapshot();
        ScenarioPlan plan = result.getPlan();
        OrchestrationContract.validatePlanForSnapshot(plan, snapshot);
        if (plan == null) {
            return Commit.unchanged();
        }
        requireDigest(result.getManifestDigest(), "manifestDigest");

        return database.runInTransaction(() -> {
            SessionEntity durableSession = dao.findSessionOwned(
                    session.getSessionId(), session.getOwnerFingerprint());
            if (durableSession == null
                    || !durableSession.scenarioId.equals(snapshot.scenarioId)
                    || !durableSession.sessionId.equals(plan.sessionId)) {
                throw violation("durable Session binding changed");
            }
            PlanEntity durablePlan = dao.findPlan(plan.planId);
            if (durablePlan == null) {
                graphRepository.persistInitial(persistentRun(
                        plan, snapshot, result.getManifestDigest()));
            } else {
                applyProjection(durablePlan, plan, snapshot, result.getManifestDigest());
            }

            boolean sessionChanged = updateSession(durableSession, snapshot);
            RuntimeEvent event = appendEventIfChanged(durableSession, snapshot);
            return new Commit(sessionChanged || event != null, event);
        });
    }

    /** Reconciles process-local runs after service restart without dispatching any Effect. */
    public RecoveryReport reconcileInterrupted() {
        List<PlanEntity> candidates = dao.listNonTerminalPlans();
        int reconciled = 0;
        int stuck = 0;
        for (PlanEntity candidate : candidates) {
            PersistentRun run = graphRepository.loadRequired(candidate.planId);
            Result result = new GraphRestartReconciler().reconcile(
                    run,
                    new Evidence(false, Collections.emptyMap(), Collections.emptyMap()),
                    System.currentTimeMillis());
            graphRepository.applyRecovery(result, System.currentTimeMillis());
            database.runInTransaction(() -> {
                SessionEntity session = dao.findSession(run.getSessionId());
                if (session != null) {
                    session.state = sessionState(result.getTargetGraphState());
                    session.updatedAtWallMs = System.currentTimeMillis();
                    session.summary = "ORCHESTRATION_RESTART_"
                            + result.getTargetGraphState().name();
                    session.revision++;
                    if (dao.updateSession(session) != 1) {
                        throw violation("restart Session update conflict");
                    }
                }
            });
            reconciled++;
            if (result.getTargetGraphState() == GraphRunState.STUCK) {
                stuck++;
            }
        }
        return new RecoveryReport(candidates.size(), reconciled, stuck);
    }

    private PersistentRun persistentRun(
            ScenarioPlan plan,
            OrchestrationSnapshot snapshot,
            String manifestDigest) {
        Map<String, OrchestrationNode> projectedNodes = nodesById(snapshot.nodes);
        List<PersistentNode> nodes = new ArrayList<>();
        for (PlanNode node : plan.nodes) {
            OrchestrationNode projection = projectedNodes.remove(node.nodeId);
            if (projection == null || !projection.nodeType.equals(node.nodeType)) {
                throw violation("node projection differs from typed Plan");
            }
            nodes.add(new PersistentNode(
                    node.nodeId,
                    node.nodeType,
                    nodeState(projection.state),
                    projection.attemptCount,
                    plan.deadlineEpochMs,
                    node.idempotencyKey,
                    "",
                    node.inputDigest,
                    snapshot.updatedAtEpochMs));
        }
        if (!projectedNodes.isEmpty()) {
            throw violation("node projection contains unknown Plan node");
        }
        return new PersistentRun(
                plan.planId,
                plan.sessionId,
                plan.revision,
                graphState(snapshot.state),
                plan.planDigest,
                plan.contextDigest,
                manifestDigest,
                plan.compiledAtEpochMs,
                Math.max(plan.compiledAtEpochMs, snapshot.updatedAtEpochMs),
                plan.deadlineEpochMs,
                nodes,
                Collections.emptyList(),
                Collections.emptyList());
    }

    private void applyProjection(
            PlanEntity durablePlan,
            ScenarioPlan plan,
            OrchestrationSnapshot snapshot,
            String manifestDigest) {
        if (!durablePlan.sessionId.equals(plan.sessionId)
                || durablePlan.revision != plan.revision
                || !durablePlan.planDigest.equals(plan.planDigest)
                || !durablePlan.contextDigest.equals(plan.contextDigest)
                || !durablePlan.manifestDigest.equals(manifestDigest)) {
            throw violation("durable Plan identity differs from projection");
        }
        int targetPlanState = GraphRestartReconciler.graphStateCode(
                graphState(snapshot.state));
        if (durablePlan.state != targetPlanState) {
            durablePlan.state = targetPlanState;
            durablePlan.updatedAtWallMs = snapshot.updatedAtEpochMs;
            if (dao.updatePlan(durablePlan) != 1) {
                throw violation("durable Plan update conflict");
            }
        }

        Map<String, PlanNodeEntity> rows = new LinkedHashMap<>();
        for (PlanNodeEntity row : dao.listPlanNodes(plan.planId)) {
            rows.put(row.nodeId, row);
        }
        Map<String, PlanNode> planNodes = new LinkedHashMap<>();
        for (PlanNode node : plan.nodes) {
            planNodes.put(node.nodeId, node);
        }
        for (OrchestrationNode projection : snapshot.nodes) {
            PlanNodeEntity row = rows.remove(projection.nodeId);
            PlanNode source = planNodes.remove(projection.nodeId);
            if (row == null || source == null
                    || !row.nodeType.equals(projection.nodeType)
                    || !row.idempotencyKey.equals(source.idempotencyKey)
                    || !row.payloadDigest.equals(source.inputDigest)) {
                throw violation("durable node identity differs from projection");
            }
            int targetState = GraphRestartReconciler.nodeStateCode(
                    nodeState(projection.state));
            if (row.state != targetState || row.attemptCount != projection.attemptCount) {
                row.state = targetState;
                row.attemptCount = projection.attemptCount;
                row.updatedAtWallMs = snapshot.updatedAtEpochMs;
                if (dao.updatePlanNode(row) != 1) {
                    throw violation("durable node update conflict");
                }
            }
        }
        if (!rows.isEmpty() || !planNodes.isEmpty()) {
            throw violation("durable node set differs from projection");
        }
    }

    private boolean updateSession(
            SessionEntity session,
            OrchestrationSnapshot snapshot) {
        int state = sessionState(graphState(snapshot.state));
        boolean changed = session.state != state
                || session.activePlanRevision != snapshot.planRevision
                || !session.summary.equals(snapshot.detailCode);
        if (!changed) {
            return false;
        }
        session.state = state;
        session.activePlanRevision = snapshot.planRevision;
        session.updatedAtWallMs = snapshot.updatedAtEpochMs;
        session.summary = snapshot.detailCode;
        session.revision++;
        if (dao.updateSession(session) != 1) {
            throw violation("durable Session update conflict");
        }
        return true;
    }

    private RuntimeEvent appendEventIfChanged(
            SessionEntity session,
            OrchestrationSnapshot snapshot) {
        RuntimeEventEntity previous = dao.findLatestRuntimeEvent(session.sessionId);
        if (previous != null && previous.payloadDigest.equals(snapshot.projectionDigest)) {
            return null;
        }
        if (dao.countRuntimeEvents(session.sessionId) >= MAX_SESSION_EVENTS) {
            throw violation("Session event capacity exhausted");
        }
        RuntimeEvent event = new RuntimeEvent();
        event.schemaVersion = EventContract.SCHEMA_VERSION;
        event.eventId = UUID.randomUUID().toString();
        event.sequence = session.lastEventSequence + 1L;
        event.sessionId = session.sessionId;
        event.parentEventId = previous == null ? "" : previous.eventId;
        event.parentSequence = previous == null ? 0L : previous.sequence;
        event.type = "SessionStateChanged";
        event.source = EventContract.SOURCE_RUNTIME;
        event.occurredAtEpochMs = snapshot.updatedAtEpochMs;
        event.privacyClass = EventContract.PRIVACY_INTERNAL;
        event.payloadDigest = snapshot.projectionDigest;
        event.eventDigest = DurableDigest.sha256(
                "central-brain-orchestration-event-v1",
                event.eventId,
                Long.toString(event.sequence),
                event.sessionId,
                event.parentEventId,
                event.payloadDigest);
        event.payloadKind = EventContract.PAYLOAD_NONE;
        EventContract.validateEvent(event);

        RuntimeEventEntity entity = new RuntimeEventEntity();
        entity.eventId = event.eventId;
        entity.sessionId = event.sessionId;
        entity.sequence = event.sequence;
        entity.parentEventId = event.parentEventId;
        entity.parentSequence = event.parentSequence;
        entity.eventType = event.type;
        entity.source = event.source;
        entity.occurredAtWallMs = event.occurredAtEpochMs;
        entity.privacyClass = event.privacyClass;
        entity.payloadDigest = event.payloadDigest;
        entity.eventDigest = event.eventDigest;
        entity.payloadKind = event.payloadKind;
        entity.payloadCanonical = "";
        dao.insertRuntimeEvent(entity);
        session.lastEventSequence = event.sequence;
        if (dao.updateSession(session) != 1) {
            throw violation("Session event sequence update conflict");
        }
        return event;
    }

    private static Map<String, OrchestrationNode> nodesById(OrchestrationNode[] source) {
        Map<String, OrchestrationNode> result = new LinkedHashMap<>();
        for (OrchestrationNode node : source) {
            if (result.put(node.nodeId, node) != null) {
                throw violation("duplicate projected node");
            }
        }
        return result;
    }

    private static GraphRunState graphState(int state) {
        switch (state) {
            case ICentralBrainOrchestration.STATE_PLANNING:
                return GraphRunState.PLANNING;
            case ICentralBrainOrchestration.STATE_WAITING_APPROVAL:
                return GraphRunState.WAITING;
            case ICentralBrainOrchestration.STATE_RUNNING:
                return GraphRunState.EXECUTING;
            case ICentralBrainOrchestration.STATE_COMPLETED:
                return GraphRunState.COMPLETED;
            case ICentralBrainOrchestration.STATE_PARTIAL:
                return GraphRunState.PARTIAL;
            case ICentralBrainOrchestration.STATE_FAILED:
                return GraphRunState.FAILED;
            case ICentralBrainOrchestration.STATE_CANCELLED:
                return GraphRunState.CANCELLED;
            case ICentralBrainOrchestration.STATE_STUCK:
                return GraphRunState.STUCK;
            default:
                throw violation("non-published orchestration state cannot be persisted");
        }
    }

    private static NodeRunState nodeState(int state) {
        switch (state) {
            case ICentralBrainOrchestration.NODE_PENDING: return NodeRunState.PENDING;
            case ICentralBrainOrchestration.NODE_READY: return NodeRunState.READY;
            case ICentralBrainOrchestration.NODE_EXECUTING: return NodeRunState.EXECUTING;
            case ICentralBrainOrchestration.NODE_WAITING: return NodeRunState.WAITING;
            case ICentralBrainOrchestration.NODE_SUCCEEDED: return NodeRunState.SUCCEEDED;
            case ICentralBrainOrchestration.NODE_FAILED: return NodeRunState.FAILED;
            case ICentralBrainOrchestration.NODE_SKIPPED: return NodeRunState.SKIPPED;
            case ICentralBrainOrchestration.NODE_CANCELLED: return NodeRunState.CANCELLED;
            case ICentralBrainOrchestration.NODE_COMPENSATING: return NodeRunState.COMPENSATING;
            case ICentralBrainOrchestration.NODE_COMPENSATED: return NodeRunState.COMPENSATED;
            case ICentralBrainOrchestration.NODE_STUCK: return NodeRunState.STUCK;
            default: throw violation("unknown orchestration node state");
        }
    }

    private static int sessionState(GraphRunState state) {
        switch (state) {
            case CREATED: return ICentralBrainSessionRuntime.SESSION_STATE_CREATED;
            case PLANNING: return ICentralBrainSessionRuntime.SESSION_STATE_PLANNING;
            case WAITING: return ICentralBrainSessionRuntime.SESSION_STATE_WAITING_FOR_CONFIRMATION;
            case EXECUTING: return ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING;
            case PARTIAL: return ICentralBrainSessionRuntime.SESSION_STATE_PARTIALLY_COMPLETED;
            case COMPENSATING: return ICentralBrainSessionRuntime.SESSION_STATE_COMPENSATING;
            case STUCK: return ICentralBrainSessionRuntime.SESSION_STATE_STUCK;
            case COMPLETED: return ICentralBrainSessionRuntime.SESSION_STATE_COMPLETED;
            case FAILED: return ICentralBrainSessionRuntime.SESSION_STATE_FAILED;
            case CANCELLED: return ICentralBrainSessionRuntime.SESSION_STATE_CANCELLED;
            default: throw violation("unknown Graph state");
        }
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw violation(field + " is invalid");
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_DURABLE_ORCHESTRATION: " + message);
    }

    public static final class Commit {
        private final boolean changed;
        private final RuntimeEvent event;

        private Commit(boolean changed, RuntimeEvent event) {
            this.changed = changed;
            this.event = event;
        }

        static Commit unchanged() { return new Commit(false, null); }
        public boolean isChanged() { return changed; }
        public RuntimeEvent getEvent() { return event; }
    }

    public static final class RecoveryReport {
        private final int candidateCount;
        private final int reconciledCount;
        private final int stuckCount;

        RecoveryReport(int candidateCount, int reconciledCount, int stuckCount) {
            this.candidateCount = candidateCount;
            this.reconciledCount = reconciledCount;
            this.stuckCount = stuckCount;
        }

        public int getCandidateCount() { return candidateCount; }
        public int getReconciledCount() { return reconciledCount; }
        public int getStuckCount() { return stuckCount; }
    }
}
