package com.centralbrain.runtime.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.graph.NodeRetryPolicy.Action;
import com.centralbrain.runtime.graph.NodeRetryPolicy.FailureKind;
import com.centralbrain.runtime.graph.NodeRetryPolicy.ReconcileState;
import com.centralbrain.runtime.graph.NodeRetryPolicy.RetryDecision;
import com.centralbrain.sdk.plan.NodePolicy;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;

import org.junit.Test;

public final class NodeRetryTimeoutPolicyTest {
    private static final String PLAN_DIGEST = "a".repeat(64);
    private static final String SEED_DIGEST = "b".repeat(64);

    @Test
    public void deterministicBackoffIsExponentiallyBoundedAndJittered() {
        BackoffCalculator calculator = BackoffCalculator.boundedDefault();
        long attemptTwo = calculator.calculateDelayMs("capture_context", SEED_DIGEST, 2);
        long repeated = calculator.calculateDelayMs("capture_context", SEED_DIGEST, 2);
        long attemptThree = calculator.calculateDelayMs("capture_context", SEED_DIGEST, 3);

        assertEquals(attemptTwo, repeated);
        assertTrue(attemptTwo >= 200L && attemptTwo <= 300L);
        assertTrue(attemptThree >= 400L && attemptThree <= 600L);
        assertEquals(200, calculator.getJitterPermille());
    }

    @Test
    public void timeoutWindowUsesMonotonicTimeAndPlanDeadlineClamp() {
        NodeTimeoutPolicy policy = NodeTimeoutPolicy.from(
                node("capture_context", "context.capture", 3, 1_000L, "retry-context"));
        NodeTimeoutPolicy.AttemptWindow window = policy.openAttempt(2, 100L, 600L);

        assertEquals(600L, window.getDeadlineElapsedMs());
        assertEquals(500L, window.getEffectiveTimeoutMs());
        assertTrue(window.isTruncatedByPlanDeadline());
        assertEquals(NodeTimeoutPolicy.Status.ACTIVE, policy.inspect(window, 599L).getStatus());
        assertEquals(1L, policy.inspect(window, 599L).getRemainingMs());
        assertEquals(NodeTimeoutPolicy.Status.EXPIRED, policy.inspect(window, 600L).getStatus());
        assertEquals(0L, policy.inspect(window, 600L).getRemainingMs());

        NodeTimeoutPolicy.AttemptWindow full = policy.openAttempt(1, 1_000L, 4_000L);
        assertEquals(2_000L, full.getDeadlineElapsedMs());
        assertFalse(full.isTruncatedByPlanDeadline());
    }

    @Test
    public void retryableNonEffectUsesAttemptBudgetAndStableDecision() {
        NodeRetryPolicy policy = NodeRetryPolicy.from(
                node("capture_context", "context.capture", 3, 1_000L, "retry-context"));
        RetryDecision first = policy.decide(
                1,
                FailureKind.RETRYABLE_FAILURE,
                ReconcileState.NOT_REQUIRED,
                1_000L,
                10_000L,
                PLAN_DIGEST);
        RetryDecision repeated = policy.decide(
                1,
                FailureKind.RETRYABLE_FAILURE,
                ReconcileState.NOT_REQUIRED,
                1_000L,
                10_000L,
                PLAN_DIGEST);
        RetryDecision exhausted = policy.decide(
                3,
                FailureKind.TIMEOUT,
                ReconcileState.NOT_REQUIRED,
                2_000L,
                10_000L,
                PLAN_DIGEST);

        assertEquals(Action.RETRY, first.getAction());
        assertEquals(2, first.getNextAttempt());
        assertEquals(1_000L + first.getDelayMs(), first.getEligibleAtElapsedMs());
        assertEquals(first.getDecisionDigest(), repeated.getDecisionDigest());
        assertEquals(Action.STOP_ATTEMPTS_EXHAUSTED, exhausted.getAction());
        assertEquals(0, exhausted.getNextAttempt());
    }

    @Test
    public void terminalAndCancelledFailuresNeverRetry() {
        NodeRetryPolicy policy = NodeRetryPolicy.from(
                node("capture_context", "context.capture", 3, 1_000L, "retry-context"));

        assertEquals(
                Action.STOP_TERMINAL,
                policy.decide(
                                1,
                                FailureKind.TERMINAL_FAILURE,
                                ReconcileState.NOT_REQUIRED,
                                10L,
                                100L,
                                PLAN_DIGEST)
                        .getAction());
        assertEquals(
                Action.STOP_CANCELLED,
                policy.decide(
                                1,
                                FailureKind.CANCELLED,
                                ReconcileState.NOT_REQUIRED,
                                10L,
                                100L,
                                PLAN_DIGEST)
                        .getAction());
    }

    @Test
    public void effectRetryRequiresIdempotencyAndReconcileProof() {
        PlanNode effect = node(
                "apply_hvac", "effect.execute", 3, 1_000L, "effect-hvac-session-1");
        NodeRetryPolicy policy = NodeRetryPolicy.from(effect);

        RetryDecision unknown = policy.decide(
                1,
                FailureKind.DELIVERY_UNKNOWN,
                ReconcileState.UNKNOWN,
                1_000L,
                10_000L,
                PLAN_DIGEST);
        RetryDecision confirmedNotApplied = policy.decide(
                1,
                FailureKind.TIMEOUT,
                ReconcileState.CONFIRMED_NOT_APPLIED,
                1_000L,
                10_000L,
                PLAN_DIGEST);
        RetryDecision applied = policy.decide(
                1,
                FailureKind.RETRYABLE_FAILURE,
                ReconcileState.CONFIRMED_APPLIED,
                1_000L,
                10_000L,
                PLAN_DIGEST);

        assertTrue(policy.isEffectDispatchNode());
        assertEquals(64, policy.getIdempotencyKeyDigest().length());
        assertEquals(Action.RECONCILE, unknown.getAction());
        assertEquals(Action.RETRY, confirmedNotApplied.getAction());
        assertEquals(Action.STOP_EFFECT_ALREADY_APPLIED, applied.getAction());

        effect.idempotencyKey = "";
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class, () -> NodeRetryPolicy.from(effect));
        assertTrue(missing.getMessage().startsWith("CB_PLAN_CONTRACT:"));
    }

    @Test
    public void effectReconcileRemainsRequiredAfterRetryBudgetExpires() {
        NodeRetryPolicy policy = NodeRetryPolicy.from(
                node("apply_hvac", "effect.execute", 2, 1_000L, "effect-hvac-session-1"));

        assertEquals(
                Action.RECONCILE,
                policy.decide(
                                2,
                                FailureKind.DELIVERY_UNKNOWN,
                                ReconcileState.UNKNOWN,
                                20_000L,
                                10_000L,
                                PLAN_DIGEST)
                        .getAction());
        assertEquals(
                Action.STOP_ATTEMPTS_EXHAUSTED,
                policy.decide(
                                2,
                                FailureKind.DELIVERY_UNKNOWN,
                                ReconcileState.CONFIRMED_NOT_APPLIED,
                                20_000L,
                                10_000L,
                                PLAN_DIGEST)
                        .getAction());
    }

    @Test
    public void planDeadlineAndOverflowPreventRetryDispatch() {
        NodeRetryPolicy policy = NodeRetryPolicy.from(
                node("capture_context", "context.capture", 3, 1_000L, "retry-context"));

        assertEquals(
                Action.STOP_DEADLINE_EXCEEDED,
                policy.decide(
                                1,
                                FailureKind.RETRYABLE_FAILURE,
                                ReconcileState.NOT_REQUIRED,
                                9_900L,
                                10_000L,
                                PLAN_DIGEST)
                        .getAction());
        assertEquals(
                Action.STOP_DEADLINE_EXCEEDED,
                policy.decide(
                                1,
                                FailureKind.TIMEOUT,
                                ReconcileState.NOT_REQUIRED,
                                Long.MAX_VALUE - 10L,
                                Long.MAX_VALUE,
                                PLAN_DIGEST)
                        .getAction());
    }

    @Test
    public void malformedPolicyInputsFailClosed() {
        PlanNode node = node(
                "capture_context", "context.capture", 3, 1_000L, "retry-context");
        NodeRetryPolicy retry = NodeRetryPolicy.from(node);
        NodeTimeoutPolicy timeout = NodeTimeoutPolicy.from(node);

        assertThrows(
                IllegalArgumentException.class,
                () -> new BackoffCalculator(0L, 100L, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BackoffCalculator(100L, 99L, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> BackoffCalculator.boundedDefault()
                        .calculateDelayMs("capture_context", SEED_DIGEST, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> timeout.openAttempt(0, 0L, 1_000L));
        assertThrows(
                IllegalArgumentException.class,
                () -> timeout.openAttempt(1, 1_000L, 1_000L));
        assertThrows(
                IllegalArgumentException.class,
                () -> retry.decide(
                        0,
                        FailureKind.TIMEOUT,
                        ReconcileState.NOT_REQUIRED,
                        0L,
                        1_000L,
                        PLAN_DIGEST));
        assertThrows(
                IllegalArgumentException.class,
                () -> retry.decide(
                        1,
                        FailureKind.DELIVERY_UNKNOWN,
                        ReconcileState.NOT_REQUIRED,
                        0L,
                        1_000L,
                        PLAN_DIGEST));
        assertThrows(
                IllegalArgumentException.class,
                () -> retry.decide(
                        1,
                        FailureKind.TIMEOUT,
                        ReconcileState.UNKNOWN,
                        0L,
                        1_000L,
                        PLAN_DIGEST));
    }

    private static PlanNode node(
            String nodeId,
            String nodeType,
            int maxAttempts,
            long timeoutMs,
            String idempotencyKey) {
        PlanNode node = new PlanNode();
        node.nodeId = nodeId;
        node.nodeType = nodeType;
        node.capabilityId = "";
        node.inputDigest = "c".repeat(64);
        node.resourceKey = "";
        node.timeoutMs = timeoutMs;
        node.maxAttempts = maxAttempts;
        node.idempotencyKey = idempotencyKey;
        node.required = true;
        node.compensationNodeId = "";
        NodePolicy policy = new NodePolicy();
        policy.policyId = "policy.test.retry";
        policy.policyVersion = 1;
        policy.riskClass = PlanContract.RISK_LOW;
        policy.approvalRequired = false;
        policy.verificationRequired = false;
        policy.failureMode = PlanContract.FAILURE_FAIL_PLAN;
        node.policy = policy;
        return node;
    }
}
