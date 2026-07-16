package com.centralbrain.sdk.session;

// Req IDs: S2-SES-001, S2-UX-001, APP-004, XSC-001, XSC-006, NV-G-003.
parcelable SessionRequest {
    int schemaVersion = 1;
    String requestId = "";
    String scenarioId = "";
    String utterance = "";
    int source = 0;
    int seatZone = 0;
    String locale = "";
    long deadlineEpochMs = 0;
    int clientContextVersion = 0;
}
