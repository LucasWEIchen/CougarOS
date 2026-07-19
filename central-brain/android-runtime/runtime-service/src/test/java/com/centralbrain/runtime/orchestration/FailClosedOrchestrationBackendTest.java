package com.centralbrain.runtime.orchestration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.centralbrain.runtime.orchestration.OrchestrationBackend.SessionDescriptor;
import com.centralbrain.sdk.orchestration.ICentralBrainOrchestration;
import com.centralbrain.sdk.orchestration.OrchestrationContract;
import com.centralbrain.sdk.orchestration.OrchestrationSnapshot;
import com.centralbrain.sdk.orchestration.OrchestrationStartRequest;

import org.junit.Test;

public final class FailClosedOrchestrationBackendTest {
    @Test
    public void productionStartReturnsStructuredBlockedProjection() {
        SessionDescriptor session = new SessionDescriptor(
                "a".repeat(64),
                "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26",
                "scene.fatigue.assist.v1",
                1,
                0,
                System.currentTimeMillis() + 60_000L,
                1L);
        OrchestrationStartRequest request = new OrchestrationStartRequest();
        request.executionProfile = ICentralBrainOrchestration.PROFILE_PRODUCTION;

        OrchestrationSnapshot snapshot = new FailClosedOrchestrationBackend()
                .start(session, request)
                .getSnapshot();

        OrchestrationContract.validateSnapshot(snapshot);
        assertEquals(ICentralBrainOrchestration.STATE_BLOCKED, snapshot.state);
        assertEquals("PRODUCTION_AUTHORITIES_UNAVAILABLE", snapshot.detailCode);
        assertFalse(snapshot.effectDispatchEnabled);
        assertFalse(snapshot.hardwareAccessed);
        assertFalse(snapshot.productionReady);
    }
}
