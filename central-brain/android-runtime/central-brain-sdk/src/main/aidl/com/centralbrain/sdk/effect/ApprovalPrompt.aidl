package com.centralbrain.sdk.effect;

// Stage 2 P1-W04 approval presentation/binding only. This DTO grants no authority.
// Req IDs: S2-EFF-001, S2-SAF-001, S2-UX-003, FW-U-004, FW-U-007, NV-G-005, NV-G-006.
parcelable ApprovalPrompt {
    int schemaVersion = 1;
    String approvalId = "";
    String sessionId = "";
    String planId = "";
    String nodeId = "";
    String actionId = "";
    String effectId = "";
    String planDigest = "";
    String actionDigest = "";
    String targetValueDigest = "";
    String contextDigest = "";
    long contextVersion = 0;
    String policyId = "";
    int policyVersion = 0;
    int riskClass = 0;
    String reasonCode = "";
    String promptCode = "";
    String approvalDigest = "";
    long createdAtEpochMs = 0;
    long expiresAtEpochMs = 0;
}
