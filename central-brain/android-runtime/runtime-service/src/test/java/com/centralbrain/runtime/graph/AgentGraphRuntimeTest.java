package com.centralbrain.runtime.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.graph.AgentGraphRuntime.GraphRunSnapshot;
import com.centralbrain.runtime.graph.AgentGraphRuntime.NodeExecutionOutcome;
import com.centralbrain.runtime.graph.AgentGraphRuntime.NodeRunSnapshot;
import com.centralbrain.sdk.plan.NodeDependency;
import com.centralbrain.sdk.plan.NodePolicy;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

import java.util.List;
import java.util.Set;

import org.junit.Test;

public final class AgentGraphRuntimeTest {
    private static final String SESSION_A = "07d954a6-f12f-488f-aaeb-afdb9601fc51";
    private static final String SESSION_B = "5ffb9872-1de1-4e2a-a94d-c5b61026179a";
    private static final String SESSION_C = "d0b0c482-1c1c-4a55-907d-f0a598828c76";
    private static final long COMPILED_AT_MS = 9_000L;
    private static final long DEADLINE_MS = 20_000L;

    @Test
    public void graphAndNodeStateTablesRejectIllegalAndTerminalTransitions() {
        assertTrue(GraphRunState.CREATED.canTransitionTo(GraphRunState.PLANNING));
        assertTrue(GraphRunState.EXECUTING.canTransitionTo(GraphRunState.WAITING));
        assertFalse(GraphRunState.CREATED.canTransitionTo(GraphRunState.EXECUTING));
        assertTrue(GraphRunState.PARTIAL.isTerminal());
        assertFalse(GraphRunState.COMPLETED.canTransitionTo(GraphRunState.WAITING));

        assertTrue(NodeRunState.PENDING.canTransitionTo(NodeRunState.READY));
        assertTrue(NodeRunState.EXECUTING.canTransitionTo(NodeRunState.WAITING));
        assertFalse(NodeRunState.PENDING.canTransitionTo(NodeRunState.SUCCEEDED));
        assertTrue(NodeRunState.SKIPPED.isTerminal());
        assertFalse(NodeRunState.FAILED.canTransitionTo(NodeRunState.READY));
    }

    @Test
    public void linearDagCompletesDeterministicallyWithoutExecutorDispatch() {
        ManualClock firstClock = new ManualClock(10_000L, 100L);
        ManualClock secondClock = new ManualClock(10_000L, 100L);
        ScenarioPlan firstInput = linearPlan(
                "0890d086-e438-4ff4-9943-39571dca99a7", SESSION_A);
        ScenarioPlan secondInput = linearPlan(
                "0890d086-e438-4ff4-9943-39571dca99a7", SESSION_A);
        AgentGraphRuntime first = runtime(firstClock, 8, 2, 32);
        AgentGraphRuntime second = runtime(secondClock, 8, 2, 32);

        assertEquals(GraphRunState.PLANNING, first.start(firstInput).getState());
        firstInput.nodes[0].nodeId = "mutated_after_admission";
        GraphRunSnapshot firstResult = executeAll(first, firstInput.planId);
        GraphRunSnapshot secondResult = executeAll(
                second, second.start(secondInput).getRunId());

        assertEquals(GraphRunState.COMPLETED, firstResult.getState());
        assertEquals(GraphRunState.COMPLETED, secondResult.getState());
        assertEquals("capture_context", firstResult.getNodes().get(0).getNodeId());
        assertEquals(firstResult.getEventDigest(), secondResult.getEventDigest());
        assertTrue(firstResult.getEventDigest().matches("[0-9a-f]{64}"));
        assertFalse(firstResult.isExecutorDispatchEnabled());
        assertFalse(firstResult.isProductionAuthorized());
        assertFalse(first.isExecutorDispatchEnabled());
        assertFalse(first.isProductionAuthorized());
    }

    @Test
    public void sameSessionIsFifoWhileDifferentSessionsUseBoundedSlots() {
        AgentGraphRuntime runtime = runtime(new ManualClock(10_000L, 100L), 8, 2, 32);
        ScenarioPlan first = singleNodePlan(
                "8284c20e-94a0-434a-8630-13418e24d649", SESSION_A);
        ScenarioPlan second = singleNodePlan(
                "2fd08844-bd02-42e5-8971-6cb5ed7be128", SESSION_A);
        ScenarioPlan third = singleNodePlan(
                "f511b10a-9695-485b-b70a-a07520ab6090", SESSION_B);
        ScenarioPlan fourth = singleNodePlan(
                "f0fa06be-7fb6-4f2c-9b5d-9f14e0b5d124", SESSION_C);

        assertEquals(GraphRunState.PLANNING, runtime.start(first).getState());
        assertEquals(GraphRunState.CREATED, runtime.start(second).getState());
        assertEquals(GraphRunState.PLANNING, runtime.start(third).getState());
        GraphRunSnapshot queued = runtime.start(fourth);
        assertEquals(GraphRunState.CREATED, queued.getState());
        assertEquals(2, runtime.activeSessionCount());

        executeAll(runtime, first.planId);
        assertEquals(GraphRunState.PLANNING, runtime.get(second.planId).getState());
        assertEquals(GraphRunState.CREATED, runtime.get(fourth.planId).getState());
        runtime.cancel(third.planId);
        assertEquals(GraphRunState.PLANNING, runtime.get(fourth.planId).getState());
        assertEquals(2, runtime.activeSessionCount());
    }

    @Test
    public void optionalFailureContinuesOnTerminalDependencyAndEndsPartial() {
        AgentGraphRuntime runtime = runtime(new ManualClock(10_000L, 100L), 8, 2, 32);
        ScenarioPlan plan = optionalPlan(
                "e6d6613f-f7e5-40bf-99c2-308e35a780ac", SESSION_A);
        runtime.start(plan);
        runtime.pump();

        NodeRunSnapshot optional = runtime.claimNextReadyNode(plan.planId);
        assertEquals("optional_context", optional.getNodeId());
        GraphRunSnapshot afterFailure = runtime.completeClaimedNode(
                plan.planId, NodeExecutionOutcome.FAILED);
        assertEquals(GraphRunState.WAITING, afterFailure.getState());
        assertEquals(NodeRunState.SKIPPED,
                node(afterFailure, "optional_context").getState());
        assertEquals(NodeRunState.READY, node(afterFailure, "render_summary").getState());

        runtime.claimNextReadyNode(plan.planId);
        GraphRunSnapshot result = runtime.completeClaimedNode(
                plan.planId, NodeExecutionOutcome.SUCCEEDED);
        assertEquals(GraphRunState.PARTIAL, result.getState());
        assertEquals(-1, result.getQueuePosition());
    }

    @Test
    public void requiredFailureFailsClosedAndInvalidSkipDoesNotLoseClaim() {
        AgentGraphRuntime runtime = runtime(new ManualClock(10_000L, 100L), 8, 2, 32);
        ScenarioPlan plan = linearPlan(
                "3c4f1e3f-b5c6-4089-af74-1a3bbfc9ff2d", SESSION_A);
        runtime.start(plan);
        runtime.pump();
        runtime.claimNextReadyNode(plan.planId);

        assertRuntimeViolation(() -> runtime.completeClaimedNode(
                plan.planId, NodeExecutionOutcome.SKIPPED));
        assertEquals(GraphRunState.EXECUTING, runtime.get(plan.planId).getState());
        GraphRunSnapshot failed = runtime.completeClaimedNode(
                plan.planId, NodeExecutionOutcome.FAILED);

        assertEquals(GraphRunState.FAILED, failed.getState());
        assertEquals(NodeRunState.FAILED, node(failed, "capture_context").getState());
        assertEquals(NodeRunState.CANCELLED, node(failed, "evaluate_policy").getState());
        assertRuntimeViolation(() -> runtime.claimNextReadyNode(plan.planId));

        AgentGraphRuntime compensationRuntime = runtime(
                new ManualClock(10_000L, 100L), 8, 2, 32);
        ScenarioPlan compensation = compensationPlan(
                "bf167349-ccf6-492c-930f-bd5a8a770b26", SESSION_B);
        compensationRuntime.start(compensation);
        compensationRuntime.pump();
        assertEquals("capture_context",
                compensationRuntime.claimNextReadyNode(compensation.planId).getNodeId());
        GraphRunSnapshot stuck = compensationRuntime.completeClaimedNode(
                compensation.planId, NodeExecutionOutcome.FAILED);
        assertEquals(GraphRunState.STUCK, stuck.getState());
        assertEquals(NodeRunState.STUCK,
                node(stuck, "rollback_context").getState());

        AgentGraphRuntime noCompensationRuntime = runtime(
                new ManualClock(10_000L, 100L), 8, 2, 32);
        ScenarioPlan noCompensation = compensationPlan(
                "3ac90f0f-7885-4848-9c99-c521d74622ca", SESSION_C);
        noCompensationRuntime.start(noCompensation);
        noCompensationRuntime.pump();
        noCompensationRuntime.claimNextReadyNode(noCompensation.planId);
        GraphRunSnapshot completed = noCompensationRuntime.completeClaimedNode(
                noCompensation.planId, NodeExecutionOutcome.SUCCEEDED);
        assertEquals(GraphRunState.COMPLETED, completed.getState());
        assertEquals(NodeRunState.SKIPPED,
                node(completed, "rollback_context").getState());
    }

    @Test
    public void waitingNodeCanResumeAndDeadlineCancelsOpenWork() {
        ManualClock clock = new ManualClock(10_000L, 100L);
        AgentGraphRuntime runtime = runtime(clock, 8, 2, 32);
        ScenarioPlan plan = linearPlan(
                "fe6d9ca2-5063-4d36-83c0-4133b318dfe9", SESSION_A);
        runtime.start(plan);
        runtime.pump();
        runtime.claimNextReadyNode(plan.planId);
        GraphRunSnapshot suspended = runtime.suspendClaimedNode(plan.planId);
        assertEquals(NodeRunState.WAITING,
                node(suspended, "capture_context").getState());
        assertRuntimeViolation(() -> runtime.claimNextReadyNode(plan.planId));
        GraphRunSnapshot resumed = runtime.resumeNode(plan.planId, "capture_context");
        assertEquals(NodeRunState.READY, node(resumed, "capture_context").getState());
        runtime.claimNextReadyNode(plan.planId);
        runtime.suspendClaimedNode(plan.planId);

        clock.advance(DEADLINE_MS - clock.epochTimeMs(), 5_000L);
        runtime.pump();
        GraphRunSnapshot expired = runtime.get(plan.planId);
        assertEquals(GraphRunState.FAILED, expired.getState());
        assertEquals(NodeRunState.CANCELLED,
                node(expired, "capture_context").getState());
        assertEquals(AgentGraphRuntime.ReasonCode.PLAN_DEADLINE_EXCEEDED,
                expired.getEvents().get(expired.getEvents().size() - 1).getReasonCode());
    }

    @Test
    public void registryCapacityAndEventProjectionAreBounded() {
        ManualClock clock = new ManualClock(10_000L, 100L);
        NodeExecutorRegistry missingSummary = NodeExecutorRegistry.controlOnly(
                Set.of("context.capture", "policy.evaluate"));
        AgentGraphRuntime incomplete = new AgentGraphRuntime(2, 1, 8, clock, missingSummary);
        assertRegistryViolation(() -> incomplete.start(linearPlan(
                "39f6072c-01f1-4cd7-98e0-2fd14bc502fe", SESSION_A)));

        AgentGraphRuntime bounded = runtime(clock, 1, 1, 3);
        ScenarioPlan first = linearPlan(
                "71508c05-61d7-4430-a595-87de86de853d", SESSION_A);
        ScenarioPlan second = singleNodePlan(
                "9b82ddb1-bffa-49d1-b87f-e884c4cf26be", SESSION_B);
        bounded.start(first);
        assertThrows(AgentGraphRuntime.CapacityExceededException.class,
                () -> bounded.start(second));
        GraphRunSnapshot result = executeAll(bounded, first.planId);
        assertEquals(3, result.getRetainedEventCount());
        assertTrue(result.getTotalEventCount() > result.getRetainedEventCount());
        assertNotEquals("0".repeat(64), result.getEventDigest());

        assertEquals(GraphRunState.PLANNING, bounded.start(second).getState());
        assertEquals(1, bounded.size());
        assertThrows(IllegalArgumentException.class, () -> new AgentGraphRuntime(
                AgentGraphRuntime.MAX_RUN_RECORDS + 1,
                1,
                8,
                clock,
                NodeExecutorRegistry.controlOnlyContractRegistry()));
    }

    private static AgentGraphRuntime runtime(
            ManualClock clock,
            int maxRuns,
            int maxSessions,
            int maxEvents) {
        return new AgentGraphRuntime(
                maxRuns,
                maxSessions,
                maxEvents,
                clock,
                NodeExecutorRegistry.controlOnlyContractRegistry());
    }

    private static GraphRunSnapshot executeAll(AgentGraphRuntime runtime, String runId) {
        int guard = 32;
        while (!runtime.get(runId).getState().isTerminal() && guard-- > 0) {
            GraphRunState state = runtime.get(runId).getState();
            if (state == GraphRunState.PLANNING) {
                runtime.pump();
                continue;
            }
            if (state == GraphRunState.WAITING) {
                NodeRunSnapshot claimed = runtime.claimNextReadyNode(runId);
                if (claimed == null) {
                    throw new AssertionError("run has no ready node");
                }
                runtime.completeClaimedNode(runId, NodeExecutionOutcome.SUCCEEDED);
                continue;
            }
            throw new AssertionError("unexpected state " + state);
        }
        if (guard <= 0) {
            throw new AssertionError("graph did not terminate");
        }
        return runtime.get(runId);
    }

    private static NodeRunSnapshot node(GraphRunSnapshot snapshot, String nodeId) {
        for (NodeRunSnapshot node : snapshot.getNodes()) {
            if (node.getNodeId().equals(nodeId)) {
                return node;
            }
        }
        throw new AssertionError("node not found " + nodeId);
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

    private static void assertRuntimeViolation(Runnable runnable) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, runnable::run);
        assertTrue(exception.getMessage().startsWith("CB_GRAPH_RUNTIME:"));
    }

    private static void assertRegistryViolation(Runnable runnable) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, runnable::run);
        assertTrue(exception.getMessage().startsWith("CB_GRAPH_REGISTRY:"));
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
