package com.centralbrain.runtime.graph;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.graph.AgentGraphRuntime.GraphRunSnapshot;
import com.centralbrain.runtime.graph.AgentGraphRuntime.NodeExecutionOutcome;
import com.centralbrain.runtime.graph.AgentGraphRuntime.NodeRunSnapshot;
import com.centralbrain.sdk.plan.NodeDependency;
import com.centralbrain.sdk.plan.NodePolicy;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

public final class AgentGraphRuntimeProbeActivity extends Activity {
    private static final String TAG = "CbAgentGraph";
    private static final String SESSION_A = "07d954a6-f12f-488f-aaeb-afdb9601fc51";
    private static final String SESSION_B = "5ffb9872-1de1-4e2a-a94d-c5b61026179a";
    private static final String SESSION_C = "d0b0c482-1c1c-4a55-907d-f0a598828c76";
    private static final String SESSION_D = "71e1c034-1316-48dc-8846-280753e3b7a4";
    private static final long COMPILED_AT_MS = 9_000L;
    private static final long DEADLINE_MS = 20_000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            ManualClock clock = new ManualClock(10_000L, 100L);
            AgentGraphRuntime runtime = new AgentGraphRuntime(
                    8,
                    2,
                    8,
                    clock,
                    NodeExecutorRegistry.controlOnlyContractRegistry());
            ScenarioPlan first = linearPlan(
                    "8284c20e-94a0-434a-8630-13418e24d649", SESSION_A);
            ScenarioPlan second = singleNodePlan(
                    "2fd08844-bd02-42e5-8971-6cb5ed7be128", SESSION_A);
            ScenarioPlan crossSession = singleNodePlan(
                    "f511b10a-9695-485b-b70a-a07520ab6090", SESSION_B);

            boolean stateVerified = runtime.start(first).getState() == GraphRunState.PLANNING;
            boolean fifoVerified = runtime.start(second).getState() == GraphRunState.CREATED;
            boolean crossSessionVerified = runtime.start(crossSession).getState()
                            == GraphRunState.PLANNING
                    && runtime.activeSessionCount() == 2;
            runtime.pump();
            GraphRunSnapshot firstResult = executeAll(runtime, first.planId);
            stateVerified = stateVerified
                    && firstResult.getState() == GraphRunState.COMPLETED
                    && firstResult.getNodes().stream().allMatch(
                            node -> node.getState() == NodeRunState.SUCCEEDED);
            fifoVerified = fifoVerified
                    && runtime.get(second.planId).getState() == GraphRunState.PLANNING;

            runtime.cancel(crossSession.planId);
            ScenarioPlan optional = optionalPlan(
                    "e6d6613f-f7e5-40bf-99c2-308e35a780ac", SESSION_C);
            runtime.start(optional);
            runtime.pump();
            NodeRunSnapshot optionalClaim = runtime.claimNextReadyNode(optional.planId);
            runtime.completeClaimedNode(optional.planId, NodeExecutionOutcome.FAILED);
            runtime.claimNextReadyNode(optional.planId);
            GraphRunSnapshot partial = runtime.completeClaimedNode(
                    optional.planId, NodeExecutionOutcome.SUCCEEDED);
            boolean partialVerified = optionalClaim.getNodeId().equals("optional_context")
                    && partial.getState() == GraphRunState.PARTIAL;
            boolean eventProjectionBounded = partial.getRetainedEventCount() == 8
                    && partial.getTotalEventCount() > partial.getRetainedEventCount()
                    && partial.getEventDigest().matches("[0-9a-f]{64}");

            ScenarioPlan deadline = singleNodePlan(
                    "fe6d9ca2-5063-4d36-83c0-4133b318dfe9", SESSION_D);
            runtime.start(deadline);
            runtime.cancel(second.planId);
            runtime.pump();
            runtime.claimNextReadyNode(deadline.planId);
            runtime.suspendClaimedNode(deadline.planId);
            clock.advance(DEADLINE_MS - clock.epochTimeMs(), 5_000L);
            runtime.pump();
            GraphRunSnapshot expired = runtime.get(deadline.planId);
            boolean deadlineVerified = expired.getState() == GraphRunState.FAILED
                    && node(expired, "capture_context").getState()
                            == NodeRunState.CANCELLED;

            ManualClock compensationClock = new ManualClock(10_000L, 100L);
            AgentGraphRuntime compensationRuntime = new AgentGraphRuntime(
                    2,
                    1,
                    8,
                    compensationClock,
                    NodeExecutorRegistry.controlOnlyContractRegistry());
            ScenarioPlan compensation = compensationPlan(
                    "bf167349-ccf6-492c-930f-bd5a8a770b26", SESSION_B);
            compensationRuntime.start(compensation);
            compensationRuntime.pump();
            compensationRuntime.claimNextReadyNode(compensation.planId);
            GraphRunSnapshot compensationResult = compensationRuntime.completeClaimedNode(
                    compensation.planId, NodeExecutionOutcome.FAILED);
            boolean compensationFailClosed = compensationResult.getState()
                            == GraphRunState.STUCK
                    && node(compensationResult, "rollback_context").getState()
                            == NodeRunState.STUCK;

            boolean noDispatch = !runtime.isExecutorDispatchEnabled()
                    && !runtime.isProductionAuthorized()
                    && !runtime.getRegistry().isDispatchEnabled()
                    && !runtime.getRegistry().isProductionAuthorized();
            boolean allVerified = stateVerified
                    && fifoVerified
                    && crossSessionVerified
                    && partialVerified
                    && deadlineVerified
                    && compensationFailClosed
                    && eventProjectionBounded
                    && noDispatch;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            Log.i(TAG, "nonce=" + nonce
                    + " agent_graph_runtime_probe_complete=true"
                    + " agent_graph_runtime_defined=" + allVerified
                    + " agent_graph_state_transition_verified=" + stateVerified
                    + " agent_graph_same_session_fifo_verified=" + fifoVerified
                    + " agent_graph_cross_session_bounded_verified="
                    + crossSessionVerified
                    + " agent_graph_partial_terminal_verified=" + partialVerified
                    + " agent_graph_deadline_verified=" + deadlineVerified
                    + " agent_graph_compensation_fail_closed_verified="
                    + compensationFailClosed
                    + " agent_graph_event_projection_bounded=" + eventProjectionBounded
                    + " agent_graph_android13_arm64_verified=" + android13Arm64Verified
                    + " agent_graph_executor_dispatch_enabled=false"
                    + " agent_graph_runtime_production_wired=false"
                    + " effect_dispatch_enabled=false"
                    + " model_invoked=false"
                    + " vehicle_signal_provider_wired=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " agent_graph_runtime_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " agent_graph_executor_dispatch_enabled=false"
                    + " agent_graph_runtime_production_wired=false"
                    + " effect_dispatch_enabled=false"
                    + " model_invoked=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static GraphRunSnapshot executeAll(AgentGraphRuntime runtime, String runId) {
        int guard = 16;
        while (!runtime.get(runId).getState().isTerminal() && guard-- > 0) {
            GraphRunState state = runtime.get(runId).getState();
            if (state == GraphRunState.PLANNING) {
                runtime.pump();
            } else if (state == GraphRunState.WAITING) {
                runtime.claimNextReadyNode(runId);
                runtime.completeClaimedNode(runId, NodeExecutionOutcome.SUCCEEDED);
            } else {
                throw new IllegalStateException("unexpected graph state " + state);
            }
        }
        if (guard <= 0) {
            throw new IllegalStateException("graph probe did not terminate");
        }
        return runtime.get(runId);
    }

    private static NodeRunSnapshot node(GraphRunSnapshot snapshot, String nodeId) {
        for (NodeRunSnapshot node : snapshot.getNodes()) {
            if (node.getNodeId().equals(nodeId)) {
                return node;
            }
        }
        throw new IllegalArgumentException("node not found");
    }

    private static ScenarioPlan singleNodePlan(String planId, String sessionId) {
        return plan(
                planId,
                sessionId,
                new PlanNode[] {node(
                        "capture_context", "context.capture", true,
                        PlanContract.FAILURE_FAIL_PLAN)},
                new NodeDependency[0]);
    }

    private static ScenarioPlan linearPlan(String planId, String sessionId) {
        return plan(
                planId,
                sessionId,
                new PlanNode[] {
                    node("capture_context", "context.capture", true,
                            PlanContract.FAILURE_FAIL_PLAN),
                    node("evaluate_policy", "policy.evaluate", true,
                            PlanContract.FAILURE_FAIL_PLAN),
                    node("render_summary", "summary.render", true,
                            PlanContract.FAILURE_FAIL_PLAN)
                },
                new NodeDependency[] {
                    dependency("capture_context", "evaluate_policy",
                            PlanContract.DEPENDENCY_ON_SUCCESS),
                    dependency("evaluate_policy", "render_summary",
                            PlanContract.DEPENDENCY_ON_SUCCESS)
                });
    }

    private static ScenarioPlan optionalPlan(String planId, String sessionId) {
        return plan(
                planId,
                sessionId,
                new PlanNode[] {
                    node("optional_context", "context.capture", false,
                            PlanContract.FAILURE_SKIP_OPTIONAL),
                    node("render_summary", "summary.render", true,
                            PlanContract.FAILURE_FAIL_PLAN)
                },
                new NodeDependency[] {dependency(
                        "optional_context", "render_summary",
                        PlanContract.DEPENDENCY_ON_TERMINAL)});
    }

    private static ScenarioPlan compensationPlan(String planId, String sessionId) {
        PlanNode trigger = node(
                "capture_context", "context.capture", true,
                PlanContract.FAILURE_COMPENSATE);
        trigger.compensationNodeId = "rollback_context";
        PlanNode compensation = node(
                "rollback_context", "compensate", true,
                PlanContract.FAILURE_FAIL_PLAN);
        compensation.idempotencyKey = "compensation-rollback-context";
        return plan(
                planId,
                sessionId,
                new PlanNode[] {trigger, compensation},
                new NodeDependency[0]);
    }

    private static ScenarioPlan plan(
            String planId,
            String sessionId,
            PlanNode[] nodes,
            NodeDependency[] dependencies) {
        ScenarioPlan plan = new ScenarioPlan();
        plan.planId = planId;
        plan.sessionId = sessionId;
        plan.scenarioId = "scene.test.graph.v1";
        plan.revision = 1;
        plan.contextDigest = "a".repeat(64);
        plan.planDigest = "b".repeat(64);
        plan.compiledAtEpochMs = COMPILED_AT_MS;
        plan.deadlineEpochMs = DEADLINE_MS;
        plan.nodes = nodes;
        plan.dependencies = dependencies;
        return plan;
    }

    private static PlanNode node(
            String nodeId,
            String nodeType,
            boolean required,
            int failureMode) {
        PlanNode node = new PlanNode();
        node.nodeId = nodeId;
        node.nodeType = nodeType;
        node.capabilityId = "";
        node.inputDigest = "c".repeat(64);
        node.resourceKey = "";
        node.timeoutMs = 1_000L;
        node.maxAttempts = 1;
        node.idempotencyKey = "";
        node.required = required;
        node.compensationNodeId = "";
        NodePolicy policy = new NodePolicy();
        policy.policyId = "policy.test.graph";
        policy.policyVersion = 1;
        policy.riskClass = PlanContract.RISK_LOW;
        policy.approvalRequired = false;
        policy.verificationRequired = false;
        policy.failureMode = failureMode;
        node.policy = policy;
        return node;
    }

    private static NodeDependency dependency(
            String prerequisite,
            String dependent,
            int condition) {
        NodeDependency dependency = new NodeDependency();
        dependency.prerequisiteNodeId = prerequisite;
        dependency.dependentNodeId = dependent;
        dependency.condition = condition;
        return dependency;
    }

    private static final class ManualClock implements AgentGraphRuntime.Clock {
        private long epochTimeMs;
        private long elapsedRealtimeMs;

        private ManualClock(long epochTimeMs, long elapsedRealtimeMs) {
            this.epochTimeMs = epochTimeMs;
            this.elapsedRealtimeMs = elapsedRealtimeMs;
        }

        @Override
        public long epochTimeMs() {
            return epochTimeMs;
        }

        @Override
        public long elapsedRealtimeMs() {
            return elapsedRealtimeMs;
        }

        private void advance(long epochDeltaMs, long elapsedDeltaMs) {
            epochTimeMs += epochDeltaMs;
            elapsedRealtimeMs += elapsedDeltaMs;
        }
    }
}
