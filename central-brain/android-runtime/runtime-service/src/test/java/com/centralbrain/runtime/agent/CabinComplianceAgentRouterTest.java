package com.centralbrain.runtime.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CabinComplianceAgentRouterTest {
    private final CabinComplianceAgentRouter router =
            new CabinComplianceAgentRouter();

    @Test
    public void explicitScenarioSelectsSmokingSpecialistWithoutModelRouting() {
        CabinComplianceAgentRouter.RouteDecision route = router.routeExplicit(
                CabinComplianceAgentRouter.SMOKING_SCENARIO_ID);

        assertEquals(CabinComplianceAgentRouter.TRIAGE_AGENT_ID,
                route.getTriageAgentId());
        assertEquals(CabinComplianceAgentRouter.SMOKING_AGENT_ID,
                route.getSpecialistAgentId());
        assertFalse(route.isModelSelectedRoute());
        assertTrue(route.getRouteDigest().matches("[0-9a-f]{64}"));
    }

    @Test
    public void triageCandidateMustCrossDeterministicAdmissionGate() {
        CabinComplianceAgentRouter.RouteDecision route = router.routeCandidate(
                CabinComplianceAgentRouter.SMOKING_CANDIDATE,
                0.8,
                CabinComplianceAgentRouter.SMOKING_SCENARIO_ID);

        assertTrue(route.isModelSelectedRoute());
        assertThrows(IllegalArgumentException.class, () -> router.routeCandidate(
                CabinComplianceAgentRouter.SMOKING_CANDIDATE,
                0.49,
                CabinComplianceAgentRouter.SMOKING_SCENARIO_ID));
        assertThrows(IllegalArgumentException.class, () -> router.routeCandidate(
                "UNKNOWN_EVENT",
                0.9,
                CabinComplianceAgentRouter.SMOKING_SCENARIO_ID));
    }
}
