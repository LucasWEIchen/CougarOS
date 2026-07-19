package com.centralbrain.sdk.orchestration;

// A response request is not an approval grant. Runtime must revalidate authority and Context.
// Req IDs: S2-EFF-001, S2-SAF-001, S2-UX-003, NV-G-005/006.
parcelable ApprovalResponse {
    int schemaVersion = 1;
    String requestId = "";
    String sessionId = "";
    String approvalId = "";
    String expectedProjectionDigest = "";
    int decision = 0;
    long respondedAtEpochMs = 0;
}
