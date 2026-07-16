package com.centralbrain.sdk.event;

// Stage 2 P1-W03 typed readback/observation payload. Evidence remains digest-only.
// Req IDs: S2-SES-001, S2-EVT-001, FW-U-003, NV-F-009, NV-G-004.
parcelable ObservationEvent {
    int schemaVersion = 1;
    String observationId = "";
    int subjectType = 0;
    String subjectId = "";
    int outcome = 0;
    int quality = 0;
    String evidenceDigest = "";
    boolean terminal = false;
}
