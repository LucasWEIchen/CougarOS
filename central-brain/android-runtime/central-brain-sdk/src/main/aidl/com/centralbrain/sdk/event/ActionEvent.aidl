package com.centralbrain.sdk.event;

// Stage 2 P1-W03 typed action-event payload.
// Req IDs: S2-SES-001, S2-EVT-001, FW-U-003, FW-U-004, NV-F-009, NV-G-004.
parcelable ActionEvent {
    int schemaVersion = 1;
    String actionId = "";
    String nodeId = "";
    String capabilityId = "";
    int state = 0;
    String actionDigest = "";
    boolean required = true;
}
