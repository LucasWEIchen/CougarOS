package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.context.ContextSnapshot;
import com.centralbrain.runtime.graph.AgentGraphRuntime;
import com.centralbrain.runtime.graph.GraphRunState;
import com.centralbrain.runtime.graph.NodeExecutorRegistry;
import com.centralbrain.runtime.graph.NodeRunState;
import com.centralbrain.runtime.scenario.ScenarioPlanCompiler.CompileRequest;
import com.centralbrain.runtime.scenario.ScenarioPlanCompiler.CompiledPlan;
import com.centralbrain.runtime.scenario.ScenarioResolver.CapabilitySnapshot;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Debug-only composition of scenario compilation and the control-only Agent Graph.
 *
 * <p>Context, policy and summary nodes are deterministic local projections. Approval, Effect and
 * readback nodes always wait for an explicit result supplied by a later debug composition layer.
 * This class never dispatches an Effect or reads hardware.
 */
public final class SimulatedScenarioGraph {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-p4-d4a-simulated-scenario-graph-v1";
    public static final int MAX_RUNS = 16;

    public enum PendingStage {
        NONE,
        APPROVAL,
        EFFECT,
        READBACK
    }

    public static final class PendingNode {
        private final String nodeId;
        private final String nodeType;
        private final String capabilityId;
        private final boolean required;
        private final PendingStage stage;

        private PendingNode(PlanNode node) {
            this.nodeId = node.nodeId;
            this.nodeType = node.nodeType;
            this.capabilityId = node.capabilityId;
            this.required = node.required;
            this.stage = pendingStage(node.nodeType);
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getNodeType() {
            return nodeType;
        }

        public String getCapabilityId() {
            return capabilityId;
        }

        public boolean isRequired() {
            return required;
        }

        public PendingStage getStage() {
            return stage;
        }
    }

    public static final class Snapshot {
        private final String runId;
        private final String sessionId;
        private final String scenarioId;
        private final String planDigest;
        private final int planRevision;
        private final GraphRunState graphState;
        private final long graphRevision;
        private final int automaticProjectionCount;
        private final int suppliedOutcomeCount;
        private final PendingNode pendingNode;
        private final String projectionDigest;

        private Snapshot(RunRecord record, AgentGraphRuntime.GraphRunSnapshot graph) {
            this.runId = graph.getRunId();
            this.sessionId = graph.getSessionId();
            this.scenarioId = graph.getScenarioId();
            this.planDigest = graph.getPlanDigest();
            this.planRevision = record.plan.revision;
            this.graphState = graph.getState();
            this.graphRevision = graph.getRevision();
            this.automaticProjectionCount = record.automaticProjectionCount;
            this.suppliedOutcomeCount = record.suppliedOutcomeCount;
            this.pendingNode = record.pendingNodeId.isEmpty()
                    ? null : new PendingNode(record.nodes.get(record.pendingNodeId));
            this.projectionDigest = digest(
                    PROFILE_ID,
                    runId,
                    sessionId,
                    scenarioId,
                    planDigest,
                    Integer.toString(planRevision),
                    graphState.name(),
                    Long.toString(graphRevision),
                    Integer.toString(automaticProjectionCount),
                    Integer.toString(suppliedOutcomeCount),
                    pendingNode == null ? "" : pendingNode.nodeId,
                    pendingNode == null ? PendingStage.NONE.name() : pendingNode.stage.name(),
                    graph.getEventDigest());
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

        public long getGraphRevision() {
            return graphRevision;
        }

        public int getAutomaticProjectionCount() {
            return automaticProjectionCount;
        }

        public int getSuppliedOutcomeCount() {
            return suppliedOutcomeCount;
        }

        public PendingNode getPendingNode() {
            return pendingNode;
        }

        public String getProjectionDigest() {
            return projectionDigest;
        }

        public boolean isPlanPublished() {
            return true;
        }

        public boolean isGraphProgressEnabled() {
            return true;
        }

        public boolean isSimulationOnly() {
            return true;
        }

        public boolean isEffectDispatchEnabled() {
            return false;
        }

        public boolean isReadbackAccessed() {
            return false;
        }

        public boolean isApprovalResponseAuthorityAvailable() {
            return false;
        }

        public boolean isAndroidRuntimeWired() {
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
        private final ScenarioPlan plan;
        private final Map<String, PlanNode> nodes;
        private int automaticProjectionCount;
        private int suppliedOutcomeCount;
        private String pendingNodeId = "";

        private RunRecord(ScenarioPlan plan) {
            this.plan = plan;
            Map<String, PlanNode> indexed = new LinkedHashMap<>();
            for (PlanNode node : plan.nodes) {
                indexed.put(node.nodeId, node);
            }
            this.nodes = Collections.unmodifiableMap(indexed);
        }
    }

    private final ScenarioPlanCompiler compiler;
    private final AgentGraphRuntime graph;
    private final Map<String, RunRecord> runs = new LinkedHashMap<>();

    public SimulatedScenarioGraph(AgentGraphRuntime.Clock clock) {
        this.compiler = new ScenarioPlanCompiler();
        this.graph = new AgentGraphRuntime(
                MAX_RUNS,
                AgentGraphRuntime.MAX_CONCURRENT_SESSIONS,
                AgentGraphRuntime.MAX_EVENT_PROJECTION,
                Objects.requireNonNull(clock, "clock"),
                NodeExecutorRegistry.controlOnlyContractRegistry());
    }

    public synchronized Snapshot start(
            CompileRequest request,
            ScenarioResolution resolution,
            ContextSnapshot context,
            CapabilitySnapshot capabilities) {
        if (runs.size() >= MAX_RUNS) {
            throw violation("run capacity exhausted");
        }
        CompiledPlan compiled = compiler.compile(request, resolution, context, capabilities);
        ScenarioPlan plan = compiled.toScenarioPlan();
        validateNodeTypes(plan);
        RunRecord record = new RunRecord(plan);
        graph.start(plan);
        runs.put(plan.planId, record);
        return advanceAutomatic(record);
    }

    public synchronized Snapshot supplyPendingOutcome(
            String runId,
            AgentGraphRuntime.NodeExecutionOutcome outcome) {
        RunRecord record = requireRun(runId);
        if (record.pendingNodeId.isEmpty()) {
            throw violation("run has no pending external node");
        }
        graph.completeWaitingNode(
                runId,
                record.pendingNodeId,
                Objects.requireNonNull(outcome, "outcome"));
        record.pendingNodeId = "";
        record.suppliedOutcomeCount++;
        return advanceAutomatic(record);
    }

    public synchronized Snapshot cancel(String runId) {
        RunRecord record = requireRun(runId);
        record.pendingNodeId = "";
        return new Snapshot(record, graph.cancel(runId));
    }

    public synchronized Snapshot get(String runId) {
        RunRecord record = requireRun(runId);
        return new Snapshot(record, graph.get(runId));
    }

    public synchronized int size() {
        return runs.size();
    }

    public boolean isProductionRegistered() {
        return false;
    }

    private Snapshot advanceAutomatic(RunRecord record) {
        while (true) {
            AgentGraphRuntime.GraphRunSnapshot snapshot = graph.get(record.plan.planId);
            if (snapshot.getState().isTerminal()) {
                record.pendingNodeId = "";
                return new Snapshot(record, snapshot);
            }
            if (snapshot.getState() == GraphRunState.PLANNING) {
                graph.pump();
                continue;
            }
            if (snapshot.getState() != GraphRunState.WAITING) {
                throw violation("graph is not ready for deterministic progress");
            }
            AgentGraphRuntime.NodeRunSnapshot claimed =
                    graph.claimNextReadyNode(record.plan.planId);
            if (claimed == null) {
                return new Snapshot(record, graph.get(record.plan.planId));
            }
            PlanNode node = record.nodes.get(claimed.getNodeId());
            if (node == null) {
                throw violation("claimed node is absent from compiled Plan");
            }
            if (isAutomaticProjection(node.nodeType)) {
                graph.completeClaimedNode(
                        record.plan.planId,
                        AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED);
                record.automaticProjectionCount++;
                continue;
            }
            graph.suspendClaimedNode(record.plan.planId);
            record.pendingNodeId = node.nodeId;
            return new Snapshot(record, graph.get(record.plan.planId));
        }
    }

    private RunRecord requireRun(String runId) {
        RunRecord record = runs.get(runId);
        if (record == null) {
            throw violation("run is unknown");
        }
        return record;
    }

    private static void validateNodeTypes(ScenarioPlan plan) {
        for (PlanNode node : plan.nodes) {
            if (!isAutomaticProjection(node.nodeType)
                    && pendingStage(node.nodeType) == PendingStage.NONE) {
                throw violation("compiled Plan contains an unsupported node type");
            }
        }
    }

    private static boolean isAutomaticProjection(String nodeType) {
        return "context.capture".equals(nodeType)
                || "policy.evaluate".equals(nodeType)
                || "summary.render".equals(nodeType);
    }

    private static PendingStage pendingStage(String nodeType) {
        if ("approval.interrupt".equals(nodeType)) {
            return PendingStage.APPROVAL;
        }
        if ("effect.execute".equals(nodeType)) {
            return PendingStage.EFFECT;
        }
        if ("effect.verify".equals(nodeType)) {
            return PendingStage.READBACK;
        }
        return PendingStage.NONE;
    }

    private static String digest(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (bytes.length >>> 24));
                digest.update((byte) (bytes.length >>> 16));
                digest.update((byte) (bytes.length >>> 8));
                digest.update((byte) bytes.length);
                digest.update(bytes);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_SIM_SCENARIO_GRAPH: " + message);
    }
}
