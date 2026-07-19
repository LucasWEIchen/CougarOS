package com.centralbrain.sdk.orchestration;

// Starts orchestration for an existing caller-owned Session. The driving field is accepted only
// by an explicitly requested debug simulation profile; it is never a production vehicle signal.
// Req IDs: S2-SCN-001, S2-GRF-001, S2-EFF-001, S2-SAF-001, XSC-001/005/006.
parcelable OrchestrationStartRequest {
    int schemaVersion = 1;
    String requestId = "";
    String sessionId = "";
    String scenarioId = "";
    int executionProfile = 0;
    int simulationMotionState = 0;
    long requestedAtEpochMs = 0;
}
