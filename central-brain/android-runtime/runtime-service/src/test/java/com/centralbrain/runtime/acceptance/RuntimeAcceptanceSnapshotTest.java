package com.centralbrain.runtime.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

public final class RuntimeAcceptanceSnapshotTest {
    @Test
    public void applicationIntegrationIsReadyButProductionRemainsBlocked() {
        RuntimeAcceptanceSnapshot current = RuntimeAcceptanceSnapshot.current();
        assertSame(current, RuntimeAcceptanceSnapshot.current());
        assertTrue(current.isCoreSoftwareBaselineReady());
        assertTrue(current.isR7ApplicationIntegrationComplete());
        assertTrue(current.isClient2BinderMigrationComplete());
        assertTrue(current.isApi33EndToEndAcceptanceComplete());
        assertFalse(current.isProductionActivationAllowed());
        assertFalse(current.isTargetHardwareValidated());
        assertFalse(current.isTargetSystemIntegrationOwnerResolved());
        assertTrue(current.isTypedBinderIntegrated());
        assertTrue(current.isTrustedGovernanceIntegrated());
        assertTrue(current.isDurableWorkflowFoundationReady());
        assertEquals(3, current.getRoomSchemaVersion());
        assertEquals(3, current.getStandardArtifactCount());
        assertEquals(3, current.getSignatureProtectedServiceCount());
    }

    @Test
    public void subsystemActivationAndDispatchRemainFailClosed() {
        RuntimeAcceptanceSnapshot current = RuntimeAcceptanceSnapshot.current();
        assertFalse(current.isEffectDeliveryActivationAllowed());
        assertFalse(current.isProductionInferenceAllowed());
        assertFalse(current.isEventRuntimeActivationAllowed());
        assertFalse(current.isMemoryRuntimeActivationAllowed());
        assertFalse(current.isSkillGovernanceActivationAllowed());
        assertFalse(current.isServiceDispatchTriggered());
        assertFalse(current.isHardwareAccessed());
    }

    @Test
    public void blockersRemainOrderedAndImmutable() {
        RuntimeAcceptanceSnapshot current = RuntimeAcceptanceSnapshot.current();
        assertEquals(Arrays.asList(
                        RuntimeAcceptanceSnapshot.Blocker
                                .TARGET_SYSTEM_INTEGRATION_OWNER_UNRESOLVED,
                        RuntimeAcceptanceSnapshot.Blocker
                                .PRODUCTION_EFFECT_DELIVERY_BLOCKED,
                        RuntimeAcceptanceSnapshot.Blocker
                                .PRODUCTION_MODEL_RUNTIME_BLOCKED,
                        RuntimeAcceptanceSnapshot.Blocker
                                .PRODUCTION_EVENT_RUNTIME_BLOCKED,
                        RuntimeAcceptanceSnapshot.Blocker
                                .PRODUCTION_MEMORY_RUNTIME_BLOCKED,
                        RuntimeAcceptanceSnapshot.Blocker
                                .PRODUCTION_SKILL_GOVERNANCE_BLOCKED,
                        RuntimeAcceptanceSnapshot.Blocker.TARGET_HARDWARE_NOT_VALIDATED),
                current.getBlockers());
        try {
            current.getBlockers().clear();
            fail("acceptance blockers must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void diagnosticDetailSeparatesSoftwareFromProductionAcceptance() {
        String detail = RuntimeAcceptanceSnapshot.current().diagnosticDetail();
        assertTrue(detail.contains("core_software_baseline_ready=true"));
        assertTrue(detail.contains("r7_application_integration_complete=true"));
        assertTrue(detail.contains("client2_binder_migration_complete=true"));
        assertTrue(detail.contains("api33_end_to_end_acceptance_complete=true"));
        assertTrue(detail.contains("production_activation_allowed=false"));
        assertTrue(detail.contains("target_hardware_validated=false"));
        assertTrue(detail.contains("typed_binder_integrated=true"));
        assertTrue(detail.contains("trusted_governance_integrated=true"));
        assertTrue(detail.contains("durable_workflow_foundation_ready=true"));
        assertTrue(detail.contains("room_schema_version=3"));
        assertTrue(detail.contains("standard_artifact_count=3"));
        assertTrue(detail.contains("signature_protected_service_count=3"));
        assertFalse(detail.contains("CLIENT2_BINDER_MIGRATION_PENDING"));
        assertFalse(detail.contains("API33_END_TO_END_ACCEPTANCE_PENDING"));
        assertTrue(detail.contains("TARGET_HARDWARE_NOT_VALIDATED"));
        assertTrue(detail.contains("service_dispatch_triggered=false"));
        assertTrue(detail.contains("hardware_accessed=false"));
    }
}
