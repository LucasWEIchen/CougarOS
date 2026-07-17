package com.centralbrain.runtime.simulation;

/** Deterministic elapsed-realtime clock for debug/test simulation only. */
public final class SimulationClock {
    public static final long MAX_ADVANCE_MS = 24 * 60 * 60 * 1000L;

    private long elapsedRealtimeMs;

    public SimulationClock(long initialElapsedRealtimeMs) {
        if (initialElapsedRealtimeMs < 0) {
            throw new IllegalArgumentException("CB_SIM_CLOCK: initial time is invalid");
        }
        elapsedRealtimeMs = initialElapsedRealtimeMs;
    }

    public synchronized long nowElapsedRealtimeMs() {
        return elapsedRealtimeMs;
    }

    public synchronized long advanceBy(long durationMs) {
        if (durationMs < 1 || durationMs > MAX_ADVANCE_MS
                || elapsedRealtimeMs > Long.MAX_VALUE - durationMs) {
            throw new IllegalArgumentException("CB_SIM_CLOCK: advance is invalid");
        }
        elapsedRealtimeMs += durationMs;
        return elapsedRealtimeMs;
    }
}
