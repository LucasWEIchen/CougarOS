package com.centralbrain.runtime.governance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.governance.ActionGovernancePolicy.Outcome;
import com.centralbrain.runtime.governance.ActionGovernancePolicy.Reason;
import com.centralbrain.runtime.governance.ActionGovernancePolicy.RiskClass;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.MotionState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SafetyState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SourceAssurance;

import org.junit.Test;

public final class ActionGovernancePolicyTest {
    private final ActionGovernancePolicy policy = new ActionGovernancePolicy();

    @Test
    public void classifiesOnlyStableCatalogActions() {
        assertEquals(
                RiskClass.READ_ONLY,
                policy.classify(ActionGovernancePolicy.ACTION_VEHICLE_STATE_READ));
        assertEquals(
                RiskClass.COMFORT_CONTROL,
                policy.classify(ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET));
        assertEquals(
                RiskClass.DRIVER_DISTRACTION,
                policy.classify(ActionGovernancePolicy.ACTION_DRIVER_VIDEO_PLAY));
        assertEquals(
                RiskClass.DIAGNOSTIC_WRITE,
                policy.classify(ActionGovernancePolicy.ACTION_DIAGNOSTIC_WRITE));
        assertEquals(
                RiskClass.OTA,
                policy.classify(ActionGovernancePolicy.ACTION_OTA_INSTALL));
        assertEquals(RiskClass.UNKNOWN, policy.classify("caller.claimed.low-risk"));
        assertEquals(RiskClass.UNKNOWN, policy.classify(null));
        assertEquals(5, policy.getCatalog().size());
    }

    @Test
    public void allowsReadAndComfortOnlyAsPolicyDecisions() {
        ActionGovernancePolicy.Decision read = policy.evaluate(
                ActionGovernancePolicy.ACTION_VEHICLE_STATE_READ,
                state(SafetyState.EMERGENCY, MotionState.MOVING, false));
        ActionGovernancePolicy.Decision comfort = policy.evaluate(
                ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET,
                state(SafetyState.DEGRADED, MotionState.MOVING, true));

        assertEquals(Outcome.ALLOW_POLICY_ONLY, read.getOutcome());
        assertEquals(Reason.READ_ALLOWED, read.getReason());
        assertEquals(Outcome.ALLOW_POLICY_ONLY, comfort.getOutcome());
        assertEquals(Reason.COMFORT_ALLOWED, comfort.getReason());
        assertFalse(read.isDispatchAllowed());
        assertFalse(comfort.isDispatchAllowed());
    }

    @Test
    public void requiresApprovalForParkedHighRiskActions() {
        SafetyVehicleStateSnapshot parked = state(
                SafetyState.NORMAL,
                MotionState.PARKED,
                true);
        for (String actionId : new String[]{
                ActionGovernancePolicy.ACTION_DRIVER_VIDEO_PLAY,
                ActionGovernancePolicy.ACTION_DIAGNOSTIC_WRITE,
                ActionGovernancePolicy.ACTION_OTA_INSTALL}) {
            ActionGovernancePolicy.Decision decision = policy.evaluate(actionId, parked);
            assertEquals(Outcome.APPROVAL_REQUIRED, decision.getOutcome());
            assertEquals(Reason.HIGH_RISK_APPROVAL_REQUIRED, decision.getReason());
            assertTrue(ActionGovernancePolicy.isHighRisk(decision.getRiskClass()));
            assertFalse(decision.isDispatchAllowed());
        }
    }

    @Test
    public void deniesHighRiskWhenMovingOrStateIsNotTrustworthyEnough() {
        ActionGovernancePolicy.Decision moving = policy.evaluate(
                ActionGovernancePolicy.ACTION_OTA_INSTALL,
                state(SafetyState.NORMAL, MotionState.MOVING, true));
        ActionGovernancePolicy.Decision unknownMotion = policy.evaluate(
                ActionGovernancePolicy.ACTION_DIAGNOSTIC_WRITE,
                state(SafetyState.NORMAL, MotionState.UNKNOWN, true));
        ActionGovernancePolicy.Decision emergency = policy.evaluate(
                ActionGovernancePolicy.ACTION_DRIVER_VIDEO_PLAY,
                state(SafetyState.EMERGENCY, MotionState.PARKED, true));
        ActionGovernancePolicy.Decision noDriver = policy.evaluate(
                ActionGovernancePolicy.ACTION_OTA_INSTALL,
                state(SafetyState.NORMAL, MotionState.PARKED, false));

        assertEquals(Reason.VEHICLE_MOVING_DENIED, moving.getReason());
        assertEquals(Reason.VEHICLE_MOTION_UNKNOWN, unknownMotion.getReason());
        assertEquals(Reason.SAFETY_STATE_DENIED, emergency.getReason());
        assertEquals(Reason.DRIVER_UNAVAILABLE, noDriver.getReason());
    }

    @Test
    public void runtimeOwnedProviderCannotClaimHardwareOrProductionTrust() {
        RuntimeOwnedSafetyVehicleStateProvider provider =
                new RuntimeOwnedSafetyVehicleStateProvider(() -> 42);
        SafetyVehicleStateSnapshot state = provider.currentSnapshot();

        assertEquals(RuntimeOwnedSafetyVehicleStateProvider.SOURCE_ID, state.getSourceId());
        assertEquals(42, state.getCapturedAtElapsedRealtimeMs());
        assertEquals(SourceAssurance.RUNTIME_OWNED_STUB, state.getSourceAssurance());
        assertFalse(state.isCallerControlled());
        assertFalse(state.isHardwareBacked());
        assertFalse(state.isProductionTrusted());
    }

    private static SafetyVehicleStateSnapshot state(
            SafetyState safetyState,
            MotionState motionState,
            boolean driverAvailable) {
        return new SafetyVehicleStateSnapshot(
                "test-runtime-source",
                1,
                10,
                safetyState,
                motionState,
                driverAvailable,
                SourceAssurance.RUNTIME_OWNED_STUB,
                false);
    }
}
