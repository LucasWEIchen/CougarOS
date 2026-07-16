package com.centralbrain.sdk.event;

// Stage 2 P1-W03 bounded HMI message payload with explicit redaction state.
// Req IDs: S2-SES-001, S2-EVT-001, FW-U-003, NV-F-009, NV-G-004.
parcelable MessageEvent {
    int schemaVersion = 1;
    String messageId = "";
    int role = 0;
    String locale = "";
    String displayText = "";
    String contentDigest = "";
    boolean redacted = false;
    int redactionReason = 0;
}
