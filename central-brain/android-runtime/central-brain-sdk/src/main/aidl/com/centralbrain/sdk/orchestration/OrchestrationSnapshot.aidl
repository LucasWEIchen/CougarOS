package com.centralbrain.sdk.orchestration;

import com.centralbrain.sdk.orchestration.OrchestrationEffect;
import com.centralbrain.sdk.orchestration.OrchestrationNode;

// Immutable metadata projection for Intent -> Plan -> Graph -> Effect -> Readback UX.
parcelable OrchestrationSnapshot {
    int schemaVersion = 1;
    String sessionId = "";
    String scenarioId = "";
    String planId = "";
    int planRevision = 0;
    String planDigest = "";
    int state = 0;
    long graphRevision = 0;
    OrchestrationNode[] nodes = {};
    OrchestrationEffect[] effects = {};
    int pendingStage = 0;
    String pendingNodeId = "";
    String pendingCapabilityId = "";
    String approvalId = "";
    String undoId = "";
    String detailCode = "";
    String projectionDigest = "";
    boolean simulated = false;
    boolean effectDispatchEnabled = false;
    boolean readbackAvailable = false;
    boolean approvalResponseAvailable = false;
    boolean approvalAuthorityTrusted = false;
    boolean undoAvailable = false;
    boolean hardwareAccessed = false;
    boolean productionReady = false;
    boolean targetHardwareValidated = false;
    long updatedAtEpochMs = 0;
}
