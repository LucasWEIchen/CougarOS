package com.centralbrain.sdk.orchestration;

import com.centralbrain.sdk.orchestration.ApprovalResponse;
import com.centralbrain.sdk.orchestration.OrchestrationSnapshot;
import com.centralbrain.sdk.orchestration.OrchestrationStartRequest;
import com.centralbrain.sdk.orchestration.UndoRequest;
import com.centralbrain.sdk.plan.ScenarioPlan;

// Independent orchestration surface. Frozen Session/Plan/Effect V1 files are not modified.
interface ICentralBrainOrchestration {
    const int INTERFACE_VERSION = 1;
    const String INTERFACE_HASH = "bd188de5d9b531c4765f0c21f99381e373c377cb4c9b8a75f2d87e6a8541fa19";

    const int PROFILE_PRODUCTION = 1;
    const int PROFILE_DEBUG_SIMULATION = 2;

    const int MOTION_UNKNOWN = 0;
    const int MOTION_PARKED = 1;
    const int MOTION_MOVING = 2;

    const int STATE_UNAVAILABLE = 0;
    const int STATE_BLOCKED = 1;
    const int STATE_PLANNING = 2;
    const int STATE_WAITING_APPROVAL = 3;
    const int STATE_RUNNING = 4;
    const int STATE_COMPLETED = 5;
    const int STATE_PARTIAL = 6;
    const int STATE_FAILED = 7;
    const int STATE_CANCELLED = 8;
    const int STATE_STUCK = 9;

    const int PENDING_NONE = 0;
    const int PENDING_APPROVAL = 1;
    const int PENDING_EFFECT = 2;
    const int PENDING_READBACK = 3;
    const int PENDING_UNDO = 4;

    const int DECISION_APPROVE = 1;
    const int DECISION_REJECT = 2;

    const int NODE_PENDING = 1;
    const int NODE_READY = 2;
    const int NODE_EXECUTING = 3;
    const int NODE_WAITING = 4;
    const int NODE_SUCCEEDED = 5;
    const int NODE_FAILED = 6;
    const int NODE_SKIPPED = 7;
    const int NODE_CANCELLED = 8;
    const int NODE_COMPENSATING = 9;
    const int NODE_COMPENSATED = 10;
    const int NODE_STUCK = 11;

    int getProtocolVersion();
    String getProtocolHash();
    OrchestrationSnapshot start(in OrchestrationStartRequest request);
    OrchestrationSnapshot getSnapshot(String sessionId);
    ScenarioPlan getPlan(String sessionId);
    OrchestrationSnapshot respondToApproval(in ApprovalResponse response);
    OrchestrationSnapshot requestUndo(in UndoRequest request);
    OrchestrationSnapshot cancel(String sessionId, int reasonCode);
}
