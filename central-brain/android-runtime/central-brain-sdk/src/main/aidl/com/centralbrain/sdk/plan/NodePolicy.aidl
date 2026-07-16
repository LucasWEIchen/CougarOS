package com.centralbrain.sdk.plan;

// Stage 2 P1-W02 policy metadata. Runtime Governance must re-evaluate before dispatch.
// Req IDs: S2-SCN-001, S2-GRF-001, FW-S-001, NV-F-001, NV-F-008, NV-G-004.
parcelable NodePolicy {
    int schemaVersion = 1;
    String policyId = "";
    int policyVersion = 0;
    int riskClass = 0;
    boolean approvalRequired = false;
    boolean verificationRequired = false;
    int failureMode = 0;
}
