package com.centralbrain.sdk.orchestration;

// Bounded HMI/recovery projection. No node input or checkpoint payload crosses Binder.
parcelable OrchestrationNode {
    int schemaVersion = 1;
    String nodeId = "";
    String nodeType = "";
    String capabilityId = "";
    int state = 0;
    int attemptCount = 0;
    boolean required = false;
    String evidenceDigest = "";
}
