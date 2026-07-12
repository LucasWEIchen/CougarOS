package com.centralbrain.runtime.acceptance;

import com.centralbrain.sdk.CentralBrainSdk;
import com.centralbrain.runtime.effects.EffectDeliveryActivationSnapshot;
import com.centralbrain.runtime.events.EventRuntimeReadinessSnapshot;
import com.centralbrain.runtime.governance.SkillGovernanceReadinessSnapshot;
import com.centralbrain.runtime.memory.MemoryRuntimeReadinessSnapshot;
import com.centralbrain.runtime.model.ModelRuntimeReadinessSnapshot;
import com.centralbrain.runtime.persistence.CentralBrainDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Immutable application-layer acceptance rollup for the Android Runtime baseline. */
public final class RuntimeAcceptanceSnapshot {
    public enum Blocker {
        TARGET_SYSTEM_INTEGRATION_OWNER_UNRESOLVED,
        PRODUCTION_EFFECT_DELIVERY_BLOCKED,
        PRODUCTION_MODEL_RUNTIME_BLOCKED,
        PRODUCTION_EVENT_RUNTIME_BLOCKED,
        PRODUCTION_MEMORY_RUNTIME_BLOCKED,
        PRODUCTION_SKILL_GOVERNANCE_BLOCKED,
        TARGET_HARDWARE_NOT_VALIDATED
    }

    private static final RuntimeAcceptanceSnapshot CURRENT = createCurrent();

    private final List<Blocker> blockers;

    private RuntimeAcceptanceSnapshot(List<Blocker> blockers) {
        this.blockers = Collections.unmodifiableList(new ArrayList<>(blockers));
    }

    public static RuntimeAcceptanceSnapshot current() {
        return CURRENT;
    }

    private static RuntimeAcceptanceSnapshot createCurrent() {
        EffectDeliveryActivationSnapshot effects =
                EffectDeliveryActivationSnapshot.current();
        ModelRuntimeReadinessSnapshot model = ModelRuntimeReadinessSnapshot.current();
        EventRuntimeReadinessSnapshot events = EventRuntimeReadinessSnapshot.current();
        MemoryRuntimeReadinessSnapshot memory = MemoryRuntimeReadinessSnapshot.current();
        SkillGovernanceReadinessSnapshot skills =
                SkillGovernanceReadinessSnapshot.current();
        if (!"android_integrated".equals(CentralBrainSdk.MATURITY)
                || !"R4_DURABLE_WORKFLOW".equals(CentralBrainSdk.EVOLUTION_STAGE)
                || CentralBrainDatabase.VERSION != 3
                || effects.isActivationAllowed()
                || model.isProductionInferenceAllowed()
                || events.isActivationAllowed()
                || memory.isActivationAllowed()
                || skills.isActivationAllowed()
                || !model.isModelProviderContractAvailable()
                || !events.isBoundedEventRuntimeImplementationAvailable()
                || !events.isEventRepositoryImplementationAvailable()
                || !memory.isBoundedMemoryLifecycleImplementationAvailable()
                || !skills.isBoundedBuiltInSkillRuntimeImplementationAvailable()
                || !skills.isFixedGovernanceMiddlewareImplementationAvailable()) {
            throw new IllegalStateException(
                    "Android Runtime acceptance baseline is inconsistent");
        }
        return new RuntimeAcceptanceSnapshot(Arrays.asList(
                Blocker.TARGET_SYSTEM_INTEGRATION_OWNER_UNRESOLVED,
                Blocker.PRODUCTION_EFFECT_DELIVERY_BLOCKED,
                Blocker.PRODUCTION_MODEL_RUNTIME_BLOCKED,
                Blocker.PRODUCTION_EVENT_RUNTIME_BLOCKED,
                Blocker.PRODUCTION_MEMORY_RUNTIME_BLOCKED,
                Blocker.PRODUCTION_SKILL_GOVERNANCE_BLOCKED,
                Blocker.TARGET_HARDWARE_NOT_VALIDATED));
    }

    public boolean isCoreSoftwareBaselineReady() {
        return true;
    }

    public boolean isR7ApplicationIntegrationComplete() {
        return true;
    }

    public boolean isClient2BinderMigrationComplete() {
        return true;
    }

    public boolean isApi33EndToEndAcceptanceComplete() {
        return true;
    }

    public boolean isProductionActivationAllowed() {
        return false;
    }

    public boolean isTargetHardwareValidated() {
        return false;
    }

    public boolean isTargetSystemIntegrationOwnerResolved() {
        return false;
    }

    public boolean isTypedBinderIntegrated() {
        return true;
    }

    public boolean isTrustedGovernanceIntegrated() {
        return true;
    }

    public boolean isDurableWorkflowFoundationReady() {
        return true;
    }

    public int getRoomSchemaVersion() {
        return CentralBrainDatabase.VERSION;
    }

    public int getStandardArtifactCount() {
        return 3;
    }

    public int getSignatureProtectedServiceCount() {
        return 3;
    }

    public boolean isEffectDeliveryActivationAllowed() {
        return false;
    }

    public boolean isProductionInferenceAllowed() {
        return false;
    }

    public boolean isEventRuntimeActivationAllowed() {
        return false;
    }

    public boolean isMemoryRuntimeActivationAllowed() {
        return false;
    }

    public boolean isSkillGovernanceActivationAllowed() {
        return false;
    }

    public boolean isServiceDispatchTriggered() {
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
        return "core_software_baseline_ready=true"
                + ";r7_application_integration_complete=true"
                + ";client2_binder_migration_complete=true"
                + ";api33_end_to_end_acceptance_complete=true"
                + ";production_activation_allowed=false"
                + ";target_hardware_validated=false"
                + ";target_system_integration_owner_resolved=false"
                + ";typed_binder_integrated=true"
                + ";trusted_governance_integrated=true"
                + ";durable_workflow_foundation_ready=true"
                + ";room_schema_version=" + getRoomSchemaVersion()
                + ";standard_artifact_count=" + getStandardArtifactCount()
                + ";signature_protected_service_count="
                + getSignatureProtectedServiceCount()
                + ";effect_delivery_activation_allowed=false"
                + ";production_inference_allowed=false"
                + ";event_runtime_activation_allowed=false"
                + ";memory_runtime_activation_allowed=false"
                + ";skill_governance_activation_allowed=false"
                + ";blockers=" + getBlockersCsv()
                + ";service_dispatch_triggered=false"
                + ";hardware_accessed=false";
    }
}
