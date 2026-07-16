package com.centralbrain.sdk.session;

// Req IDs: S2-SES-001, S2-UX-001, APP-004, XSC-001, XSC-006, NV-G-003.
parcelable SessionSnapshot {
    int schemaVersion = 1;
    String sessionId = "";
    String requestId = "";
    String scenarioId = "";
    int state = 0;
    int activePlanRevision = 0;
    long lastEventSequence = 0;
    long createdAtEpochMs = 0;
    long updatedAtEpochMs = 0;
    long deadlineEpochMs = 0;
    String summary = "";
}
