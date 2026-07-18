package com.centralbrain.runtime.governance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.governance.DriverSafetyAdmissionContract.AdmissionRequest;
import com.centralbrain.runtime.governance.DriverSafetyAdmissionContract.CapabilityEvidence;
import com.centralbrain.runtime.governance.DriverSafetyAdmissionContract.Decision;
import com.centralbrain.runtime.governance.DriverSafetyAdmissionContract.DecisionCode;
import com.centralbrain.runtime.governance.DriverSafetyAdmissionContract.Outcome;
import com.centralbrain.runtime.governance.DriverSafetyAdmissionContract.OwnerApproval;
import com.centralbrain.runtime.governance.DriverSafetyAdmissionContract.OwnerRole;
import com.centralbrain.runtime.governance.DriverSafetyAdmissionContract.PolicyProfile;
import com.centralbrain.runtime.governance.DriverSafetyAdmissionContract.UxProfile;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.MotionState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SafetyState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SourceAssurance;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class DriverSafetyAdmissionContractTest {
    private static final long CAPTURED_AT_MS = 1_000;
    private static final long OBSERVED_AT_MS = 1_100;

    @Test
    public void exactCatalogAndUxOnlyActionsNeverGrantEffectAuthority() {
        assertEquals(
                DriverSafetyAdmissionContract.ACTION_RULE_COUNT,
                DriverSafetyAdmissionContract.actionRules().size());
        assertEquals(64, DriverSafetyAdmissionContract.catalogDigest().length());
        Decision scene = evaluate(
                DriverSafetyAdmissionContract.ACTION_SCENE_INTENT_SUBMIT,
                null,
                null,
                null,
                0);
        Decision cancel = evaluate(
                DriverSafetyAdmissionContract.ACTION_SESSION_CANCEL,
                runtimeStub(MotionState.PARKED),
                null,
                null,
                OBSERVED_AT_MS);

        assertEquals(DecisionCode.ADMITTED_UI_ONLY, scene.getCode());
        assertEquals(UxProfile.UNKNOWN_RESTRICTED, scene.getUxProfile());
        assertEquals(Outcome.ALLOW_UI_ONLY, cancel.getOutcome());
        assertFalse(scene.isEffectDispatchAuthorized());
        assertFalse(cancel.isHardwareOperationExecuted());
        assertNull(DriverSafetyAdmissionContract.actionRules().get("not.cataloged"));
    }

    @Test
    public void staleFutureAndUntrustedStateFailClosed() {
        Decision stale = evaluate(
                ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET,
                trusted(SafetyState.NORMAL, MotionState.PARKED, true, CAPTURED_AT_MS),
                approvedPolicy(),
                capability(VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE, true, true, true),
                CAPTURED_AT_MS + DriverSafetyAdmissionContract.MAXIMUM_STATE_AGE_MS + 1);
        Decision future = evaluate(
                ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET,
                trusted(SafetyState.NORMAL, MotionState.PARKED, true, CAPTURED_AT_MS + 1),
                approvedPolicy(),
                capability(VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE, true, true, true),
                CAPTURED_AT_MS);
        Decision untrusted = evaluate(
                ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET,
                runtimeStub(MotionState.PARKED),
                approvedPolicy(),
                capability(VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE, true, true, true),
                OBSERVED_AT_MS);

        assertEquals(DecisionCode.STATE_STALE, stale.getCode());
        assertEquals(DecisionCode.STATE_TIME_INVALID, future.getCode());
        assertEquals(DecisionCode.STATE_SOURCE_UNTRUSTED, untrusted.getCode());
        assertEquals(UxProfile.UNKNOWN_RESTRICTED, untrusted.getUxProfile());
    }

    @Test
    public void movingProfileAllowsGovernedComfortButHardDeniesDistractionAndRecline() {
        SafetyVehicleStateSnapshot moving = trusted(
                SafetyState.NORMAL,
                MotionState.MOVING,
                true,
                CAPTURED_AT_MS);
        Decision hvac = evaluate(
                ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET,
                moving,
                approvedPolicy(),
                capability(VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE, true, true, true),
                OBSERVED_AT_MS);
        Decision longText = evaluate(
                DriverSafetyAdmissionContract.ACTION_UI_LONG_TEXT_DISPLAY,
                moving,
                approvedPolicy(),
                null,
                OBSERVED_AT_MS);
        Decision recline = evaluate(
                DriverSafetyAdmissionContract.ACTION_DRIVER_SEAT_RECLINE_SET,
                moving,
                approvedPolicy(),
                capability(VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE, true, true, true),
                OBSERVED_AT_MS);

        assertEquals(DecisionCode.ADMITTED_POLICY_ONLY, hvac.getCode());
        assertEquals(UxProfile.MOVING_RESTRICTED, hvac.getUxProfile());
        assertEquals(DecisionCode.MOVING_HARD_INTERLOCK, longText.getCode());
        assertEquals(DecisionCode.MOVING_HARD_INTERLOCK, recline.getCode());
        assertFalse(hvac.isEffectDispatchAuthorized());
    }

    @Test
    public void parkedDriverSeatReclineRequiresApprovalAndNeverDispatches() {
        Decision decision = evaluate(
                DriverSafetyAdmissionContract.ACTION_DRIVER_SEAT_RECLINE_SET,
                trusted(SafetyState.NORMAL, MotionState.PARKED, true, CAPTURED_AT_MS),
                approvedPolicy(),
                capability(VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE, true, true, true),
                OBSERVED_AT_MS);

        assertEquals(DecisionCode.APPROVAL_REQUIRED, decision.getCode());
        assertEquals(Outcome.APPROVAL_REQUIRED, decision.getOutcome());
        assertEquals(UxProfile.PARKED_FULL, decision.getUxProfile());
        assertEquals(64, decision.getDecisionDigest().length());
        assertFalse(decision.isEffectDispatchAuthorized());
    }

    @Test
    public void ownerPolicyMustContainThreeUniqueDigestBoundRoles() {
        SafetyVehicleStateSnapshot parked = trusted(
                SafetyState.NORMAL, MotionState.PARKED, true, CAPTURED_AT_MS);
        CapabilityEvidence evidence = capability(
                VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE, true, true, true);
        Decision missing = evaluate(
                ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET,
                parked,
                DriverSafetyAdmissionContract.currentDraftPolicy(),
                evidence,
                OBSERVED_AT_MS);
        List<OwnerApproval> duplicate = new ArrayList<>(approvedPolicy().getApprovals());
        duplicate.set(2, duplicate.get(0));
        Decision duplicateRole = evaluate(
                ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET,
                parked,
                new PolicyProfile(
                        DriverSafetyAdmissionContract.PROFILE_ID,
                        DriverSafetyAdmissionContract.SCHEMA_VERSION,
                        DriverSafetyAdmissionContract.catalogDigest(),
                        duplicate),
                evidence,
                OBSERVED_AT_MS);

        assertEquals(DecisionCode.OWNER_POLICY_MISSING, missing.getCode());
        assertEquals(DecisionCode.OWNER_POLICY_INVALID, duplicateRole.getCode());
    }

    @Test
    public void capabilityAvailabilityAuthorizationReadbackAndActivationAreIndependentGates() {
        SafetyVehicleStateSnapshot parked = trusted(
                SafetyState.NORMAL, MotionState.PARKED, true, CAPTURED_AT_MS);
        assertCode(DecisionCode.CAPABILITY_EVIDENCE_MISSING, parked, null);
        assertCode(DecisionCode.CAPABILITY_UNAVAILABLE, parked,
                capability(VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE,
                        false, true, true));
        assertCode(DecisionCode.CAPABILITY_UNAUTHORIZED, parked,
                capability(VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE,
                        true, false, true));
        assertCode(DecisionCode.READBACK_UNAVAILABLE, parked,
                capability(VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE,
                        true, true, false));
        assertEquals(
                DecisionCode.ACTIVATION_EVIDENCE_MISSING,
                evaluate(
                        ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET,
                        parked,
                        approvedPolicy(),
                        new CapabilityEvidence(
                                VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE
                                        .getCanonicalId(),
                                true,
                                true,
                                true,
                                null),
                        OBSERVED_AT_MS).getCode());
    }

    @Test
    public void emergencyUnknownMotionAndMissingDriverRemainRestricted() {
        PolicyProfile policy = approvedPolicy();
        CapabilityEvidence evidence = capability(
                VehicleCapability.CapabilityId.SEAT_HEATING_LEVEL, true, true, true);
        Decision emergency = evaluate(
                DriverSafetyAdmissionContract.ACTION_DRIVER_SEAT_HEATING_SET,
                trusted(SafetyState.EMERGENCY, MotionState.PARKED, true, CAPTURED_AT_MS),
                policy,
                evidence,
                OBSERVED_AT_MS);
        Decision unknownMotion = evaluate(
                DriverSafetyAdmissionContract.ACTION_DRIVER_SEAT_HEATING_SET,
                trusted(SafetyState.NORMAL, MotionState.UNKNOWN, true, CAPTURED_AT_MS),
                policy,
                evidence,
                OBSERVED_AT_MS);
        Decision degraded = evaluate(
                DriverSafetyAdmissionContract.ACTION_DRIVER_SEAT_HEATING_SET,
                trusted(SafetyState.DEGRADED, MotionState.PARKED, true, CAPTURED_AT_MS),
                policy,
                evidence,
                OBSERVED_AT_MS);
        Decision missingDriver = evaluate(
                DriverSafetyAdmissionContract.ACTION_DRIVER_SEAT_HEATING_SET,
                trusted(SafetyState.NORMAL, MotionState.PARKED, false, CAPTURED_AT_MS),
                policy,
                evidence,
                OBSERVED_AT_MS);

        assertEquals(DecisionCode.SAFETY_STATE_RESTRICTED, emergency.getCode());
        assertEquals(UxProfile.FAULT_RESTRICTED, emergency.getUxProfile());
        assertEquals(DecisionCode.SAFETY_STATE_RESTRICTED, degraded.getCode());
        assertEquals(UxProfile.FAULT_RESTRICTED, degraded.getUxProfile());
        assertEquals(DecisionCode.MOTION_UNKNOWN, unknownMotion.getCode());
        assertEquals(DecisionCode.DRIVER_UNAVAILABLE, missingDriver.getCode());
    }

    @Test
    public void repositoryClaimsRemainOwnerBlockedUnwiredAndUnverified() {
        assertFalse(DriverSafetyAdmissionContract.isCurrentOwnerPolicyApproved());
        assertFalse(DriverSafetyAdmissionContract.isVehicleStateProviderWired());
        assertFalse(DriverSafetyAdmissionContract.isEffectRuntimeWired());
        assertFalse(DriverSafetyAdmissionContract.isAndroid13Arm64Verified());
        assertFalse(DriverSafetyAdmissionContract.isHardwareAccessed());
        assertFalse(DriverSafetyAdmissionContract.isProductionReady());
        assertFalse(DriverSafetyAdmissionContract.isTargetHardwareValidated());
        assertTrue(DriverSafetyAdmissionContract.currentDraftPolicy().getApprovals().isEmpty());
    }

    private static void assertCode(
            DecisionCode expected,
            SafetyVehicleStateSnapshot state,
            CapabilityEvidence evidence) {
        assertEquals(
                expected,
                evaluate(
                        ActionGovernancePolicy.ACTION_CABIN_TEMPERATURE_SET,
                        state,
                        approvedPolicy(),
                        evidence,
                        OBSERVED_AT_MS).getCode());
    }

    private static Decision evaluate(
            String actionId,
            SafetyVehicleStateSnapshot state,
            PolicyProfile policy,
            CapabilityEvidence evidence,
            long observedAtMs) {
        return DriverSafetyAdmissionContract.evaluate(new AdmissionRequest(
                actionId,
                state,
                observedAtMs,
                policy,
                evidence));
    }

    private static PolicyProfile approvedPolicy() {
        String catalogDigest = DriverSafetyAdmissionContract.catalogDigest();
        List<OwnerApproval> approvals = List.of(
                approval(OwnerRole.FUNCTIONAL_SAFETY, digest('a')),
                approval(OwnerRole.DRIVER_DISTRACTION_HMI, digest('b')),
                approval(OwnerRole.VEHICLE_INTEGRATION, digest('c')));
        return new PolicyProfile(
                DriverSafetyAdmissionContract.PROFILE_ID,
                DriverSafetyAdmissionContract.SCHEMA_VERSION,
                catalogDigest,
                approvals);
    }

    private static OwnerApproval approval(OwnerRole role, String digest) {
        return new OwnerApproval(
                role,
                DriverSafetyAdmissionContract.PROFILE_ID,
                DriverSafetyAdmissionContract.SCHEMA_VERSION,
                DriverSafetyAdmissionContract.catalogDigest(),
                digest);
    }

    private static CapabilityEvidence capability(
            VehicleCapability.CapabilityId capabilityId,
            boolean available,
            boolean authorized,
            boolean readback) {
        return new CapabilityEvidence(
                capabilityId.getCanonicalId(),
                available,
                authorized,
                readback,
                digest('d'));
    }

    private static SafetyVehicleStateSnapshot runtimeStub(MotionState motionState) {
        return new SafetyVehicleStateSnapshot(
                "runtime-owned-state-stub",
                1,
                CAPTURED_AT_MS,
                SafetyState.NORMAL,
                motionState,
                true,
                SourceAssurance.RUNTIME_OWNED_STUB,
                false);
    }

    private static SafetyVehicleStateSnapshot trusted(
            SafetyState safetyState,
            MotionState motionState,
            boolean driverAvailable,
            long capturedAtMs) {
        return new SafetyVehicleStateSnapshot(
                "platform-trusted-test-fixture",
                7,
                capturedAtMs,
                safetyState,
                motionState,
                driverAvailable,
                SourceAssurance.PLATFORM_TRUSTED_ADAPTER,
                true);
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }
}
