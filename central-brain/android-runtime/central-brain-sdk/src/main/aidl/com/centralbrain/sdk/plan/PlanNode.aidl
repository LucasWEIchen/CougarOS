package com.centralbrain.sdk.plan;

import com.centralbrain.sdk.plan.NodePolicy;

// Stage 2 P1-W02 bounded graph node. Input material remains digest-only at this boundary.
// Req IDs: S2-SCN-001, S2-GRF-001, FW-S-001, NV-F-001, NV-F-008, NV-G-004.
parcelable PlanNode {
    int schemaVersion = 1;
    String nodeId = "";
    String nodeType = "";
    String capabilityId = "";
    String inputDigest = "";
    String resourceKey = "";
    long timeoutMs = 0;
    int maxAttempts = 1;
    String idempotencyKey = "";
    boolean required = true;
    String compensationNodeId = "";
    NodePolicy policy;
}
