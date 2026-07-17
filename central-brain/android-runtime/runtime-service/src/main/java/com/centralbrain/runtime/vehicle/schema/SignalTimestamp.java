package com.centralbrain.runtime.vehicle.schema;

/** Source wall time plus receive-side monotonic time used for deterministic freshness. */
public final class SignalTimestamp {
    private final long sourceEpochMs;
    private final long receivedElapsedRealtimeMs;

    public SignalTimestamp(long sourceEpochMs, long receivedElapsedRealtimeMs) {
        if (sourceEpochMs <= 0) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: source time must be positive");
        }
        if (receivedElapsedRealtimeMs < 0) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: receive time is negative");
        }
        this.sourceEpochMs = sourceEpochMs;
        this.receivedElapsedRealtimeMs = receivedElapsedRealtimeMs;
    }

    public long getSourceEpochMs() {
        return sourceEpochMs;
    }

    public long getReceivedElapsedRealtimeMs() {
        return receivedElapsedRealtimeMs;
    }

    public long ageMs(long nowElapsedRealtimeMs) {
        if (nowElapsedRealtimeMs < receivedElapsedRealtimeMs) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: receive time is in the future");
        }
        return nowElapsedRealtimeMs - receivedElapsedRealtimeMs;
    }

    public boolean isFresh(long nowElapsedRealtimeMs, long maximumAgeMs) {
        if (maximumAgeMs < 0) {
            throw new IllegalArgumentException("CB_VEHICLE_SIGNAL: maximum age is negative");
        }
        return ageMs(nowElapsedRealtimeMs) <= maximumAgeMs;
    }
}
