package com.centralbrain.sdk.governance;

// Req IDs: FW-U-004, FW-U-007, XSC-005, XSC-006, NV-G-005, NV-P-002.
parcelable ActionRequest {
    int schemaVersion = 1;
    String clientRequestId = "";
    String actionId = "";
    String idempotencyKey = "";
}
