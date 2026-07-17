package com.centralbrain.runtime.vehicle.schema;

/** Provenance class for a canonical signal; it is not an authorization assertion. */
public enum SignalSource {
    SIMULATED,
    AAOS,
    VENDOR,
    DERIVED;

    public boolean isSimulated() {
        return this == SIMULATED;
    }

    public boolean isHardwareBackedCandidate() {
        return this == AAOS || this == VENDOR;
    }
}
