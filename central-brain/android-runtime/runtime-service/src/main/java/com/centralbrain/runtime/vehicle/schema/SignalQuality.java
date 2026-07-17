package com.centralbrain.runtime.vehicle.schema;

/** Quality of one canonical vehicle signal observation. */
public enum SignalQuality {
    VALID,
    STALE,
    UNAVAILABLE,
    ERROR,
    CONFLICT;

    public boolean hasScalarValue() {
        return this == VALID || this == STALE;
    }

    public boolean isUsableForDecision() {
        return this == VALID;
    }
}
