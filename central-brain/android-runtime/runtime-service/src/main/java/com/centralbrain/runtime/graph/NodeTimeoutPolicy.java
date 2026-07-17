package com.centralbrain.runtime.graph;

import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;

/** Monotonic-time attempt window bounded by both node timeout and plan deadline. */
public final class NodeTimeoutPolicy {
    public enum Status {
        ACTIVE,
        EXPIRED
    }

    private final long timeoutMs;
    private final int maxAttempts;

    private NodeTimeoutPolicy(long timeoutMs, int maxAttempts) {
        this.timeoutMs = timeoutMs;
        this.maxAttempts = maxAttempts;
    }

    public static NodeTimeoutPolicy from(PlanNode node) {
        PlanContract.validateNode(node);
        return new NodeTimeoutPolicy(node.timeoutMs, node.maxAttempts);
    }

    public AttemptWindow openAttempt(
            int attemptNumber,
            long startedAtElapsedMs,
            long planDeadlineElapsedMs) {
        if (attemptNumber < 1 || attemptNumber > maxAttempts) {
            throw violation("attemptNumber is outside the configured attempt budget");
        }
        if (startedAtElapsedMs < 0L) {
            throw violation("startedAtElapsedMs must be non-negative");
        }
        if (planDeadlineElapsedMs <= startedAtElapsedMs) {
            throw violation("plan deadline must be after attempt start");
        }
        long nodeDeadline = saturatedAdd(startedAtElapsedMs, timeoutMs);
        long effectiveDeadline = Math.min(nodeDeadline, planDeadlineElapsedMs);
        return new AttemptWindow(
                attemptNumber,
                startedAtElapsedMs,
                effectiveDeadline,
                effectiveDeadline - startedAtElapsedMs,
                effectiveDeadline == planDeadlineElapsedMs && planDeadlineElapsedMs < nodeDeadline);
    }

    public TimeoutSnapshot inspect(AttemptWindow window, long nowElapsedMs) {
        if (window == null) {
            throw violation("window is required");
        }
        if (window.attemptNumber > maxAttempts || window.effectiveTimeoutMs > timeoutMs) {
            throw violation("window does not belong to this policy");
        }
        if (nowElapsedMs < window.startedAtElapsedMs) {
            throw violation("nowElapsedMs is before attempt start");
        }
        boolean expired = nowElapsedMs >= window.deadlineElapsedMs;
        long remainingMs = expired ? 0L : window.deadlineElapsedMs - nowElapsedMs;
        return new TimeoutSnapshot(expired ? Status.EXPIRED : Status.ACTIVE, remainingMs);
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    private static long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_GRAPH_TIMEOUT: " + message);
    }

    public static final class AttemptWindow {
        private final int attemptNumber;
        private final long startedAtElapsedMs;
        private final long deadlineElapsedMs;
        private final long effectiveTimeoutMs;
        private final boolean truncatedByPlanDeadline;

        private AttemptWindow(
                int attemptNumber,
                long startedAtElapsedMs,
                long deadlineElapsedMs,
                long effectiveTimeoutMs,
                boolean truncatedByPlanDeadline) {
            this.attemptNumber = attemptNumber;
            this.startedAtElapsedMs = startedAtElapsedMs;
            this.deadlineElapsedMs = deadlineElapsedMs;
            this.effectiveTimeoutMs = effectiveTimeoutMs;
            this.truncatedByPlanDeadline = truncatedByPlanDeadline;
        }

        public int getAttemptNumber() {
            return attemptNumber;
        }

        public long getStartedAtElapsedMs() {
            return startedAtElapsedMs;
        }

        public long getDeadlineElapsedMs() {
            return deadlineElapsedMs;
        }

        public long getEffectiveTimeoutMs() {
            return effectiveTimeoutMs;
        }

        public boolean isTruncatedByPlanDeadline() {
            return truncatedByPlanDeadline;
        }
    }

    public static final class TimeoutSnapshot {
        private final Status status;
        private final long remainingMs;

        private TimeoutSnapshot(Status status, long remainingMs) {
            this.status = status;
            this.remainingMs = remainingMs;
        }

        public Status getStatus() {
            return status;
        }

        public long getRemainingMs() {
            return remainingMs;
        }
    }
}
