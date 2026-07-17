package com.centralbrain.runtime.graph;

import com.centralbrain.runtime.scenario.PlanGraphValidator;
import com.centralbrain.sdk.plan.NodeDependency;
import com.centralbrain.sdk.plan.NodePolicy;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic, process-local P3-W01 graph state machine.
 *
 * <p>This class never invokes a node executor, dispatches an Effect, calls a model, or accesses
 * vehicle hardware. It serializes runs within a session and allows a bounded number of sessions
 * to make state-machine progress concurrently. Req IDs: S2-GRF-001, NV-G-004/006/007.
 */
public final class AgentGraphRuntime {
    public static final int MAX_RUN_RECORDS = 64;
    public static final int MAX_CONCURRENT_SESSIONS = 8;
    public static final int MAX_EVENT_PROJECTION = 256;
    private static final String EMPTY_DIGEST = "0".repeat(64);

    public interface Clock {
        long epochTimeMs();

        long elapsedRealtimeMs();
    }

    public enum NodeExecutionOutcome {
        SUCCEEDED,
        FAILED,
        SKIPPED
    }

    public enum ReasonCode {
        ADMITTED,
        SESSION_ACTIVE,
        SESSION_QUEUED,
        PLAN_READY,
        NODE_READY,
        NODE_CLAIMED,
        NODE_SUSPENDED,
        NODE_RESUMED,
        NODE_SUCCEEDED,
        NODE_OPTIONAL_SKIPPED,
        PLAN_COMPLETED,
        PLAN_PARTIAL,
        PLAN_FAILED,
        PLAN_CANCELLED,
        PLAN_DEADLINE_EXCEEDED,
        DEPENDENCY_UNSATISFIED,
        COMPENSATION_NOT_REQUIRED,
        COMPENSATION_UNAVAILABLE
    }

    public static final class GraphEvent {
        private final long sequence;
        private final long elapsedRealtimeMs;
        private final String nodeId;
        private final GraphRunState graphState;
        private final NodeRunState nodeState;
        private final ReasonCode reasonCode;
        private final String digest;

        private GraphEvent(
                long sequence,
                long elapsedRealtimeMs,
                String nodeId,
                GraphRunState graphState,
                NodeRunState nodeState,
                ReasonCode reasonCode,
                String digest) {
            this.sequence = sequence;
            this.elapsedRealtimeMs = elapsedRealtimeMs;
            this.nodeId = nodeId;
            this.graphState = graphState;
            this.nodeState = nodeState;
            this.reasonCode = reasonCode;
            this.digest = digest;
        }

        public long getSequence() {
            return sequence;
        }

        public long getElapsedRealtimeMs() {
            return elapsedRealtimeMs;
        }

        public String getNodeId() {
            return nodeId;
        }

        public GraphRunState getGraphState() {
            return graphState;
        }

        public NodeRunState getNodeState() {
            return nodeState;
        }

        public ReasonCode getReasonCode() {
            return reasonCode;
        }

        public String getDigest() {
            return digest;
        }
    }

    public static final class NodeRunSnapshot {
        private final String nodeId;
        private final String nodeType;
        private final NodeRunState state;
        private final boolean required;
        private final int attempt;
        private final long revision;

        private NodeRunSnapshot(NodeRecord record) {
            this.nodeId = record.node.nodeId;
            this.nodeType = record.node.nodeType;
            this.state = record.state;
            this.required = record.node.required;
            this.attempt = record.attempt;
            this.revision = record.revision;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getNodeType() {
            return nodeType;
        }

        public NodeRunState getState() {
            return state;
        }

        public boolean isRequired() {
            return required;
        }

        public int getAttempt() {
            return attempt;
        }

        public long getRevision() {
            return revision;
        }
    }

    public static final class GraphRunSnapshot {
        private final String runId;
        private final String sessionId;
        private final String scenarioId;
        private final String planDigest;
        private final GraphRunState state;
        private final long revision;
        private final int queuePosition;
        private final long deadlineEpochMs;
        private final long totalEventCount;
        private final String eventDigest;
        private final List<NodeRunSnapshot> nodes;
        private final List<GraphEvent> events;

        private GraphRunSnapshot(RunRecord record, int queuePosition) {
            this.runId = record.plan.planId;
            this.sessionId = record.plan.sessionId;
            this.scenarioId = record.plan.scenarioId;
            this.planDigest = record.plan.planDigest;
            this.state = record.state;
            this.revision = record.revision;
            this.queuePosition = queuePosition;
            this.deadlineEpochMs = record.plan.deadlineEpochMs;
            this.totalEventCount = record.eventSequence;
            this.eventDigest = record.eventDigest;
            List<NodeRunSnapshot> nodeCopies = new ArrayList<>();
            for (NodeRecord node : record.nodes.values()) {
                nodeCopies.add(new NodeRunSnapshot(node));
            }
            this.nodes = Collections.unmodifiableList(nodeCopies);
            this.events = Collections.unmodifiableList(new ArrayList<>(record.events));
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

        public GraphRunState getState() {
            return state;
        }

        public long getRevision() {
            return revision;
        }

        public int getQueuePosition() {
            return queuePosition;
        }

        public long getDeadlineEpochMs() {
            return deadlineEpochMs;
        }

        public long getTotalEventCount() {
            return totalEventCount;
        }

        public int getRetainedEventCount() {
            return events.size();
        }

        public String getEventDigest() {
            return eventDigest;
        }

        public List<NodeRunSnapshot> getNodes() {
            return nodes;
        }

        public List<GraphEvent> getEvents() {
            return events;
        }

        public boolean isExecutorDispatchEnabled() {
            return false;
        }

        public boolean isProductionAuthorized() {
            return false;
        }
    }

    private static final class NodeRecord {
        private final PlanNode node;
        private NodeRunState state = NodeRunState.PENDING;
        private int attempt;
        private long revision = 1;

        private NodeRecord(PlanNode node) {
            this.node = node;
        }
    }

    private static final class RunRecord {
        private final ScenarioPlan plan;
        private final LinkedHashMap<String, NodeRecord> nodes = new LinkedHashMap<>();
        private final List<GraphEvent> events = new ArrayList<>();
        private GraphRunState state = GraphRunState.CREATED;
        private long revision = 1;
        private long eventSequence;
        private String eventDigest = EMPTY_DIGEST;
        private boolean partialObserved;
        private String claimedNodeId = "";

        private RunRecord(ScenarioPlan plan) {
            this.plan = plan;
            for (PlanNode node : plan.nodes) {
                nodes.put(node.nodeId, new NodeRecord(node));
            }
        }
    }

    private final int maxRunRecords;
    private final int maxConcurrentSessions;
    private final int maxEventProjection;
    private final Clock clock;
    private final NodeExecutorRegistry registry;
    private final LinkedHashMap<String, RunRecord> runs = new LinkedHashMap<>();
    private final Map<String, String> activeRunBySession = new LinkedHashMap<>();

    public AgentGraphRuntime(
            int maxRunRecords,
            int maxConcurrentSessions,
            int maxEventProjection,
            Clock clock,
            NodeExecutorRegistry registry) {
        if (maxRunRecords < 1 || maxRunRecords > MAX_RUN_RECORDS) {
            throw violation("maxRunRecords outside 1.." + MAX_RUN_RECORDS);
        }
        if (maxConcurrentSessions < 1
                || maxConcurrentSessions > MAX_CONCURRENT_SESSIONS) {
            throw violation("maxConcurrentSessions outside 1.."
                    + MAX_CONCURRENT_SESSIONS);
        }
        if (maxEventProjection < 1 || maxEventProjection > MAX_EVENT_PROJECTION) {
            throw violation("maxEventProjection outside 1.." + MAX_EVENT_PROJECTION);
        }
        this.maxRunRecords = maxRunRecords;
        this.maxConcurrentSessions = maxConcurrentSessions;
        this.maxEventProjection = maxEventProjection;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.registry = Objects.requireNonNull(registry, "registry");
        if (registry.isDispatchEnabled() || registry.isProductionAuthorized()) {
            throw violation("P3-W01 registry must remain control-only");
        }
    }

    public synchronized GraphRunSnapshot start(ScenarioPlan input) {
        Objects.requireNonNull(input, "plan");
        PlanGraphValidator.validateTransport(input);
        registry.validatePlan(input);
        if (input.deadlineEpochMs <= clock.epochTimeMs()) {
            throw violation("plan deadline has already expired");
        }
        if (runs.containsKey(input.planId)) {
            throw violation("duplicate runId");
        }
        evictTerminalForCapacityLocked();
        ScenarioPlan plan = copyPlan(input);
        RunRecord record = new RunRecord(plan);
        runs.put(plan.planId, record);
        appendEventLocked(record, "", NodeRunState.PENDING, ReasonCode.ADMITTED);
        activateEligibleLocked();
        if (record.state == GraphRunState.CREATED) {
            appendEventLocked(record, "", NodeRunState.PENDING, ReasonCode.SESSION_QUEUED);
        }
        return snapshotLocked(record);
    }

    /** Advances deadline checks and PLANNING to WAITING without invoking any executor. */
    public synchronized void pump() {
        List<RunRecord> planning = new ArrayList<>();
        for (RunRecord record : runs.values()) {
            if (!record.state.isTerminal()
                    && clock.epochTimeMs() >= record.plan.deadlineEpochMs) {
                failRunLocked(record, ReasonCode.PLAN_DEADLINE_EXCEEDED);
            } else if (record.state == GraphRunState.PLANNING) {
                planning.add(record);
            }
        }
        releaseTerminalSessionsLocked();
        for (RunRecord record : planning) {
            if (record.state != GraphRunState.PLANNING) {
                continue;
            }
            transitionGraphLocked(record, GraphRunState.WAITING, ReasonCode.PLAN_READY);
            exposeReadyNodesLocked(record);
            finalizeIfSettledLocked(record);
        }
        releaseTerminalSessionsLocked();
        activateEligibleLocked();
    }

    /** Claims the first READY node in plan order. No executor is invoked. */
    public synchronized NodeRunSnapshot claimNextReadyNode(String runId) {
        RunRecord record = requireRunLocked(runId);
        requireActiveState(record, GraphRunState.WAITING);
        if (hasNodeInState(record, NodeRunState.WAITING)) {
            throw violation("a waiting node must be resumed or completed first");
        }
        for (NodeRecord node : record.nodes.values()) {
            if (node.state != NodeRunState.READY) {
                continue;
            }
            transitionNodeLocked(record, node, NodeRunState.EXECUTING,
                    ReasonCode.NODE_CLAIMED);
            node.attempt++;
            record.claimedNodeId = node.node.nodeId;
            transitionGraphLocked(record, GraphRunState.EXECUTING,
                    ReasonCode.NODE_CLAIMED);
            return new NodeRunSnapshot(node);
        }
        return null;
    }

    public synchronized GraphRunSnapshot suspendClaimedNode(String runId) {
        RunRecord record = requireRunLocked(runId);
        requireActiveState(record, GraphRunState.EXECUTING);
        NodeRecord node = requireClaimedNodeLocked(record);
        transitionNodeLocked(record, node, NodeRunState.WAITING,
                ReasonCode.NODE_SUSPENDED);
        record.claimedNodeId = "";
        transitionGraphLocked(record, GraphRunState.WAITING,
                ReasonCode.NODE_SUSPENDED);
        return snapshotLocked(record);
    }

    public synchronized GraphRunSnapshot resumeNode(String runId, String nodeId) {
        RunRecord record = requireRunLocked(runId);
        requireActiveState(record, GraphRunState.WAITING);
        NodeRecord node = requireNodeLocked(record, nodeId);
        if (node.state != NodeRunState.WAITING) {
            throw violation("node is not waiting");
        }
        transitionNodeLocked(record, node, NodeRunState.READY, ReasonCode.NODE_RESUMED);
        return snapshotLocked(record);
    }

    public synchronized GraphRunSnapshot completeClaimedNode(
            String runId,
            NodeExecutionOutcome outcome) {
        RunRecord record = requireRunLocked(runId);
        requireActiveState(record, GraphRunState.EXECUTING);
        NodeRecord node = requireClaimedNodeLocked(record);
        applyOutcomeLocked(record, node, Objects.requireNonNull(outcome, "outcome"));
        return snapshotLocked(record);
    }

    public synchronized GraphRunSnapshot completeWaitingNode(
            String runId,
            String nodeId,
            NodeExecutionOutcome outcome) {
        RunRecord record = requireRunLocked(runId);
        requireActiveState(record, GraphRunState.WAITING);
        NodeRecord node = requireNodeLocked(record, nodeId);
        if (node.state != NodeRunState.WAITING) {
            throw violation("node is not waiting");
        }
        applyOutcomeLocked(record, node, Objects.requireNonNull(outcome, "outcome"));
        return snapshotLocked(record);
    }

    public synchronized GraphRunSnapshot cancel(String runId) {
        RunRecord record = requireRunLocked(runId);
        if (record.state.isTerminal()) {
            return snapshotLocked(record);
        }
        cancelOpenNodesLocked(record);
        transitionGraphLocked(record, GraphRunState.CANCELLED, ReasonCode.PLAN_CANCELLED);
        releaseTerminalSessionsLocked();
        activateEligibleLocked();
        return snapshotLocked(record);
    }

    public synchronized GraphRunSnapshot get(String runId) {
        return snapshotLocked(requireRunLocked(runId));
    }

    public synchronized List<GraphRunSnapshot> list() {
        List<GraphRunSnapshot> snapshots = new ArrayList<>();
        for (RunRecord record : runs.values()) {
            snapshots.add(snapshotLocked(record));
        }
        return Collections.unmodifiableList(snapshots);
    }

    public synchronized int size() {
        return runs.size();
    }

    public synchronized int activeSessionCount() {
        return activeRunBySession.size();
    }

    public NodeExecutorRegistry getRegistry() {
        return registry;
    }

    public boolean isExecutorDispatchEnabled() {
        return false;
    }

    public boolean isProductionAuthorized() {
        return false;
    }

    private void applyOutcomeLocked(
            RunRecord record,
            NodeRecord node,
            NodeExecutionOutcome outcome) {
        if (outcome == NodeExecutionOutcome.SKIPPED && node.node.required) {
            throw violation("required node cannot be skipped");
        }
        record.claimedNodeId = "";
        if (outcome == NodeExecutionOutcome.SUCCEEDED) {
            transitionNodeLocked(record, node, NodeRunState.SUCCEEDED,
                    ReasonCode.NODE_SUCCEEDED);
        } else if (outcome == NodeExecutionOutcome.SKIPPED) {
            record.partialObserved = true;
            transitionNodeLocked(record, node, NodeRunState.SKIPPED,
                    ReasonCode.NODE_OPTIONAL_SKIPPED);
        } else if (node.node.policy.failureMode == PlanContract.FAILURE_COMPENSATE) {
            transitionNodeLocked(record, node, NodeRunState.FAILED,
                    ReasonCode.COMPENSATION_UNAVAILABLE);
            stuckRunLocked(record, ReasonCode.COMPENSATION_UNAVAILABLE);
            releaseTerminalSessionsLocked();
            activateEligibleLocked();
            return;
        } else if (node.node.required
                || node.node.policy.failureMode == PlanContract.FAILURE_FAIL_PLAN) {
            transitionNodeLocked(record, node, NodeRunState.FAILED, ReasonCode.PLAN_FAILED);
            failRunLocked(record, ReasonCode.PLAN_FAILED);
            releaseTerminalSessionsLocked();
            activateEligibleLocked();
            return;
        } else {
            record.partialObserved = true;
            transitionNodeLocked(record, node, NodeRunState.SKIPPED,
                    ReasonCode.NODE_OPTIONAL_SKIPPED);
        }
        exposeReadyNodesLocked(record);
        if (!record.state.isTerminal()) {
            finalizeIfSettledLocked(record);
        }
        releaseTerminalSessionsLocked();
        activateEligibleLocked();
    }

    private void exposeReadyNodesLocked(RunRecord record) {
        boolean changed;
        do {
            changed = false;
            for (NodeRecord node : record.nodes.values()) {
                if (node.state != NodeRunState.PENDING) {
                    continue;
                }
                if ("compensate".equals(node.node.nodeType)) {
                    continue;
                }
                DependencyStatus dependencies = dependencyStatus(record, node.node.nodeId);
                if (dependencies == DependencyStatus.SATISFIED) {
                    transitionNodeLocked(record, node, NodeRunState.READY,
                            ReasonCode.NODE_READY);
                    changed = true;
                } else if (dependencies == DependencyStatus.IMPOSSIBLE) {
                    if (node.node.required) {
                        transitionNodeLocked(record, node, NodeRunState.STUCK,
                                ReasonCode.DEPENDENCY_UNSATISFIED);
                        stuckRunLocked(record, ReasonCode.DEPENDENCY_UNSATISFIED);
                        return;
                    }
                    record.partialObserved = true;
                    transitionNodeLocked(record, node, NodeRunState.SKIPPED,
                            ReasonCode.DEPENDENCY_UNSATISFIED);
                    changed = true;
                }
            }
        } while (changed && !record.state.isTerminal());
    }

    private enum DependencyStatus {
        BLOCKED,
        SATISFIED,
        IMPOSSIBLE
    }

    private static DependencyStatus dependencyStatus(RunRecord record, String nodeId) {
        boolean found = false;
        boolean blocked = false;
        for (NodeDependency dependency : record.plan.dependencies) {
            if (!dependency.dependentNodeId.equals(nodeId)) {
                continue;
            }
            found = true;
            NodeRecord prerequisite = record.nodes.get(dependency.prerequisiteNodeId);
            if (dependency.condition == PlanContract.DEPENDENCY_ON_SUCCESS) {
                if (prerequisite.state.satisfiesSuccessDependency()) {
                    continue;
                }
                if (prerequisite.state.isTerminal()) {
                    return DependencyStatus.IMPOSSIBLE;
                }
                blocked = true;
            } else {
                if (!prerequisite.state.isTerminal()) {
                    blocked = true;
                }
            }
        }
        if (!found || !blocked) {
            return DependencyStatus.SATISFIED;
        }
        return DependencyStatus.BLOCKED;
    }

    private void finalizeIfSettledLocked(RunRecord record) {
        if (record.state.isTerminal()) {
            return;
        }
        boolean regularNodesTerminal = true;
        for (NodeRecord node : record.nodes.values()) {
            if (!"compensate".equals(node.node.nodeType) && !node.state.isTerminal()) {
                regularNodesTerminal = false;
                break;
            }
        }
        if (regularNodesTerminal) {
            for (NodeRecord node : record.nodes.values()) {
                if ("compensate".equals(node.node.nodeType) && !node.state.isTerminal()) {
                    transitionNodeLocked(record, node, NodeRunState.SKIPPED,
                            ReasonCode.COMPENSATION_NOT_REQUIRED);
                }
            }
        }
        boolean allTerminal = true;
        for (NodeRecord node : record.nodes.values()) {
            if (!node.state.isTerminal()) {
                allTerminal = false;
                break;
            }
        }
        if (allTerminal) {
            transitionGraphLocked(
                    record,
                    record.partialObserved ? GraphRunState.PARTIAL : GraphRunState.COMPLETED,
                    record.partialObserved
                            ? ReasonCode.PLAN_PARTIAL : ReasonCode.PLAN_COMPLETED);
            return;
        }
        if (record.state == GraphRunState.EXECUTING) {
            transitionGraphLocked(record, GraphRunState.WAITING, ReasonCode.PLAN_READY);
        }
    }

    private void failRunLocked(RunRecord record, ReasonCode reason) {
        if (record.state.isTerminal()) {
            return;
        }
        cancelOpenNodesLocked(record);
        transitionGraphLocked(record, GraphRunState.FAILED, reason);
    }

    private void stuckRunLocked(RunRecord record, ReasonCode reason) {
        if (record.state.isTerminal()) {
            return;
        }
        for (NodeRecord node : record.nodes.values()) {
            if (!node.state.isTerminal()) {
                transitionNodeLocked(record, node, NodeRunState.STUCK, reason);
            }
        }
        transitionGraphLocked(record, GraphRunState.STUCK, reason);
    }

    private void cancelOpenNodesLocked(RunRecord record) {
        for (NodeRecord node : record.nodes.values()) {
            if (!node.state.isTerminal()) {
                transitionNodeLocked(record, node, NodeRunState.CANCELLED,
                        ReasonCode.PLAN_CANCELLED);
            }
        }
        record.claimedNodeId = "";
    }

    private void transitionGraphLocked(
            RunRecord record,
            GraphRunState target,
            ReasonCode reason) {
        if (!record.state.canTransitionTo(target)) {
            throw violation("invalid graph transition " + record.state + " -> " + target);
        }
        record.state = target;
        record.revision++;
        appendEventLocked(record, "", NodeRunState.PENDING, reason);
    }

    private void transitionNodeLocked(
            RunRecord record,
            NodeRecord node,
            NodeRunState target,
            ReasonCode reason) {
        if (!node.state.canTransitionTo(target)) {
            throw violation("invalid node transition " + node.state + " -> " + target);
        }
        node.state = target;
        node.revision++;
        record.revision++;
        appendEventLocked(record, node.node.nodeId, target, reason);
    }

    private void appendEventLocked(
            RunRecord record,
            String nodeId,
            NodeRunState nodeState,
            ReasonCode reason) {
        long sequence = ++record.eventSequence;
        long elapsed = clock.elapsedRealtimeMs();
        String digest = eventDigest(
                record.eventDigest,
                sequence,
                elapsed,
                record.plan.planId,
                nodeId,
                record.state,
                nodeState,
                reason);
        record.eventDigest = digest;
        record.events.add(new GraphEvent(
                sequence,
                elapsed,
                nodeId,
                record.state,
                nodeState,
                reason,
                digest));
        while (record.events.size() > maxEventProjection) {
            record.events.remove(0);
        }
    }

    private void activateEligibleLocked() {
        if (activeRunBySession.size() >= maxConcurrentSessions) {
            return;
        }
        for (RunRecord record : runs.values()) {
            if (activeRunBySession.size() >= maxConcurrentSessions) {
                return;
            }
            if (record.state != GraphRunState.CREATED
                    || activeRunBySession.containsKey(record.plan.sessionId)) {
                continue;
            }
            activeRunBySession.put(record.plan.sessionId, record.plan.planId);
            transitionGraphLocked(record, GraphRunState.PLANNING,
                    ReasonCode.SESSION_ACTIVE);
        }
    }

    private void releaseTerminalSessionsLocked() {
        Iterator<Map.Entry<String, String>> iterator =
                activeRunBySession.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            RunRecord record = runs.get(entry.getValue());
            if (record == null || record.state.isTerminal()) {
                iterator.remove();
            }
        }
    }

    private void evictTerminalForCapacityLocked() {
        while (runs.size() >= maxRunRecords) {
            String terminalId = null;
            for (RunRecord record : runs.values()) {
                if (record.state.isTerminal()) {
                    terminalId = record.plan.planId;
                    break;
                }
            }
            if (terminalId == null) {
                throw new CapacityExceededException(maxRunRecords);
            }
            runs.remove(terminalId);
        }
    }

    private GraphRunSnapshot snapshotLocked(RunRecord record) {
        return new GraphRunSnapshot(record, queuePositionLocked(record));
    }

    private int queuePositionLocked(RunRecord target) {
        if (target.state.isTerminal()) {
            return -1;
        }
        if (activeRunBySession.getOrDefault(target.plan.sessionId, "")
                .equals(target.plan.planId)) {
            return 0;
        }
        int position = 0;
        for (RunRecord record : runs.values()) {
            if (record.state == GraphRunState.CREATED) {
                position++;
                if (record == target) {
                    return position;
                }
            }
        }
        return -1;
    }

    private RunRecord requireRunLocked(String runId) {
        RunRecord record = runs.get(runId);
        if (record == null) {
            throw violation("run not found");
        }
        return record;
    }

    private static NodeRecord requireNodeLocked(RunRecord record, String nodeId) {
        NodeRecord node = record.nodes.get(nodeId);
        if (node == null) {
            throw violation("node not found");
        }
        return node;
    }

    private static NodeRecord requireClaimedNodeLocked(RunRecord record) {
        if (record.claimedNodeId.isEmpty()) {
            throw violation("no claimed node");
        }
        return requireNodeLocked(record, record.claimedNodeId);
    }

    private static void requireActiveState(RunRecord record, GraphRunState expected) {
        if (record.state != expected) {
            throw violation("run state must be " + expected + " but was " + record.state);
        }
    }

    private static boolean hasNodeInState(RunRecord record, NodeRunState state) {
        for (NodeRecord node : record.nodes.values()) {
            if (node.state == state) {
                return true;
            }
        }
        return false;
    }

    private static ScenarioPlan copyPlan(ScenarioPlan source) {
        ScenarioPlan copy = new ScenarioPlan();
        copy.schemaVersion = source.schemaVersion;
        copy.planId = source.planId;
        copy.sessionId = source.sessionId;
        copy.scenarioId = source.scenarioId;
        copy.revision = source.revision;
        copy.contextDigest = source.contextDigest;
        copy.planDigest = source.planDigest;
        copy.compiledAtEpochMs = source.compiledAtEpochMs;
        copy.deadlineEpochMs = source.deadlineEpochMs;
        copy.nodes = new PlanNode[source.nodes.length];
        for (int index = 0; index < source.nodes.length; index++) {
            copy.nodes[index] = copyNode(source.nodes[index]);
        }
        copy.dependencies = new NodeDependency[source.dependencies.length];
        for (int index = 0; index < source.dependencies.length; index++) {
            NodeDependency original = source.dependencies[index];
            NodeDependency dependency = new NodeDependency();
            dependency.schemaVersion = original.schemaVersion;
            dependency.prerequisiteNodeId = original.prerequisiteNodeId;
            dependency.dependentNodeId = original.dependentNodeId;
            dependency.condition = original.condition;
            copy.dependencies[index] = dependency;
        }
        return copy;
    }

    private static PlanNode copyNode(PlanNode source) {
        PlanNode copy = new PlanNode();
        copy.schemaVersion = source.schemaVersion;
        copy.nodeId = source.nodeId;
        copy.nodeType = source.nodeType;
        copy.capabilityId = source.capabilityId;
        copy.inputDigest = source.inputDigest;
        copy.resourceKey = source.resourceKey;
        copy.timeoutMs = source.timeoutMs;
        copy.maxAttempts = source.maxAttempts;
        copy.idempotencyKey = source.idempotencyKey;
        copy.required = source.required;
        copy.compensationNodeId = source.compensationNodeId;
        NodePolicy policy = new NodePolicy();
        policy.schemaVersion = source.policy.schemaVersion;
        policy.policyId = source.policy.policyId;
        policy.policyVersion = source.policy.policyVersion;
        policy.riskClass = source.policy.riskClass;
        policy.approvalRequired = source.policy.approvalRequired;
        policy.verificationRequired = source.policy.verificationRequired;
        policy.failureMode = source.policy.failureMode;
        copy.policy = policy;
        return copy;
    }

    private static String eventDigest(
            String previous,
            long sequence,
            long elapsed,
            String runId,
            String nodeId,
            GraphRunState graphState,
            NodeRunState nodeState,
            ReasonCode reason) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, previous);
            update(digest, Long.toString(sequence));
            update(digest, Long.toString(elapsed));
            update(digest, runId);
            update(digest, nodeId);
            update(digest, graphState.name());
            update(digest, nodeState.name());
            update(digest, reason.name());
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_GRAPH_RUNTIME: " + message);
    }

    public static final class CapacityExceededException extends IllegalStateException {
        private CapacityExceededException(int limit) {
            super("CB_GRAPH_RUNTIME_CAPACITY: active records reached " + limit);
        }
    }
}
