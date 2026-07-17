package com.centralbrain.sdk.effect;

// Stage 2 P1-W04 Effect lifecycle/readback evidence. Simulation must remain explicit.
// Req IDs: S2-EFF-001, S2-SAF-001, S2-UX-003, FW-U-003, FW-U-004, NV-F-009, NV-G-007.
parcelable EffectObservation {
    int schemaVersion = 1;
    String observationId = "";
    String effectId = "";
    String sessionId = "";
    String actionId = "";
    String planDigest = "";
    long contextVersion = 0;
    int state = 0;
    int source = 0;
    String sourceId = "";
    int attempt = 0;
    String targetValueDigest = "";
    String reportedValueDigest = "";
    String evidenceDigest = "";
    String observationDigest = "";
    String failureCode = "";
    long occurredAtEpochMs = 0;
    boolean terminal = false;
    boolean retryable = false;
    boolean simulated = false;
}
