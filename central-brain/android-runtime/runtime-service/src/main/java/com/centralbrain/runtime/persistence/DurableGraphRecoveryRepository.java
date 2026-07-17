package com.centralbrain.runtime.persistence;

import com.centralbrain.runtime.graph.GraphRestartReconciler;
import com.centralbrain.runtime.graph.GraphRestartReconciler.NodeRecovery;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentCompensation;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentEffect;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentNode;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentRun;
import com.centralbrain.runtime.graph.GraphRestartReconciler.Result;
import com.centralbrain.sdk.plan.PlanContract;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Room v4 persistence boundary for Stage 2 graph recovery. It is not wired to
 * the Runtime Service and never invokes graph executors or Effect adapters.
 */
public final class DurableGraphRecoveryRepository {
    public static final String AUDIT_GRAPH_RESTART_RECONCILED =
            "GRAPH_RESTART_RECONCILED";
    public static final int MAX_EFFECT_OBSERVATION_ROWS = 1024;

    private final CentralBrainDatabase database;
    private final RuntimeStateDao dao;

    public DurableGraphRecoveryRepository(CentralBrainDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
        this.dao = database.runtimeStateDao();
    }

    /** Writes one immutable recovery baseline after its Session row exists. */
    public void persistInitial(PersistentRun run) {
        Objects.requireNonNull(run, "run");
        database.runInTransaction(() -> {
            SessionEntity session = requireSession(run.getSessionId());
            if (session.deadlineWallMs != run.getDeadlineEpochMs()) {
                throw violation("plan deadline differs from durable Session");
            }
            if (dao.findPlan(run.getPlanId()) != null) {
                throw violation("plan already exists");
            }
            dao.insertPlan(planEntity(run));
            for (PersistentNode node : run.getNodes()) {
                dao.insertPlanNode(nodeEntity(run.getPlanId(), node));
            }
            for (PersistentEffect effect : run.getEffects()) {
                dao.insertEffectObservation(effectEntity(effect));
            }
            for (PersistentCompensation compensation : run.getCompensations()) {
                dao.insertCompensation(compensationEntity(compensation));
            }
        });
    }

    public PersistentRun loadRequired(String planId) {
        return database.runInTransaction(() -> loadLocked(canonicalUuid(planId, "planId")));
    }

    /**
     * Atomically applies only the reconciler's target graph/node states and a
     * digest-only audit. Effect observations and compensation material remain immutable.
     */
    public ApplyReport applyRecovery(Result result, long nowEpochMs) {
        Objects.requireNonNull(result, "result");
        if (nowEpochMs <= 0L) {
            throw violation("recovery persistence time must be positive");
        }
        return database.runInTransaction(() -> {
            PersistentRun current = loadLocked(result.getPlanId());
            if (!current.getSessionId().equals(result.getSessionId())
                    || !current.getPlanDigest().equals(result.getPlanDigest())) {
                throw violation("recovery result binding differs from durable plan");
            }
            PlanEntity plan = requirePlan(result.getPlanId());
            List<PlanNodeEntity> durableNodes = dao.listPlanNodes(result.getPlanId());
            Map<String, PlanNodeEntity> byId = new LinkedHashMap<>();
            for (PlanNodeEntity node : durableNodes) {
                byId.put(node.nodeId, node);
            }
            if (byId.size() != result.getNodes().size()) {
                throw violation("recovery result does not cover every durable node");
            }

            int changedRows = 0;
            int targetPlanState = GraphRestartReconciler.graphStateCode(
                    result.getTargetGraphState());
            if (plan.state != targetPlanState) {
                plan.state = targetPlanState;
                plan.updatedAtWallMs = nowEpochMs;
                if (dao.updatePlan(plan) != 1) {
                    throw violation("durable plan state update conflict");
                }
                changedRows++;
            }
            for (NodeRecovery recovery : result.getNodes()) {
                PlanNodeEntity node = byId.remove(recovery.getNodeId());
                if (node == null || !sameNodeIdentity(node, recovery.getSource())) {
                    throw violation("recovery node binding differs from durable row");
                }
                int targetState = GraphRestartReconciler.nodeStateCode(
                        recovery.getTargetState());
                if (node.state != targetState) {
                    node.state = targetState;
                    node.updatedAtWallMs = nowEpochMs;
                    if (dao.updatePlanNode(node) != 1) {
                        throw violation("durable node state update conflict");
                    }
                    changedRows++;
                }
            }
            if (!byId.isEmpty()) {
                throw violation("durable plan contains an uncovered node");
            }

            SessionEntity session = requireSession(result.getSessionId());
            AuditEventEntity candidate = auditEntity(result, session, nowEpochMs);
            AuditEventEntity existing = dao.findAuditEvent(candidate.eventId);
            boolean auditReplayed = existing != null;
            boolean auditInserted = false;
            if (auditReplayed) {
                requireSameAuditIdentity(existing, candidate);
            } else {
                dao.insertAuditEvent(candidate);
                auditInserted = true;
            }
            return new ApplyReport(
                    changedRows,
                    auditInserted,
                    auditReplayed,
                    result.getResultDigest());
        });
    }

    private PersistentRun loadLocked(String planId) {
        PlanEntity plan = requirePlan(planId);
        SessionEntity session = requireSession(plan.sessionId);
        int nodeCount = dao.countPlanNodes(planId);
        if (nodeCount < 1 || nodeCount > PlanContract.MAX_NODES) {
            throw violation("durable node count is outside bound");
        }
        List<PlanNodeEntity> nodeRows = dao.listPlanNodes(planId);
        if (nodeRows.size() != nodeCount) {
            throw violation("durable node query is incomplete");
        }
        List<PersistentNode> nodes = new ArrayList<>();
        for (PlanNodeEntity row : nodeRows) {
            nodes.add(persistentNode(row));
        }

        int observationCount = dao.countEffectObservationsForRecovery(plan.sessionId);
        if (observationCount < 0 || observationCount > MAX_EFFECT_OBSERVATION_ROWS) {
            throw violation("durable Effect observation history exceeds bound");
        }
        List<EffectObservationEntity> observationRows =
                dao.listEffectObservationsForRecovery(
                        plan.sessionId, MAX_EFFECT_OBSERVATION_ROWS + 1);
        if (observationRows.size() != observationCount) {
            throw violation("durable Effect observation query is incomplete");
        }
        Map<String, EffectObservationEntity> latestByEffect = new LinkedHashMap<>();
        for (EffectObservationEntity row : observationRows) {
            EffectObservationEntity previous = latestByEffect.put(row.effectId, row);
            if (previous != null && row.sequence <= previous.sequence) {
                throw violation("durable Effect observation sequence regressed");
            }
        }
        List<PersistentEffect> effects = new ArrayList<>();
        for (EffectObservationEntity row : latestByEffect.values()) {
            effects.add(persistentEffect(row));
        }

        int compensationCount = dao.countCompensationsForRecovery(plan.sessionId);
        if (compensationCount < 0
                || compensationCount > GraphRestartReconciler.MAX_COMPENSATIONS) {
            throw violation("durable compensation count exceeds bound");
        }
        List<CompensationEntity> compensationRows = dao.listCompensationsForRecovery(
                plan.sessionId, GraphRestartReconciler.MAX_COMPENSATIONS + 1);
        if (compensationRows.size() != compensationCount) {
            throw violation("durable compensation query is incomplete");
        }
        List<PersistentCompensation> compensations = new ArrayList<>();
        for (CompensationEntity row : compensationRows) {
            compensations.add(persistentCompensation(row));
        }
        return new PersistentRun(
                plan.planId,
                plan.sessionId,
                plan.revision,
                GraphRestartReconciler.graphStateFromCode(plan.state),
                plan.planDigest,
                plan.contextDigest,
                plan.manifestDigest,
                plan.createdAtWallMs,
                plan.updatedAtWallMs,
                session.deadlineWallMs,
                nodes,
                effects,
                compensations);
    }

    private SessionEntity requireSession(String sessionId) {
        SessionEntity session = dao.findSession(sessionId);
        if (session == null) {
            throw violation("durable Session is missing");
        }
        return session;
    }

    private PlanEntity requirePlan(String planId) {
        PlanEntity plan = dao.findPlan(planId);
        if (plan == null) {
            throw violation("durable plan is missing");
        }
        return plan;
    }

    private static PlanEntity planEntity(PersistentRun run) {
        PlanEntity entity = new PlanEntity();
        entity.planId = run.getPlanId();
        entity.sessionId = run.getSessionId();
        entity.revision = run.getRevision();
        entity.state = GraphRestartReconciler.graphStateCode(run.getGraphState());
        entity.planDigest = run.getPlanDigest();
        entity.contextDigest = run.getContextDigest();
        entity.manifestDigest = run.getManifestDigest();
        entity.createdAtWallMs = run.getCreatedAtEpochMs();
        entity.updatedAtWallMs = run.getUpdatedAtEpochMs();
        return entity;
    }

    private static PlanNodeEntity nodeEntity(String planId, PersistentNode node) {
        PlanNodeEntity entity = new PlanNodeEntity();
        entity.planId = planId;
        entity.nodeId = node.getNodeId();
        entity.nodeType = node.getNodeType();
        entity.state = GraphRestartReconciler.nodeStateCode(node.getState());
        entity.attemptCount = node.getAttemptCount();
        entity.deadlineWallMs = node.getDeadlineEpochMs();
        entity.idempotencyKey = node.getIdempotencyKey();
        entity.checkpointRef = node.getCheckpointRef();
        entity.payloadDigest = node.getPayloadDigest();
        entity.updatedAtWallMs = node.getUpdatedAtEpochMs();
        return entity;
    }

    private static EffectObservationEntity effectEntity(PersistentEffect effect) {
        EffectObservationEntity entity = new EffectObservationEntity();
        entity.effectId = effect.getEffectId();
        entity.sequence = effect.getSequence();
        entity.observationId = effect.getObservationId();
        entity.sessionId = effect.getSessionId();
        entity.state = effect.getState();
        entity.source = effect.getSource();
        entity.attemptCount = effect.getAttemptCount();
        entity.targetDigest = effect.getTargetDigest();
        entity.reportedDigest = effect.getReportedDigest();
        entity.evidenceDigest = effect.getEvidenceDigest();
        entity.observationDigest = effect.getObservationDigest();
        entity.terminal = effect.isTerminal();
        entity.observedAtWallMs = effect.getObservedAtEpochMs();
        return entity;
    }

    private static CompensationEntity compensationEntity(
            PersistentCompensation compensation) {
        CompensationEntity entity = new CompensationEntity();
        entity.compensationId = compensation.getCompensationId();
        entity.sessionId = compensation.getSessionId();
        entity.effectId = compensation.getEffectId();
        entity.idempotencyKey = compensation.getIdempotencyKey();
        entity.beforeSnapshotRef = compensation.getBeforeSnapshotRef();
        entity.compensationDigest = compensation.getCompensationDigest();
        entity.state = compensation.getState();
        entity.expiresAtWallMs = compensation.getExpiresAtEpochMs();
        entity.createdAtWallMs = compensation.getCreatedAtEpochMs();
        entity.updatedAtWallMs = compensation.getUpdatedAtEpochMs();
        return entity;
    }

    private static PersistentNode persistentNode(PlanNodeEntity row) {
        return new PersistentNode(
                row.nodeId,
                row.nodeType,
                GraphRestartReconciler.nodeStateFromCode(row.state),
                row.attemptCount,
                row.deadlineWallMs,
                row.idempotencyKey,
                row.checkpointRef,
                row.payloadDigest,
                row.updatedAtWallMs);
    }

    private static PersistentEffect persistentEffect(EffectObservationEntity row) {
        return new PersistentEffect(
                row.effectId,
                row.sequence,
                row.observationId,
                row.sessionId,
                row.state,
                row.source,
                row.attemptCount,
                row.targetDigest,
                row.reportedDigest,
                row.evidenceDigest,
                row.observationDigest,
                row.terminal,
                row.observedAtWallMs);
    }

    private static PersistentCompensation persistentCompensation(CompensationEntity row) {
        return new PersistentCompensation(
                row.compensationId,
                row.sessionId,
                row.effectId,
                row.idempotencyKey,
                row.beforeSnapshotRef,
                row.compensationDigest,
                row.state,
                row.expiresAtWallMs,
                row.createdAtWallMs,
                row.updatedAtWallMs);
    }

    private static boolean sameNodeIdentity(PlanNodeEntity row, PersistentNode source) {
        return row.nodeId.equals(source.getNodeId())
                && row.nodeType.equals(source.getNodeType())
                && row.attemptCount == source.getAttemptCount()
                && row.deadlineWallMs == source.getDeadlineEpochMs()
                && row.idempotencyKey.equals(source.getIdempotencyKey())
                && row.checkpointRef.equals(source.getCheckpointRef())
                && row.payloadDigest.equals(source.getPayloadDigest());
    }

    private static AuditEventEntity auditEntity(
            Result result,
            SessionEntity session,
            long nowEpochMs) {
        AuditEventEntity audit = new AuditEventEntity();
        audit.eventId = UUID.nameUUIDFromBytes((
                "graph.restart.audit.v1\u0000"
                        + result.getPlanId()
                        + "\u0000"
                        + result.getResultDigest())
                .getBytes(StandardCharsets.UTF_8)).toString();
        audit.eventType = AUDIT_GRAPH_RESTART_RECONCILED;
        audit.subjectId = result.getPlanId();
        audit.ownerFingerprint = session.ownerFingerprint;
        audit.outcome = result.isContinuationAllowed()
                ? "CONTINUATION_READY" : result.getTargetGraphState().name();
        audit.detailDigest = result.getResultDigest();
        audit.observedAtWallMs = nowEpochMs;
        return audit;
    }

    private static void requireSameAuditIdentity(
            AuditEventEntity existing,
            AuditEventEntity candidate) {
        if (!existing.eventId.equals(candidate.eventId)
                || !existing.eventType.equals(candidate.eventType)
                || !existing.subjectId.equals(candidate.subjectId)
                || !existing.ownerFingerprint.equals(candidate.ownerFingerprint)
                || !existing.outcome.equals(candidate.outcome)
                || !existing.detailDigest.equals(candidate.detailDigest)) {
            throw violation("recovery audit identity collision");
        }
    }

    private static String canonicalUuid(String value, String field) {
        try {
            return UUID.fromString(Objects.requireNonNull(value, field)).toString();
        } catch (IllegalArgumentException exception) {
            throw violation(field + " is not a canonical UUID");
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_GRAPH_RECOVERY_REPOSITORY: " + message);
    }

    public static final class ApplyReport {
        private final int changedRowCount;
        private final boolean auditInserted;
        private final boolean auditReplayed;
        private final String resultDigest;

        private ApplyReport(
                int changedRowCount,
                boolean auditInserted,
                boolean auditReplayed,
                String resultDigest) {
            this.changedRowCount = changedRowCount;
            this.auditInserted = auditInserted;
            this.auditReplayed = auditReplayed;
            this.resultDigest = resultDigest;
        }

        public int getChangedRowCount() { return changedRowCount; }
        public boolean isAuditInserted() { return auditInserted; }
        public boolean isAuditReplayed() { return auditReplayed; }
        public String getResultDigest() { return resultDigest; }
        public boolean isExecutorDispatchEnabled() { return false; }
    }
}
