package com.centralbrain.runtime.governance;

import com.centralbrain.runtime.skills.BoundedBuiltInSkillRuntime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Immutable production visibility for blocked Skill and middleware activation. */
public final class SkillGovernanceReadinessSnapshot {
    public enum Blocker {
        ARTIFACT_CRYPTO_VERIFIER_NOT_CONFIGURED,
        SKILL_LIFECYCLE_STORE_NOT_IMPLEMENTED,
        SKILL_REVOCATION_NOT_CONFIGURED,
        SKILL_ROLLBACK_NOT_CONFIGURED,
        SKILL_SANDBOX_NOT_CONFIGURED,
        GOVERNANCE_AUTHORITIES_NOT_WIRED,
        ROUTE_OWNER_REGISTRY_NOT_WIRED,
        MIDDLEWARE_CHAIN_NOT_WIRED,
        AUDIT_PERSISTENCE_NOT_WIRED,
        SKILL_DISPATCHER_NOT_WIRED
    }

    private static final int COMPILED_BUILT_IN_SKILL_COUNT = 3;
    private static final SkillGovernanceReadinessSnapshot CURRENT = createCurrent();

    private final List<Blocker> blockers;

    private SkillGovernanceReadinessSnapshot(List<Blocker> blockers) {
        this.blockers = Collections.unmodifiableList(new ArrayList<>(blockers));
    }

    public static SkillGovernanceReadinessSnapshot current() {
        return CURRENT;
    }

    private static SkillGovernanceReadinessSnapshot createCurrent() {
        if (!"vehicle.state.query".equals(
                        BoundedBuiltInSkillRuntime.SKILL_VEHICLE_STATE_QUERY)
                || !"cabin.precondition".equals(
                        BoundedBuiltInSkillRuntime.SKILL_CABIN_PRECONDITION)
                || !"cabin.scene.nap".equals(
                        BoundedBuiltInSkillRuntime.SKILL_CABIN_SCENE_NAP)) {
            throw new IllegalStateException("built-in Skill ID baseline is inconsistent");
        }
        if (!Arrays.equals(
                FixedGovernanceMiddlewareChain.StageId.values(),
                new FixedGovernanceMiddlewareChain.StageId[] {
                        FixedGovernanceMiddlewareChain.StageId.IDENTITY,
                        FixedGovernanceMiddlewareChain.StageId.SCHEMA,
                        FixedGovernanceMiddlewareChain.StageId.PRIVACY,
                        FixedGovernanceMiddlewareChain.StageId.POLICY,
                        FixedGovernanceMiddlewareChain.StageId.QOS,
                        FixedGovernanceMiddlewareChain.StageId.TRACE,
                        FixedGovernanceMiddlewareChain.StageId.DISPATCH_GATE,
                        FixedGovernanceMiddlewareChain.StageId.OUTPUT_GUARD,
                        FixedGovernanceMiddlewareChain.StageId.AUDIT
                })) {
            throw new IllegalStateException("Governance middleware order is inconsistent");
        }
        return new SkillGovernanceReadinessSnapshot(Arrays.asList(
                Blocker.ARTIFACT_CRYPTO_VERIFIER_NOT_CONFIGURED,
                Blocker.SKILL_LIFECYCLE_STORE_NOT_IMPLEMENTED,
                Blocker.SKILL_REVOCATION_NOT_CONFIGURED,
                Blocker.SKILL_ROLLBACK_NOT_CONFIGURED,
                Blocker.SKILL_SANDBOX_NOT_CONFIGURED,
                Blocker.GOVERNANCE_AUTHORITIES_NOT_WIRED,
                Blocker.ROUTE_OWNER_REGISTRY_NOT_WIRED,
                Blocker.MIDDLEWARE_CHAIN_NOT_WIRED,
                Blocker.AUDIT_PERSISTENCE_NOT_WIRED,
                Blocker.SKILL_DISPATCHER_NOT_WIRED));
    }

    public boolean isActivationAllowed() {
        return false;
    }

    public boolean isBoundedBuiltInSkillRuntimeImplementationAvailable() {
        return true;
    }

    public int getCompiledBuiltInSkillCount() {
        return COMPILED_BUILT_IN_SKILL_COUNT;
    }

    public boolean isCompileTimeSignerEvidenceAvailable() {
        return true;
    }

    public boolean isCryptographicArtifactVerificationPerformed() {
        return false;
    }

    public boolean isFixedGovernanceMiddlewareImplementationAvailable() {
        return true;
    }

    public int getMiddlewareStageCount() {
        return FixedGovernanceMiddlewareChain.StageId.values().length;
    }

    public boolean isMiddlewareOrderFixed() {
        return true;
    }

    public boolean isSkillLifecycleStoreImplemented() {
        return false;
    }

    public boolean isSkillRevocationConfigured() {
        return false;
    }

    public boolean isSkillRollbackConfigured() {
        return false;
    }

    public boolean isSkillSandboxConfigured() {
        return false;
    }

    public boolean areGovernanceProductionAuthoritiesWired() {
        return false;
    }

    public boolean isRouteOwnerRegistryWired() {
        return false;
    }

    public boolean isMiddlewareProductionWired() {
        return false;
    }

    public boolean isAuditPersistenceWired() {
        return false;
    }

    public boolean isSkillDispatcherProductionWired() {
        return false;
    }

    public boolean isDynamicSkillLoadingEnabled() {
        return false;
    }

    public boolean isRawSkillInputStored() {
        return false;
    }

    public boolean isRawSkillOutputStored() {
        return false;
    }

    public boolean isNetworkAccessEnabled() {
        return false;
    }

    public boolean isHardwareAccessed() {
        return false;
    }

    public List<Blocker> getBlockers() {
        return blockers;
    }

    public String getBlockersCsv() {
        return blockers.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public String diagnosticDetail() {
        return "skill_governance_activation_allowed=false"
                + ";bounded_built_in_skill_runtime_implementation_available=true"
                + ";compiled_built_in_skill_count=" + getCompiledBuiltInSkillCount()
                + ";compile_time_skill_signer_evidence_available=true"
                + ";skill_artifact_cryptographic_verification_performed=false"
                + ";fixed_governance_middleware_implementation_available=true"
                + ";governance_middleware_stage_count=" + getMiddlewareStageCount()
                + ";governance_middleware_order_fixed=true"
                + ";skill_lifecycle_store_implemented=false"
                + ";skill_revocation_configured=false"
                + ";skill_rollback_configured=false"
                + ";skill_sandbox_configured=false"
                + ";governance_production_authorities_wired=false"
                + ";skill_route_owner_registry_wired=false"
                + ";skill_governance_middleware_production_wired=false"
                + ";skill_governance_audit_persistence_wired=false"
                + ";skill_dispatcher_production_wired=false"
                + ";skill_dynamic_loading_enabled=false"
                + ";raw_skill_input_stored=false"
                + ";raw_skill_output_stored=false"
                + ";skill_network_access_enabled=false"
                + ";blockers=" + getBlockersCsv()
                + ";service_dispatch_triggered=false"
                + ";hardware_accessed=false";
    }
}
