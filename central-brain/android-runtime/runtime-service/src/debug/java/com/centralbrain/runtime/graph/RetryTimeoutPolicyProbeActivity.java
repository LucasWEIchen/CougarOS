package com.centralbrain.runtime.graph;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.graph.NodeRetryPolicy.Action;
import com.centralbrain.runtime.graph.NodeRetryPolicy.FailureKind;
import com.centralbrain.runtime.graph.NodeRetryPolicy.ReconcileState;
import com.centralbrain.runtime.graph.NodeRetryPolicy.RetryDecision;
import com.centralbrain.sdk.plan.NodePolicy;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;

public final class RetryTimeoutPolicyProbeActivity extends Activity {
    private static final String TAG = "CbRetryPolicy";
    private static final String PLAN_DIGEST = "a".repeat(64);
    private static final String SEED_DIGEST = "b".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            BackoffCalculator backoff = BackoffCalculator.boundedDefault();
            long secondAttempt = backoff.calculateDelayMs(
                    "capture_context", SEED_DIGEST, 2);
            boolean backoffVerified = secondAttempt
                            == backoff.calculateDelayMs("capture_context", SEED_DIGEST, 2)
                    && secondAttempt >= 200L
                    && secondAttempt <= 300L
                    && backoff.calculateDelayMs("capture_context", SEED_DIGEST, 3) >= 400L
                    && backoff.calculateDelayMs("capture_context", SEED_DIGEST, 3) <= 600L;

            PlanNode contextNode = node(
                    "capture_context", "context.capture", 3, 1_000L, "retry-context");
            NodeTimeoutPolicy timeout = NodeTimeoutPolicy.from(contextNode);
            NodeTimeoutPolicy.AttemptWindow window = timeout.openAttempt(2, 100L, 600L);
            boolean timeoutVerified = window.getDeadlineElapsedMs() == 600L
                    && window.getEffectiveTimeoutMs() == 500L
                    && window.isTruncatedByPlanDeadline()
                    && timeout.inspect(window, 599L).getStatus()
                            == NodeTimeoutPolicy.Status.ACTIVE
                    && timeout.inspect(window, 600L).getStatus()
                            == NodeTimeoutPolicy.Status.EXPIRED;

            NodeRetryPolicy retry = NodeRetryPolicy.from(contextNode);
            RetryDecision scheduled = retry.decide(
                    1,
                    FailureKind.RETRYABLE_FAILURE,
                    ReconcileState.NOT_REQUIRED,
                    1_000L,
                    10_000L,
                    PLAN_DIGEST);
            RetryDecision exhausted = retry.decide(
                    3,
                    FailureKind.TIMEOUT,
                    ReconcileState.NOT_REQUIRED,
                    2_000L,
                    10_000L,
                    PLAN_DIGEST);
            boolean attemptBudgetVerified = scheduled.getAction() == Action.RETRY
                    && scheduled.getNextAttempt() == 2
                    && scheduled.getEligibleAtElapsedMs() == 1_000L + scheduled.getDelayMs()
                    && exhausted.getAction() == Action.STOP_ATTEMPTS_EXHAUSTED;

            NodeRetryPolicy effectRetry = NodeRetryPolicy.from(node(
                    "apply_hvac", "effect.execute", 3, 1_000L, "effect-hvac-session-1"));
            boolean effectGateVerified = effectRetry.decide(
                                    1,
                                    FailureKind.DELIVERY_UNKNOWN,
                                    ReconcileState.UNKNOWN,
                                    1_000L,
                                    10_000L,
                                    PLAN_DIGEST)
                            .getAction() == Action.RECONCILE
                    && effectRetry.decide(
                                    1,
                                    FailureKind.TIMEOUT,
                                    ReconcileState.CONFIRMED_NOT_APPLIED,
                                    1_000L,
                                    10_000L,
                                    PLAN_DIGEST)
                            .getAction() == Action.RETRY
                    && effectRetry.decide(
                                    1,
                                    FailureKind.RETRYABLE_FAILURE,
                                    ReconcileState.CONFIRMED_APPLIED,
                                    1_000L,
                                    10_000L,
                                    PLAN_DIGEST)
                            .getAction() == Action.STOP_EFFECT_ALREADY_APPLIED;

            boolean deadlineVerified = retry.decide(
                                    1,
                                    FailureKind.TIMEOUT,
                                    ReconcileState.NOT_REQUIRED,
                                    Long.MAX_VALUE - 10L,
                                    Long.MAX_VALUE,
                                    PLAN_DIGEST)
                            .getAction() == Action.STOP_DEADLINE_EXCEEDED;
            boolean allVerified = backoffVerified
                    && timeoutVerified
                    && attemptBudgetVerified
                    && effectGateVerified
                    && deadlineVerified;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            Log.i(TAG, "nonce=" + nonce
                    + " retry_timeout_policy_probe_complete=true"
                    + " node_retry_policy_defined=" + allVerified
                    + " node_timeout_policy_defined=" + allVerified
                    + " backoff_deterministic_bounded_verified=" + backoffVerified
                    + " timeout_deadline_clamp_verified=" + timeoutVerified
                    + " retry_attempt_budget_verified=" + attemptBudgetVerified
                    + " effect_idempotency_reconcile_gate_verified=" + effectGateVerified
                    + " retry_deadline_fail_closed_verified=" + deadlineVerified
                    + " retry_timeout_policy_android13_arm64_verified="
                    + android13Arm64Verified
                    + " retry_timeout_policy_runtime_wired=false"
                    + " agent_graph_executor_dispatch_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " model_invoked=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " retry_timeout_policy_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " retry_timeout_policy_runtime_wired=false"
                    + " agent_graph_executor_dispatch_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        }
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
