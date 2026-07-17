package com.centralbrain.runtime.graph;

import com.centralbrain.sdk.plan.PlanContract;

/** Deterministic bounded exponential backoff for graph-node retry policy. */
public final class BackoffCalculator {
    public static final long MAX_DELAY_MS = PlanContract.MAX_NODE_TIMEOUT_MS;
    public static final int MAX_JITTER_PERMILLE = 250;

    private final long baseDelayMs;
    private final long maxDelayMs;
    private final int jitterPermille;

    public BackoffCalculator(long baseDelayMs, long maxDelayMs, int jitterPermille) {
        if (baseDelayMs < 1L || baseDelayMs > MAX_DELAY_MS) {
            throw violation("baseDelayMs is outside 1.." + MAX_DELAY_MS);
        }
        if (maxDelayMs < baseDelayMs || maxDelayMs > MAX_DELAY_MS) {
            throw violation("maxDelayMs is outside baseDelayMs.." + MAX_DELAY_MS);
        }
        if (jitterPermille < 0 || jitterPermille > MAX_JITTER_PERMILLE) {
            throw violation("jitterPermille is outside 0.." + MAX_JITTER_PERMILLE);
        }
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.jitterPermille = jitterPermille;
    }

    public static BackoffCalculator boundedDefault() {
        return new BackoffCalculator(250L, 8_000L, 200);
    }

    public long calculateDelayMs(String nodeId, String retrySeedDigest, int nextAttempt) {
        NodeExecutionContract.requireNodeId(nodeId, "nodeId");
        NodeExecutionContract.requireDigest(retrySeedDigest, "retrySeedDigest");
        if (nextAttempt < 2 || nextAttempt > PlanContract.MAX_ATTEMPTS) {
            throw violation("nextAttempt is outside 2.." + PlanContract.MAX_ATTEMPTS);
        }

        long exponential = baseDelayMs;
        for (int attempt = 2; attempt < nextAttempt; attempt++) {
            exponential = Math.min(maxDelayMs, exponential * 2L);
        }
        long jitterSpan = exponential * jitterPermille / 1_000L;
        if (jitterSpan == 0L) {
            return exponential;
        }

        String jitterDigest = NodeExecutionContract.digest(
                "graph.retry.jitter.v1",
                nodeId,
                retrySeedDigest,
                Integer.toString(nextAttempt));
        long sample = Long.parseLong(jitterDigest.substring(0, 8), 16);
        long width = jitterSpan * 2L + 1L;
        long offset = sample % width - jitterSpan;
        return Math.max(1L, Math.min(maxDelayMs, exponential + offset));
    }

    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public int getJitterPermille() {
        return jitterPermille;
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_GRAPH_BACKOFF: " + message);
    }
}
