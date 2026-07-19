package com.centralbrain.sdk.orchestration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class OrchestrationContractTest {
    private static final long NOW = 1_750_000_000_000L;
    private static final String SESSION = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";

    @Test
    public void validatesFailClosedProjectionDigest() {
        OrchestrationSnapshot snapshot = blocked();
        snapshot.projectionDigest = OrchestrationContract.calculateProjectionDigest(snapshot);
        OrchestrationContract.validateSnapshot(snapshot);

        snapshot.detailCode = "PRODUCTION_READY";
        assertThrows(
                IllegalArgumentException.class,
                () -> OrchestrationContract.validateSnapshot(snapshot));
    }

    @Test
    public void productionStartCannotCarrySimulatedMotion() {
        OrchestrationStartRequest request = start();
        request.executionProfile = ICentralBrainOrchestration.PROFILE_PRODUCTION;
        request.simulationMotionState = ICentralBrainOrchestration.MOTION_PARKED;
        assertThrows(
                IllegalArgumentException.class,
                () -> OrchestrationContract.validateStartRequest(request, NOW));

        request.simulationMotionState = ICentralBrainOrchestration.MOTION_UNKNOWN;
        OrchestrationContract.validateStartRequest(request, NOW);
    }

    @Test
    public void approvalResponseRequiresProjectionBinding() {
        ApprovalResponse response = new ApprovalResponse();
        response.requestId = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
        response.sessionId = SESSION;
        response.approvalId = "37b459a6-4373-4b0b-b2a4-44df3adb2aef";
        response.expectedProjectionDigest = "a".repeat(64);
        response.decision = ICentralBrainOrchestration.DECISION_APPROVE;
        response.respondedAtEpochMs = NOW;
        OrchestrationContract.validateApprovalResponse(response, NOW);

        response.expectedProjectionDigest = "";
        assertThrows(
                IllegalArgumentException.class,
                () -> OrchestrationContract.validateApprovalResponse(response, NOW));
    }

    @Test
    public void interfaceHashMatchesPublishedConstant() {
        assertEquals(
                "bd188de5d9b531c4765f0c21f99381e373c377cb4c9b8a75f2d87e6a8541fa19",
                ICentralBrainOrchestration.INTERFACE_HASH);
    }

    private static OrchestrationStartRequest start() {
        OrchestrationStartRequest request = new OrchestrationStartRequest();
        request.requestId = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
        request.sessionId = SESSION;
        request.scenarioId = "scene.fatigue.assist.v1";
        request.executionProfile = ICentralBrainOrchestration.PROFILE_DEBUG_SIMULATION;
        request.simulationMotionState = ICentralBrainOrchestration.MOTION_PARKED;
        request.requestedAtEpochMs = NOW;
        return request;
    }

    private static OrchestrationSnapshot blocked() {
        OrchestrationSnapshot snapshot = new OrchestrationSnapshot();
        snapshot.sessionId = SESSION;
        snapshot.scenarioId = "scene.fatigue.assist.v1";
        snapshot.state = ICentralBrainOrchestration.STATE_BLOCKED;
        snapshot.nodes = new OrchestrationNode[0];
        snapshot.effects = new OrchestrationEffect[0];
        snapshot.pendingStage = ICentralBrainOrchestration.PENDING_NONE;
        snapshot.detailCode = "PRODUCTION_AUTHORITIES_UNAVAILABLE";
        snapshot.updatedAtEpochMs = NOW;
        return snapshot;
    }
}
