package com.centralbrain.sdk.production;

// Req IDs: XSC-001, XSC-006, NV-G-003, NV-G-006, NV-P-002.
parcelable AgentTaskRequest {
    int schemaVersion = 1;
    String clientRequestId = "";
    String sessionId = "";
    String utterance = "";
    String locale = "";
    long deadlineElapsedRealtimeMs = 0;
    int priority = 0;
    String idempotencyKey = "";
}
