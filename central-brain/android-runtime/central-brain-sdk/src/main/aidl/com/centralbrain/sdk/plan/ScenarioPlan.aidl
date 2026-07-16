package com.centralbrain.sdk.plan;

import com.centralbrain.sdk.plan.NodeDependency;
import com.centralbrain.sdk.plan.PlanNode;

// Stage 2 P1-W02 immutable plan transport contract.
// Req IDs: S2-SCN-001, S2-GRF-001, FW-S-001, NV-F-001, NV-F-008, NV-G-004.
parcelable ScenarioPlan {
    int schemaVersion = 1;
    String planId = "";
    String sessionId = "";
    String scenarioId = "";
    int revision = 0;
    String contextDigest = "";
    String planDigest = "";
    long compiledAtEpochMs = 0;
    long deadlineEpochMs = 0;
    PlanNode[] nodes = {};
    NodeDependency[] dependencies = {};
}
