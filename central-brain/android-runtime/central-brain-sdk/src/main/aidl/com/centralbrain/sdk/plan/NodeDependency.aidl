package com.centralbrain.sdk.plan;

// Stage 2 P1-W02 directed edge from prerequisiteNodeId to dependentNodeId.
// Req IDs: S2-SCN-001, S2-GRF-001, FW-S-001, NV-F-001, NV-F-008, NV-G-004.
parcelable NodeDependency {
    int schemaVersion = 1;
    String prerequisiteNodeId = "";
    String dependentNodeId = "";
    int condition = 0;
}
