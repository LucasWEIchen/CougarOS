package com.centralbrain.runtime.orchestration;

import com.centralbrain.sdk.orchestration.ApprovalResponse;
import com.centralbrain.sdk.orchestration.OrchestrationStartRequest;
import com.centralbrain.sdk.orchestration.UndoRequest;

/** Release-safe backend used when no trusted Context/Safety/Effect authority is configured. */
public final class FailClosedOrchestrationBackend implements OrchestrationBackend {
    @Override
    public Result start(SessionDescriptor session, OrchestrationStartRequest request) {
        String detail = request.executionProfile
                        == com.centralbrain.sdk.orchestration.ICentralBrainOrchestration
                                .PROFILE_DEBUG_SIMULATION
                ? "DEBUG_PROFILE_UNAVAILABLE"
                : "PRODUCTION_AUTHORITIES_UNAVAILABLE";
        return blocked(session, detail);
    }

    @Override
    public Result get(SessionDescriptor session) {
        return blocked(session, "ORCHESTRATION_NOT_STARTED");
    }

    @Override
    public Result respondToApproval(SessionDescriptor session, ApprovalResponse response) {
        return blocked(session, "APPROVAL_AUTHORITY_UNAVAILABLE");
    }

    @Override
    public Result requestUndo(SessionDescriptor session, UndoRequest request) {
        return blocked(session, "UNDO_AUTHORITY_UNAVAILABLE");
    }

    @Override
    public Result cancel(SessionDescriptor session, int reasonCode) {
        return blocked(session, "ORCHESTRATION_NOT_STARTED");
    }

    private static Result blocked(SessionDescriptor session, String detail) {
        return OrchestrationProjectionFactory.blocked(session, detail, System.currentTimeMillis());
    }
}
