package com.centralbrain.runtime.governance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;

public final class DriverSafetyAuditProjectionTest {
    @Test
    public void currentRepositoryProjectionHasExactRedactedCounts() {
        DriverSafetyAuditProjection.Snapshot snapshot =
                DriverSafetyAuditProjection.evaluateCurrentRepository();

        assertTrue(snapshot.isProjectionVerified());
        assertEquals(12, snapshot.getActionRuleCount());
        assertEquals(6, snapshot.getMovingBlockedActionCount());
        assertEquals(4, snapshot.getVehicleEffectActionCount());
        assertEquals(4, snapshot.getApprovalRequiredActionCount());
    }

    @Test
    public void projectionUsesExactUniqueAllowlistedKeys() {
        List<String> keys = DriverSafetyAuditProjection.allowedAuditKeys();

        assertEquals(DriverSafetyAuditProjection.AUDIT_KEY_COUNT, keys.size());
        assertEquals(keys.size(), new HashSet<>(keys).size());
        assertEquals("driver_safety_probe_complete", keys.get(0));
        assertEquals("target_hardware_validated", keys.get(keys.size() - 1));
    }

    @Test
    public void projectionContainsOnlyCountsBooleansAndNoSensitiveFields() {
        String audit = DriverSafetyAuditProjection.evaluateCurrentRepository().auditMetadata();

        assertTrue(audit.matches("[a-z0-9_= ]+"));
        for (String forbidden : List.of(
                "speed", "gear", "parking_brake", "seat_angle", "seat_belt",
                "seat_occupancy", "source_id", "owner_reference=", "approval_digest",
                "activation_digest", "serial", "fingerprint", "payload", "raw_log=")) {
            assertFalse(audit.contains(forbidden));
        }
    }

    @Test
    public void projectionKeepsAuthorityHardwareAndReadinessClaimsFalse() {
        String audit = DriverSafetyAuditProjection.evaluateCurrentRepository().auditMetadata();

        for (String marker : List.of(
                "driver_safety_current_owner_policy_approved=false",
                "driver_safety_vehicle_state_provider_wired=false",
                "driver_safety_effect_runtime_wired=false",
                "driver_safety_effect_dispatch_authorized=false",
                "driver_safety_hardware_operation_executed=false",
                "driver_safety_vehicle_scalar_read=false",
                "driver_safety_android13_arm64_verified=false",
                "hardware_accessed=false",
                "production_ready=false",
                "target_hardware_validated=false")) {
            assertTrue(audit.contains(marker));
        }
    }

    @Test
    public void repositoryClaimsProbeAvailableButNotExecuted() {
        assertTrue(DriverSafetyAuditProjection.isDebugProbeAvailable());
        assertFalse(DriverSafetyAuditProjection.isDebugProbeExecuted());
        assertFalse(DriverSafetyAuditProjection.isAndroid13Arm64Verified());
        assertFalse(DriverSafetyAuditProjection.isHardwareAccessed());
        assertFalse(DriverSafetyAuditProjection.isProductionReady());
        assertFalse(DriverSafetyAuditProjection.isTargetHardwareValidated());
    }

    @Test
    public void projectionAcceptsNoRuntimeOrVehicleInput() {
        assertEquals(0,
                DriverSafetyAdmissionContract.currentDraftPolicy().getApprovals().size());
        assertEquals(
                DriverSafetyAdmissionContract.ACTION_RULE_COUNT,
                DriverSafetyAdmissionContract.actionRules().size());
        assertTrue(DriverSafetyAuditProjection.evaluateCurrentRepository().isProjectionVerified());
    }
}
