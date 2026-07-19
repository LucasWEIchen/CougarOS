package com.centralbrain.sdk.orchestration;

// Metadata-only Effect projection. Target/reported values remain behind the Effect boundary.
parcelable OrchestrationEffect {
    int schemaVersion = 1;
    String effectId = "";
    String nodeId = "";
    String capabilityId = "";
    int state = 0;
    int attemptCount = 0;
    boolean simulated = false;
    String sourceId = "";
    String evidenceDigest = "";
}
