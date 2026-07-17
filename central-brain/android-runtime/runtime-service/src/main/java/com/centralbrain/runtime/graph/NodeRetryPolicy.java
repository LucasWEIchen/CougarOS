package com.centralbrain.runtime.graph;

import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;

import java.util.Objects;

/** Fail-closed retry decision policy; it never invokes a node or Effect adapter. */
public final class NodeRetryPolicy {
    public enum FailureKind {
        RETRYABLE_FAILURE,
        TIMEOUT,
        TERMINAL_FAILURE,
        CANCELLED,
        DELIVERY_UNKNOWN
    }

    public enum ReconcileState {
        NOT_REQUIRED,
        CONFIRMED_NOT_APPLIED,
        CONFIRMED_APPLIED,
        UNKNOWN
    }

    public enum Action {
        RETRY,
        RECONCILE,
        STOP_EFFECT_ALREADY_APPLIED,
        STOP_ATTEMPTS_EXHAUSTED,
        STOP_DEADLINE_EXCEEDED,
        STOP_TERMINAL,
        STOP_CANCELLED
    }

    private final String nodeId;
    private final String nodeType;
    private final int maxAttempts;
    private final String idempotencyKeyDigest;
    private final boolean effectDispatchNode;
    private final BackoffCalculator backoffCalculator;

    private NodeRetryPolicy(PlanNode node, BackoffCalculator backoffCalculator) {
        PlanContract.validateNode(node);
        this.nodeId = node.nodeId;
        this.nodeType = node.nodeType;
        this.maxAttempts = node.maxAttempts;
        this.effectDispatchNode = "effect.execute".equals(node.nodeType)
                || "compensate".equals(node.nodeType);
        if (effectDispatchNode && node.idempotencyKey.isEmpty()) {
            throw violation("Effect retry policy requires an idempotency key");
        }
        this.idempotencyKeyDigest = NodeExecutionContract.digest(
                "graph.retry.key.v1", node.nodeId, node.idempotencyKey);
        this.backoffCalculator = Objects.requireNonNull(backoffCalculator, "backoffCalculator");
    }

    public static NodeRetryPolicy from(PlanNode node, BackoffCalculator backoffCalculator) {
        return new NodeRetryPolicy(node, backoffCalculator);
    }

    public static NodeRetryPolicy from(PlanNode node) {
        return from(node, BackoffCalculator.boundedDefault());
    }

    public RetryDecision decide(
            int completedAttempt,
            FailureKind failureKind,
            ReconcileState reconcileState,
            long nowElapsedMs,
            long planDeadlineElapsedMs,
            String planDigest) {
        Objects.requireNonNull(failureKind, "failureKind");
        Objects.requireNonNull(reconcileState, "reconcileState");
        NodeExecutionContract.requireDigest(planDigest, "planDigest");
        if (completedAttempt < 1 || completedAttempt > maxAttempts) {
            throw violation("completedAttempt is outside the configured attempt budget");
        }
        if (nowElapsedMs < 0L || planDeadlineElapsedMs < 0L) {
            throw violation("elapsed timestamps must be non-negative");
        }

        if (failureKind == FailureKind.CANCELLED) {
            return stop(Action.STOP_CANCELLED, completedAttempt, nowElapsedMs, planDigest);
        }
        if (failureKind == FailureKind.TERMINAL_FAILURE) {
            return stop(Action.STOP_TERMINAL, completedAttempt, nowElapsedMs, planDigest);
        }

        if (effectDispatchNode) {
            RetryDecision effectDecision = enforceEffectReconcileGate(
                    completedAttempt,
                    failureKind,
                    reconcileState,
                    nowElapsedMs,
                    planDigest);
            if (effectDecision != null) {
                return effectDecision;
            }
        } else {
            if (failureKind == FailureKind.DELIVERY_UNKNOWN) {
                throw violation("DELIVERY_UNKNOWN is reserved for Effect dispatch nodes");
            }
            if (reconcileState != ReconcileState.NOT_REQUIRED) {
                throw violation("non-Effect retry cannot carry reconcile state");
            }
        }

        if (completedAttempt >= maxAttempts) {
            return stop(
                    Action.STOP_ATTEMPTS_EXHAUSTED,
                    completedAttempt,
                    nowElapsedMs,
                    planDigest);
        }
        if (nowElapsedMs >= planDeadlineElapsedMs) {
            return stop(
                    Action.STOP_DEADLINE_EXCEEDED,
                    completedAttempt,
                    nowElapsedMs,
                    planDigest);
        }

        int nextAttempt = completedAttempt + 1;
        String retrySeedDigest = NodeExecutionContract.digest(
                "graph.retry.seed.v1", planDigest, nodeId, idempotencyKeyDigest);
        long delayMs = backoffCalculator.calculateDelayMs(nodeId, retrySeedDigest, nextAttempt);
        long eligibleAtElapsedMs = saturatedAdd(nowElapsedMs, delayMs);
        if (eligibleAtElapsedMs >= planDeadlineElapsedMs) {
            return stop(
                    Action.STOP_DEADLINE_EXCEEDED,
                    completedAttempt,
                    nowElapsedMs,
                    planDigest);
        }
        return decision(
                Action.RETRY,
                completedAttempt,
                nextAttempt,
                delayMs,
                eligibleAtElapsedMs,
                planDigest);
    }

    private RetryDecision enforceEffectReconcileGate(
            int completedAttempt,
            FailureKind failureKind,
            ReconcileState reconcileState,
            long nowElapsedMs,
            String planDigest) {
        if (reconcileState == ReconcileState.CONFIRMED_APPLIED) {
            return stop(
                    Action.STOP_EFFECT_ALREADY_APPLIED,
                    completedAttempt,
                    nowElapsedMs,
                    planDigest);
        }
        if (reconcileState != ReconcileState.CONFIRMED_NOT_APPLIED) {
            return stop(Action.RECONCILE, completedAttempt, nowElapsedMs, planDigest);
        }
        if (failureKind != FailureKind.RETRYABLE_FAILURE
                && failureKind != FailureKind.TIMEOUT
                && failureKind != FailureKind.DELIVERY_UNKNOWN) {
            throw violation("Effect failure kind is not retry eligible");
        }
        return null;
    }

    private RetryDecision stop(
            Action action,
            int completedAttempt,
            long nowElapsedMs,
            String planDigest) {
        return decision(
                action,
                completedAttempt,
                0,
                0L,
                nowElapsedMs,
                planDigest);
    }

    private RetryDecision decision(
            Action action,
            int completedAttempt,
            int nextAttempt,
            long delayMs,
            long eligibleAtElapsedMs,
            String planDigest) {
        String decisionDigest = NodeExecutionContract.digest(
                "graph.retry.decision.v1",
                planDigest,
                nodeId,
                nodeType,
                Integer.toString(completedAttempt),
                action.name(),
                Integer.toString(nextAttempt),
                Long.toString(delayMs),
                Long.toString(eligibleAtElapsedMs),
                idempotencyKeyDigest);
        return new RetryDecision(
                action,
                completedAttempt,
                nextAttempt,
                delayMs,
                eligibleAtElapsedMs,
                decisionDigest);
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getIdempotencyKeyDigest() {
        return idempotencyKeyDigest;
    }

    public boolean isEffectDispatchNode() {
        return effectDispatchNode;
    }

    private static long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_GRAPH_RETRY: " + message);
    }

    public static final class RetryDecision {
        private final Action action;
        private final int completedAttempt;
        private final int nextAttempt;
        private final long delayMs;
        private final long eligibleAtElapsedMs;
        private final String decisionDigest;

        private RetryDecision(
                Action action,
                int completedAttempt,
                int nextAttempt,
                long delayMs,
                long eligibleAtElapsedMs,
                String decisionDigest) {
            this.action = action;
            this.completedAttempt = completedAttempt;
            this.nextAttempt = nextAttempt;
            this.delayMs = delayMs;
            this.eligibleAtElapsedMs = eligibleAtElapsedMs;
            this.decisionDigest = decisionDigest;
        }

        public Action getAction() {
            return action;
        }

        public int getCompletedAttempt() {
            return completedAttempt;
        }

        public int getNextAttempt() {
            return nextAttempt;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public long getEligibleAtElapsedMs() {
            return eligibleAtElapsedMs;
        }

        public String getDecisionDigest() {
            return decisionDigest;
        }
    }
}
