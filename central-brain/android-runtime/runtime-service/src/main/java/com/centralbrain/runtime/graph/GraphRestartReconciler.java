package com.centralbrain.runtime.graph;

import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.plan.PlanContract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fail-closed Stage 2 graph recovery reducer. It never invokes an executor,
 * adapter, model, Binder service, clock, scheduler, or hardware interface.
 */
public final class GraphRestartReconciler {
    public static final int MAX_EFFECTS = 64;
    public static final int MAX_COMPENSATIONS = 64;

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern NODE_ID =
            Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Set<String> EFFECT_NODE_TYPES = Set.of(
            "effect.execute", "effect.verify");
    private static final Set<String> OTHER_SIDE_EFFECT_NODE_TYPES = Set.of(
            "tool.invoke", "model.invoke", "memory.write");
    private static final Set<String> IDEMPOTENCY_REQUIRED_NODE_TYPES = Set.of(
            "effect.execute", "tool.invoke", "memory.write", "compensate");

    public enum CheckpointStatus {
        VALID,
        MISSING,
        MISMATCH,
        UNKNOWN
    }

    public enum EffectDeliveryStatus {
        UNKNOWN,
        CONFIRMED_APPLIED,
        CONFIRMED_NOT_APPLIED
    }

    public enum DirectiveType {
        REVALIDATE_GOVERNANCE,
        REVALIDATE_APPROVAL,
        REVALIDATE_UNDO,
        RECONCILE_EFFECT_NODE,
        RECONCILE_SIDE_EFFECT,
        RECONCILE_EFFECT_STATUS,
        VERIFY_EFFECT_READBACK,
        RETRY_EFFECT_AFTER_POLICY,
        CHECKPOINT_MISSING,
        CHECKPOINT_MISMATCH,
        CHECKPOINT_UNTRUSTED,
        UNDO_EXPIRED,
        PLAN_DEADLINE_EXCEEDED
    }

    public Result reconcile(PersistentRun run, Evidence evidence, long nowEpochMs) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(evidence, "evidence");
        if (nowEpochMs <= 0L) {
            throw violation("recovery time must be positive");
        }
        if (run.getGraphState().isTerminal()) {
            return result(run, run.getGraphState(), copyNodeStates(run), List.of());
        }
        if (nowEpochMs >= run.getDeadlineEpochMs()) {
            List<Directive> directives = List.of(new Directive(
                    DirectiveType.PLAN_DEADLINE_EXCEEDED,
                    run.getPlanId()));
            return result(
                    run,
                    GraphRunState.FAILED,
                    terminalizeNonTerminalNodes(run, NodeRunState.STUCK),
                    directives);
        }

        List<NodeRecovery> recoveredNodes = new ArrayList<>();
        List<Directive> directives = new ArrayList<>();
        boolean checkpointFailure = false;
        for (PersistentNode node : run.getNodes()) {
            NodeRunState target = node.getState();
            if (!node.getState().isTerminal()) {
                CheckpointStatus status = checkpointStatus(node, evidence);
                if (requiresCheckpoint(node.getState()) && status != CheckpointStatus.VALID) {
                    checkpointFailure = true;
                    target = NodeRunState.STUCK;
                    directives.add(new Directive(checkpointDirective(status), node.getNodeId()));
                } else {
                    target = recoverNode(node, evidence, directives);
                }
            }
            recoveredNodes.add(new NodeRecovery(node, target));
        }
        if (checkpointFailure) {
            List<NodeRecovery> stuck = new ArrayList<>();
            for (NodeRecovery node : recoveredNodes) {
                NodeRunState target = node.getTargetState().isTerminal()
                        ? node.getTargetState() : NodeRunState.STUCK;
                stuck.add(new NodeRecovery(node.getSource(), target));
            }
            return result(run, GraphRunState.STUCK, stuck, directives);
        }

        for (PersistentEffect effect : run.getEffects()) {
            if (effect.getState() != EffectContract.STATE_UNKNOWN || effect.isTerminal()) {
                continue;
            }
            EffectDeliveryStatus status = evidence.effectStatus(effect.getEffectId());
            DirectiveType type;
            switch (status) {
                case CONFIRMED_APPLIED:
                    type = DirectiveType.VERIFY_EFFECT_READBACK;
                    break;
                case CONFIRMED_NOT_APPLIED:
                    type = DirectiveType.RETRY_EFFECT_AFTER_POLICY;
                    break;
                case UNKNOWN:
                default:
                    type = DirectiveType.RECONCILE_EFFECT_STATUS;
                    break;
            }
            directives.add(new Directive(type, effect.getEffectId()));
        }
        for (PersistentCompensation compensation : run.getCompensations()) {
            if (compensation.getState() == EffectContract.UNDO_COMPLETED
                    || compensation.getState() == EffectContract.UNDO_REJECTED
                    || compensation.getState() == EffectContract.UNDO_EXPIRED) {
                continue;
            }
            DirectiveType type = nowEpochMs >= compensation.getExpiresAtEpochMs()
                    ? DirectiveType.UNDO_EXPIRED : DirectiveType.REVALIDATE_UNDO;
            directives.add(new Directive(type, compensation.getCompensationId()));
        }
        return result(run, GraphRunState.WAITING, recoveredNodes, directives);
    }

    private static NodeRunState recoverNode(
            PersistentNode node,
            Evidence evidence,
            List<Directive> directives) {
        String type = node.getNodeType();
        if (EFFECT_NODE_TYPES.contains(type)) {
            directives.add(new Directive(
                    DirectiveType.RECONCILE_EFFECT_NODE, node.getNodeId()));
            return NodeRunState.WAITING;
        }
        if ("approval.interrupt".equals(type)) {
            directives.add(new Directive(
                    DirectiveType.REVALIDATE_APPROVAL, node.getNodeId()));
            return NodeRunState.WAITING;
        }
        if ("compensate".equals(type)) {
            directives.add(new Directive(
                    DirectiveType.REVALIDATE_UNDO, node.getNodeId()));
            return NodeRunState.WAITING;
        }
        if (OTHER_SIDE_EFFECT_NODE_TYPES.contains(type)) {
            directives.add(new Directive(
                    DirectiveType.RECONCILE_SIDE_EFFECT, node.getNodeId()));
            return NodeRunState.WAITING;
        }
        if (!evidence.isGovernanceRevalidated()) {
            directives.add(new Directive(
                    DirectiveType.REVALIDATE_GOVERNANCE, node.getNodeId()));
            return node.getState() == NodeRunState.PENDING
                    ? NodeRunState.PENDING : NodeRunState.WAITING;
        }
        if (node.getState() == NodeRunState.PENDING) {
            return NodeRunState.PENDING;
        }
        return NodeRunState.READY;
    }

    private static CheckpointStatus checkpointStatus(PersistentNode node, Evidence evidence) {
        if (node.getCheckpointRef().isEmpty()) {
            return CheckpointStatus.MISSING;
        }
        return evidence.checkpointStatus(node.getCheckpointRef());
    }

    private static boolean requiresCheckpoint(NodeRunState state) {
        return state == NodeRunState.EXECUTING
                || state == NodeRunState.WAITING
                || state == NodeRunState.COMPENSATING;
    }

    private static DirectiveType checkpointDirective(CheckpointStatus status) {
        switch (status) {
            case MISSING:
                return DirectiveType.CHECKPOINT_MISSING;
            case MISMATCH:
                return DirectiveType.CHECKPOINT_MISMATCH;
            case UNKNOWN:
            default:
                return DirectiveType.CHECKPOINT_UNTRUSTED;
        }
    }

    private static Result result(
            PersistentRun run,
            GraphRunState graphState,
            List<NodeRecovery> nodes,
            List<Directive> directives) {
        List<Directive> orderedDirectives = new ArrayList<>(directives);
        orderedDirectives.sort(Comparator
                .comparing((Directive directive) -> directive.type.name())
                .thenComparing(directive -> directive.subjectId));
        boolean hasReady = false;
        for (NodeRecovery node : nodes) {
            hasReady |= node.targetState == NodeRunState.READY;
        }
        boolean continuationAllowed = graphState == GraphRunState.WAITING
                && orderedDirectives.isEmpty()
                && hasReady;
        return new Result(
                run.getPlanId(),
                run.getSessionId(),
                run.getPlanDigest(),
                graphState,
                nodes,
                orderedDirectives,
                continuationAllowed);
    }

    private static List<NodeRecovery> copyNodeStates(PersistentRun run) {
        List<NodeRecovery> nodes = new ArrayList<>();
        for (PersistentNode node : run.getNodes()) {
            nodes.add(new NodeRecovery(node, node.getState()));
        }
        return nodes;
    }

    private static List<NodeRecovery> terminalizeNonTerminalNodes(
            PersistentRun run,
            NodeRunState terminalState) {
        List<NodeRecovery> nodes = new ArrayList<>();
        for (PersistentNode node : run.getNodes()) {
            nodes.add(new NodeRecovery(
                    node,
                    node.getState().isTerminal() ? node.getState() : terminalState));
        }
        return nodes;
    }

    public static int graphStateCode(GraphRunState state) {
        Objects.requireNonNull(state, "graphState");
        switch (state) {
            case CREATED: return 1;
            case PLANNING: return 2;
            case WAITING: return 3;
            case EXECUTING: return 4;
            case PARTIAL: return 5;
            case COMPENSATING: return 6;
            case COMPLETED: return 7;
            case FAILED: return 8;
            case CANCELLED: return 9;
            case STUCK: return 10;
            default: throw violation("unsupported graph state");
        }
    }

    public static GraphRunState graphStateFromCode(int code) {
        switch (code) {
            case 1: return GraphRunState.CREATED;
            case 2: return GraphRunState.PLANNING;
            case 3: return GraphRunState.WAITING;
            case 4: return GraphRunState.EXECUTING;
            case 5: return GraphRunState.PARTIAL;
            case 6: return GraphRunState.COMPENSATING;
            case 7: return GraphRunState.COMPLETED;
            case 8: return GraphRunState.FAILED;
            case 9: return GraphRunState.CANCELLED;
            case 10: return GraphRunState.STUCK;
            default: throw violation("unknown durable graph state code");
        }
    }

    public static int nodeStateCode(NodeRunState state) {
        Objects.requireNonNull(state, "nodeState");
        switch (state) {
            case PENDING: return 1;
            case READY: return 2;
            case EXECUTING: return 3;
            case WAITING: return 4;
            case SUCCEEDED: return 5;
            case FAILED: return 6;
            case SKIPPED: return 7;
            case CANCELLED: return 8;
            case COMPENSATING: return 9;
            case COMPENSATED: return 10;
            case STUCK: return 11;
            default: throw violation("unsupported node state");
        }
    }

    public static NodeRunState nodeStateFromCode(int code) {
        switch (code) {
            case 1: return NodeRunState.PENDING;
            case 2: return NodeRunState.READY;
            case 3: return NodeRunState.EXECUTING;
            case 4: return NodeRunState.WAITING;
            case 5: return NodeRunState.SUCCEEDED;
            case 6: return NodeRunState.FAILED;
            case 7: return NodeRunState.SKIPPED;
            case 8: return NodeRunState.CANCELLED;
            case 9: return NodeRunState.COMPENSATING;
            case 10: return NodeRunState.COMPENSATED;
            case 11: return NodeRunState.STUCK;
            default: throw violation("unknown durable node state code");
        }
    }

    private static boolean isEffectTerminal(int state) {
        return state == EffectContract.STATE_REJECTED
                || state == EffectContract.STATE_VERIFIED
                || state == EffectContract.STATE_FAILED_TERMINAL
                || state == EffectContract.STATE_COMPENSATED
                || state == EffectContract.STATE_CANCELLED;
    }

    private static String requireUuid(String value, String field) {
        try {
            return UUID.fromString(Objects.requireNonNull(value, field)).toString();
        } catch (IllegalArgumentException exception) {
            throw violation(field + " is not a canonical UUID");
        }
    }

    private static String requireDigest(String value, String field) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw violation(field + " is not a SHA-256 digest");
        }
        return value;
    }

    private static String requireNodeId(String value) {
        if (value == null
                || value.length() > PlanContract.MAX_NODE_ID_CHARS
                || !NODE_ID.matcher(value).matches()) {
            throw violation("nodeId is not canonical");
        }
        return value;
    }

    private static String digest(String domain, List<String> parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, domain);
            for (String part : parts) {
                updateDigest(digest, part);
            }
            byte[] bytes = digest.digest();
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
        digest.update((byte) 0);
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_GRAPH_RESTART: " + message);
    }

    public static final class Evidence {
        private final boolean governanceRevalidated;
        private final Map<String, CheckpointStatus> checkpointStatuses;
        private final Map<String, EffectDeliveryStatus> effectStatuses;

        public Evidence(
                boolean governanceRevalidated,
                Map<String, CheckpointStatus> checkpointStatuses,
                Map<String, EffectDeliveryStatus> effectStatuses) {
            this.governanceRevalidated = governanceRevalidated;
            this.checkpointStatuses = immutableCheckpointStatuses(checkpointStatuses);
            this.effectStatuses = immutableEffectStatuses(effectStatuses);
        }

        private static Map<String, CheckpointStatus> immutableCheckpointStatuses(
                Map<String, CheckpointStatus> source) {
            Objects.requireNonNull(source, "checkpointStatuses");
            if (source.size() > PlanContract.MAX_NODES) {
                throw violation("checkpoint evidence exceeds node bound");
            }
            Map<String, CheckpointStatus> result = new HashMap<>();
            for (Map.Entry<String, CheckpointStatus> entry : source.entrySet()) {
                result.put(
                        requireDigest(entry.getKey(), "checkpointRef"),
                        Objects.requireNonNull(entry.getValue(), "checkpointStatus"));
            }
            return Collections.unmodifiableMap(result);
        }

        private static Map<String, EffectDeliveryStatus> immutableEffectStatuses(
                Map<String, EffectDeliveryStatus> source) {
            Objects.requireNonNull(source, "effectStatuses");
            if (source.size() > MAX_EFFECTS) {
                throw violation("Effect evidence exceeds bound");
            }
            Map<String, EffectDeliveryStatus> result = new HashMap<>();
            for (Map.Entry<String, EffectDeliveryStatus> entry : source.entrySet()) {
                result.put(
                        requireUuid(entry.getKey(), "effectId"),
                        Objects.requireNonNull(entry.getValue(), "effectStatus"));
            }
            return Collections.unmodifiableMap(result);
        }

        public boolean isGovernanceRevalidated() {
            return governanceRevalidated;
        }

        private CheckpointStatus checkpointStatus(String checkpointRef) {
            return checkpointStatuses.getOrDefault(
                    checkpointRef, CheckpointStatus.UNKNOWN);
        }

        private EffectDeliveryStatus effectStatus(String effectId) {
            return effectStatuses.getOrDefault(
                    effectId, EffectDeliveryStatus.UNKNOWN);
        }
    }

    public static final class PersistentRun {
        private final String planId;
        private final String sessionId;
        private final int revision;
        private final GraphRunState graphState;
        private final String planDigest;
        private final String contextDigest;
        private final String manifestDigest;
        private final long createdAtEpochMs;
        private final long updatedAtEpochMs;
        private final long deadlineEpochMs;
        private final List<PersistentNode> nodes;
        private final List<PersistentEffect> effects;
        private final List<PersistentCompensation> compensations;

        public PersistentRun(
                String planId,
                String sessionId,
                int revision,
                GraphRunState graphState,
                String planDigest,
                String contextDigest,
                String manifestDigest,
                long createdAtEpochMs,
                long updatedAtEpochMs,
                long deadlineEpochMs,
                List<PersistentNode> nodes,
                List<PersistentEffect> effects,
                List<PersistentCompensation> compensations) {
            this.planId = requireUuid(planId, "planId");
            this.sessionId = requireUuid(sessionId, "sessionId");
            if (revision < 1) {
                throw violation("plan revision must be positive");
            }
            this.revision = revision;
            this.graphState = Objects.requireNonNull(graphState, "graphState");
            this.planDigest = requireDigest(planDigest, "planDigest");
            this.contextDigest = requireDigest(contextDigest, "contextDigest");
            this.manifestDigest = requireDigest(manifestDigest, "manifestDigest");
            if (createdAtEpochMs <= 0L
                    || updatedAtEpochMs < createdAtEpochMs
                    || deadlineEpochMs <= createdAtEpochMs) {
                throw violation("durable plan timestamps are invalid");
            }
            this.createdAtEpochMs = createdAtEpochMs;
            this.updatedAtEpochMs = updatedAtEpochMs;
            this.deadlineEpochMs = deadlineEpochMs;
            this.nodes = immutableNodes(nodes);
            this.effects = immutableEffects(effects, this.sessionId);
            this.compensations = immutableCompensations(compensations, this.sessionId);
            Set<String> effectIds = new HashSet<>();
            for (PersistentEffect effect : this.effects) {
                effectIds.add(effect.effectId);
            }
            for (PersistentCompensation compensation : this.compensations) {
                if (!effectIds.contains(compensation.effectId)) {
                    throw violation("durable compensation source Effect is missing");
                }
            }
        }

        private static List<PersistentNode> immutableNodes(List<PersistentNode> source) {
            Objects.requireNonNull(source, "nodes");
            if (source.isEmpty() || source.size() > PlanContract.MAX_NODES) {
                throw violation("durable node count is outside bound");
            }
            List<PersistentNode> result = new ArrayList<>(source);
            result.sort(Comparator.comparing(PersistentNode::getNodeId));
            Set<String> nodeIds = new HashSet<>();
            Set<String> idempotencyKeys = new HashSet<>();
            for (PersistentNode node : result) {
                if (!nodeIds.add(node.nodeId)
                        || (!node.idempotencyKey.isEmpty()
                                && !idempotencyKeys.add(node.idempotencyKey))) {
                    throw violation("durable nodes contain duplicate identity");
                }
            }
            return Collections.unmodifiableList(result);
        }

        private static List<PersistentEffect> immutableEffects(
                List<PersistentEffect> source,
                String sessionId) {
            Objects.requireNonNull(source, "effects");
            if (source.size() > MAX_EFFECTS) {
                throw violation("durable Effect count exceeds bound");
            }
            List<PersistentEffect> result = new ArrayList<>(source);
            result.sort(Comparator.comparing(PersistentEffect::getEffectId));
            Set<String> ids = new HashSet<>();
            for (PersistentEffect effect : result) {
                if (!sessionId.equals(effect.sessionId) || !ids.add(effect.effectId)) {
                    throw violation("durable Effect binding or identity is invalid");
                }
            }
            return Collections.unmodifiableList(result);
        }

        private static List<PersistentCompensation> immutableCompensations(
                List<PersistentCompensation> source,
                String sessionId) {
            Objects.requireNonNull(source, "compensations");
            if (source.size() > MAX_COMPENSATIONS) {
                throw violation("durable compensation count exceeds bound");
            }
            List<PersistentCompensation> result = new ArrayList<>(source);
            result.sort(Comparator.comparing(PersistentCompensation::getCompensationId));
            Set<String> ids = new HashSet<>();
            Set<String> keys = new HashSet<>();
            for (PersistentCompensation compensation : result) {
                if (!sessionId.equals(compensation.sessionId)
                        || !ids.add(compensation.compensationId)
                        || !keys.add(compensation.idempotencyKey)) {
                    throw violation("durable compensation binding or identity is invalid");
                }
            }
            return Collections.unmodifiableList(result);
        }

        public String getPlanId() { return planId; }
        public String getSessionId() { return sessionId; }
        public int getRevision() { return revision; }
        public GraphRunState getGraphState() { return graphState; }
        public String getPlanDigest() { return planDigest; }
        public String getContextDigest() { return contextDigest; }
        public String getManifestDigest() { return manifestDigest; }
        public long getCreatedAtEpochMs() { return createdAtEpochMs; }
        public long getUpdatedAtEpochMs() { return updatedAtEpochMs; }
        public long getDeadlineEpochMs() { return deadlineEpochMs; }
        public List<PersistentNode> getNodes() { return nodes; }
        public List<PersistentEffect> getEffects() { return effects; }
        public List<PersistentCompensation> getCompensations() { return compensations; }
    }

    public static final class PersistentNode {
        private final String nodeId;
        private final String nodeType;
        private final NodeRunState state;
        private final int attemptCount;
        private final long deadlineEpochMs;
        private final String idempotencyKey;
        private final String checkpointRef;
        private final String payloadDigest;
        private final long updatedAtEpochMs;

        public PersistentNode(
                String nodeId,
                String nodeType,
                NodeRunState state,
                int attemptCount,
                long deadlineEpochMs,
                String idempotencyKey,
                String checkpointRef,
                String payloadDigest,
                long updatedAtEpochMs) {
            this.nodeId = requireNodeId(nodeId);
            if (nodeType == null || !PlanContract.allowedNodeTypes().contains(nodeType)) {
                throw violation("nodeType is not allowed");
            }
            this.nodeType = nodeType;
            this.state = Objects.requireNonNull(state, "nodeState");
            if (attemptCount < 0 || attemptCount > PlanContract.MAX_ATTEMPTS) {
                throw violation("attemptCount is outside bound");
            }
            if (deadlineEpochMs <= 0L || updatedAtEpochMs <= 0L) {
                throw violation("node timestamp is invalid");
            }
            if (idempotencyKey == null
                    || (!idempotencyKey.isEmpty()
                            && !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches())) {
                throw violation("node idempotency key is not canonical");
            }
            if (idempotencyKey.isEmpty()
                    && IDEMPOTENCY_REQUIRED_NODE_TYPES.contains(nodeType)) {
                throw violation("side-effect node requires an idempotency key");
            }
            if (!checkpointRef.isEmpty()) {
                requireDigest(checkpointRef, "checkpointRef");
            }
            this.attemptCount = attemptCount;
            this.deadlineEpochMs = deadlineEpochMs;
            this.idempotencyKey = idempotencyKey;
            this.checkpointRef = checkpointRef;
            this.payloadDigest = requireDigest(payloadDigest, "payloadDigest");
            this.updatedAtEpochMs = updatedAtEpochMs;
        }

        public String getNodeId() { return nodeId; }
        public String getNodeType() { return nodeType; }
        public NodeRunState getState() { return state; }
        public int getAttemptCount() { return attemptCount; }
        public long getDeadlineEpochMs() { return deadlineEpochMs; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public String getCheckpointRef() { return checkpointRef; }
        public String getPayloadDigest() { return payloadDigest; }
        public long getUpdatedAtEpochMs() { return updatedAtEpochMs; }
    }

    public static final class PersistentEffect {
        private final String effectId;
        private final long sequence;
        private final String observationId;
        private final String sessionId;
        private final int state;
        private final int source;
        private final int attemptCount;
        private final String targetDigest;
        private final String reportedDigest;
        private final String evidenceDigest;
        private final String observationDigest;
        private final boolean terminal;
        private final long observedAtEpochMs;

        public PersistentEffect(
                String effectId,
                long sequence,
                String observationId,
                String sessionId,
                int state,
                int source,
                int attemptCount,
                String targetDigest,
                String reportedDigest,
                String evidenceDigest,
                String observationDigest,
                boolean terminal,
                long observedAtEpochMs) {
            this.effectId = requireUuid(effectId, "effectId");
            this.observationId = requireUuid(observationId, "observationId");
            this.sessionId = requireUuid(sessionId, "effectSessionId");
            if (sequence < 1L
                    || state < EffectContract.STATE_PROPOSED
                    || state > EffectContract.STATE_CANCELLED
                    || source < EffectContract.SOURCE_RUNTIME
                    || source > EffectContract.SOURCE_SIMULATION
                    || attemptCount < 1
                    || attemptCount > PlanContract.MAX_ATTEMPTS
                    || observedAtEpochMs <= 0L
                    || terminal != isEffectTerminal(state)) {
                throw violation("durable Effect metadata is invalid");
            }
            this.sequence = sequence;
            this.state = state;
            this.source = source;
            this.attemptCount = attemptCount;
            this.targetDigest = requireDigest(targetDigest, "targetDigest");
            this.reportedDigest = requireDigest(reportedDigest, "reportedDigest");
            this.evidenceDigest = requireDigest(evidenceDigest, "evidenceDigest");
            this.observationDigest = requireDigest(observationDigest, "observationDigest");
            this.terminal = terminal;
            this.observedAtEpochMs = observedAtEpochMs;
        }

        public String getEffectId() { return effectId; }
        public long getSequence() { return sequence; }
        public String getObservationId() { return observationId; }
        public String getSessionId() { return sessionId; }
        public int getState() { return state; }
        public int getSource() { return source; }
        public int getAttemptCount() { return attemptCount; }
        public String getTargetDigest() { return targetDigest; }
        public String getReportedDigest() { return reportedDigest; }
        public String getEvidenceDigest() { return evidenceDigest; }
        public String getObservationDigest() { return observationDigest; }
        public boolean isTerminal() { return terminal; }
        public long getObservedAtEpochMs() { return observedAtEpochMs; }
    }

    public static final class PersistentCompensation {
        private final String compensationId;
        private final String sessionId;
        private final String effectId;
        private final String idempotencyKey;
        private final String beforeSnapshotRef;
        private final String compensationDigest;
        private final int state;
        private final long expiresAtEpochMs;
        private final long createdAtEpochMs;
        private final long updatedAtEpochMs;

        public PersistentCompensation(
                String compensationId,
                String sessionId,
                String effectId,
                String idempotencyKey,
                String beforeSnapshotRef,
                String compensationDigest,
                int state,
                long expiresAtEpochMs,
                long createdAtEpochMs,
                long updatedAtEpochMs) {
            this.compensationId = requireUuid(compensationId, "compensationId");
            this.sessionId = requireUuid(sessionId, "compensationSessionId");
            this.effectId = requireUuid(effectId, "compensationEffectId");
            if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
                throw violation("compensation idempotency key is not canonical");
            }
            if (state < EffectContract.UNDO_AVAILABLE || state > EffectContract.UNDO_COMPLETED
                    || createdAtEpochMs <= 0L
                    || updatedAtEpochMs < createdAtEpochMs
                    || expiresAtEpochMs <= createdAtEpochMs) {
                throw violation("durable compensation metadata is invalid");
            }
            this.idempotencyKey = idempotencyKey;
            this.beforeSnapshotRef = requireDigest(beforeSnapshotRef, "beforeSnapshotRef");
            this.compensationDigest = requireDigest(compensationDigest, "compensationDigest");
            this.state = state;
            this.expiresAtEpochMs = expiresAtEpochMs;
            this.createdAtEpochMs = createdAtEpochMs;
            this.updatedAtEpochMs = updatedAtEpochMs;
        }

        public String getCompensationId() { return compensationId; }
        public String getSessionId() { return sessionId; }
        public String getEffectId() { return effectId; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public String getBeforeSnapshotRef() { return beforeSnapshotRef; }
        public String getCompensationDigest() { return compensationDigest; }
        public int getState() { return state; }
        public long getExpiresAtEpochMs() { return expiresAtEpochMs; }
        public long getCreatedAtEpochMs() { return createdAtEpochMs; }
        public long getUpdatedAtEpochMs() { return updatedAtEpochMs; }
    }

    public static final class NodeRecovery {
        private final PersistentNode source;
        private final NodeRunState targetState;

        private NodeRecovery(PersistentNode source, NodeRunState targetState) {
            this.source = Objects.requireNonNull(source, "source");
            this.targetState = Objects.requireNonNull(targetState, "targetState");
        }

        public PersistentNode getSource() { return source; }
        public String getNodeId() { return source.nodeId; }
        public NodeRunState getTargetState() { return targetState; }
    }

    public static final class Directive {
        private final DirectiveType type;
        private final String subjectId;

        private Directive(DirectiveType type, String subjectId) {
            this.type = Objects.requireNonNull(type, "directiveType");
            this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        }

        public DirectiveType getType() { return type; }
        public String getSubjectId() { return subjectId; }
    }

    public static final class Result {
        private final String planId;
        private final String sessionId;
        private final String planDigest;
        private final GraphRunState targetGraphState;
        private final List<NodeRecovery> nodes;
        private final List<Directive> directives;
        private final boolean continuationAllowed;
        private final String resultDigest;

        private Result(
                String planId,
                String sessionId,
                String planDigest,
                GraphRunState targetGraphState,
                List<NodeRecovery> nodes,
                List<Directive> directives,
                boolean continuationAllowed) {
            this.planId = planId;
            this.sessionId = sessionId;
            this.planDigest = planDigest;
            this.targetGraphState = targetGraphState;
            this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
            this.directives = Collections.unmodifiableList(new ArrayList<>(directives));
            this.continuationAllowed = continuationAllowed;
            List<String> parts = new ArrayList<>();
            parts.add(planId);
            parts.add(sessionId);
            parts.add(planDigest);
            parts.add(targetGraphState.name());
            for (NodeRecovery node : this.nodes) {
                parts.add(node.getNodeId());
                parts.add(node.getTargetState().name());
            }
            for (Directive directive : this.directives) {
                parts.add(directive.type.name());
                parts.add(directive.subjectId);
            }
            parts.add(Boolean.toString(continuationAllowed));
            this.resultDigest = digest("graph.restart.reconciliation.v1", parts);
        }

        public String getPlanId() { return planId; }
        public String getSessionId() { return sessionId; }
        public String getPlanDigest() { return planDigest; }
        public GraphRunState getTargetGraphState() { return targetGraphState; }
        public List<NodeRecovery> getNodes() { return nodes; }
        public List<Directive> getDirectives() { return directives; }
        public boolean isContinuationAllowed() { return continuationAllowed; }
        public String getResultDigest() { return resultDigest; }
        public boolean isExecutorDispatchEnabled() { return false; }
        public boolean isProductionAuthorized() { return false; }
    }
}
