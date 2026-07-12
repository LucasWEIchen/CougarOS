package com.centralbrain.runtime.governance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

public final class SkillGovernanceReadinessSnapshotTest {
    @Test
    public void currentSnapshotIsImmutableAndFailClosed() {
        SkillGovernanceReadinessSnapshot current =
                SkillGovernanceReadinessSnapshot.current();
        assertSame(current, SkillGovernanceReadinessSnapshot.current());
        assertFalse(current.isActivationAllowed());
        assertTrue(current.isBoundedBuiltInSkillRuntimeImplementationAvailable());
        assertEquals(3, current.getCompiledBuiltInSkillCount());
        assertTrue(current.isCompileTimeSignerEvidenceAvailable());
        assertFalse(current.isCryptographicArtifactVerificationPerformed());
        assertTrue(current.isFixedGovernanceMiddlewareImplementationAvailable());
        assertEquals(9, current.getMiddlewareStageCount());
        assertTrue(current.isMiddlewareOrderFixed());
        assertFalse(current.isSkillLifecycleStoreImplemented());
        assertFalse(current.isSkillRevocationConfigured());
        assertFalse(current.isSkillRollbackConfigured());
        assertFalse(current.isSkillSandboxConfigured());
        assertFalse(current.areGovernanceProductionAuthoritiesWired());
        assertFalse(current.isRouteOwnerRegistryWired());
        assertFalse(current.isMiddlewareProductionWired());
        assertFalse(current.isAuditPersistenceWired());
        assertFalse(current.isSkillDispatcherProductionWired());
        assertFalse(current.isDynamicSkillLoadingEnabled());
        assertFalse(current.isRawSkillInputStored());
        assertFalse(current.isRawSkillOutputStored());
        assertFalse(current.isNetworkAccessEnabled());
        assertFalse(current.isHardwareAccessed());
    }

    @Test
    public void baselineAndBlockersRemainOrdered() {
        SkillGovernanceReadinessSnapshot current =
                SkillGovernanceReadinessSnapshot.current();
        assertEquals(Arrays.asList(
                        SkillGovernanceReadinessSnapshot.Blocker
                                .ARTIFACT_CRYPTO_VERIFIER_NOT_CONFIGURED,
                        SkillGovernanceReadinessSnapshot.Blocker
                                .SKILL_LIFECYCLE_STORE_NOT_IMPLEMENTED,
                        SkillGovernanceReadinessSnapshot.Blocker
                                .SKILL_REVOCATION_NOT_CONFIGURED,
                        SkillGovernanceReadinessSnapshot.Blocker
                                .SKILL_ROLLBACK_NOT_CONFIGURED,
                        SkillGovernanceReadinessSnapshot.Blocker
                                .SKILL_SANDBOX_NOT_CONFIGURED,
                        SkillGovernanceReadinessSnapshot.Blocker
                                .GOVERNANCE_AUTHORITIES_NOT_WIRED,
                        SkillGovernanceReadinessSnapshot.Blocker
                                .ROUTE_OWNER_REGISTRY_NOT_WIRED,
                        SkillGovernanceReadinessSnapshot.Blocker.MIDDLEWARE_CHAIN_NOT_WIRED,
                        SkillGovernanceReadinessSnapshot.Blocker
                                .AUDIT_PERSISTENCE_NOT_WIRED,
                        SkillGovernanceReadinessSnapshot.Blocker
                                .SKILL_DISPATCHER_NOT_WIRED),
                current.getBlockers());
        assertEquals(Arrays.asList(
                        FixedGovernanceMiddlewareChain.StageId.IDENTITY,
                        FixedGovernanceMiddlewareChain.StageId.SCHEMA,
                        FixedGovernanceMiddlewareChain.StageId.PRIVACY,
                        FixedGovernanceMiddlewareChain.StageId.POLICY,
                        FixedGovernanceMiddlewareChain.StageId.QOS,
                        FixedGovernanceMiddlewareChain.StageId.TRACE,
                        FixedGovernanceMiddlewareChain.StageId.DISPATCH_GATE,
                        FixedGovernanceMiddlewareChain.StageId.OUTPUT_GUARD,
                        FixedGovernanceMiddlewareChain.StageId.AUDIT),
                Arrays.asList(FixedGovernanceMiddlewareChain.StageId.values()));
        try {
            current.getBlockers().clear();
            fail("blockers must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void diagnosticDetailExposesEveryPrerequisite() {
        String detail = SkillGovernanceReadinessSnapshot.current().diagnosticDetail();
        assertTrue(detail.contains("skill_governance_activation_allowed=false"));
        assertTrue(detail.contains(
                "bounded_built_in_skill_runtime_implementation_available=true"));
        assertTrue(detail.contains("compiled_built_in_skill_count=3"));
        assertTrue(detail.contains("compile_time_skill_signer_evidence_available=true"));
        assertTrue(detail.contains(
                "skill_artifact_cryptographic_verification_performed=false"));
        assertTrue(detail.contains(
                "fixed_governance_middleware_implementation_available=true"));
        assertTrue(detail.contains("governance_middleware_stage_count=9"));
        assertTrue(detail.contains("governance_middleware_order_fixed=true"));
        assertTrue(detail.contains("skill_lifecycle_store_implemented=false"));
        assertTrue(detail.contains("skill_revocation_configured=false"));
        assertTrue(detail.contains("skill_rollback_configured=false"));
        assertTrue(detail.contains("skill_sandbox_configured=false"));
        assertTrue(detail.contains("governance_production_authorities_wired=false"));
        assertTrue(detail.contains("skill_route_owner_registry_wired=false"));
        assertTrue(detail.contains(
                "skill_governance_middleware_production_wired=false"));
        assertTrue(detail.contains("skill_governance_audit_persistence_wired=false"));
        assertTrue(detail.contains("skill_dispatcher_production_wired=false"));
        assertTrue(detail.contains("skill_dynamic_loading_enabled=false"));
        assertTrue(detail.contains("raw_skill_input_stored=false"));
        assertTrue(detail.contains("raw_skill_output_stored=false"));
        assertTrue(detail.contains("skill_network_access_enabled=false"));
        assertTrue(detail.contains("ARTIFACT_CRYPTO_VERIFIER_NOT_CONFIGURED"));
        assertTrue(detail.contains("MIDDLEWARE_CHAIN_NOT_WIRED"));
        assertTrue(detail.contains("SKILL_DISPATCHER_NOT_WIRED"));
        assertTrue(detail.contains("service_dispatch_triggered=false"));
        assertTrue(detail.contains("hardware_accessed=false"));
    }
}
