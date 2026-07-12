package com.centralbrain.runtime.skills;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class BuiltInSkillRuntimeProbeActivity extends Activity {
    private static final String TAG = "CbBuiltInSkill";
    private static final String OWNER_A = repeat("a", 64);
    private static final String OWNER_B = repeat("b", 64);
    private static final String DIGEST_A = repeat("c", 64);
    private static final String DIGEST_B = repeat("d", 64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            AtomicInteger ids = new AtomicInteger();
            BoundedBuiltInSkillRuntime runtime =
                    BoundedBuiltInSkillRuntime.createForContractTest(
                            new BoundedBuiltInSkillRuntime.Limits(2, 1, 1),
                            () -> "probe-" + ids.incrementAndGet());
            List<BoundedBuiltInSkillRuntime.SkillManifest> manifests =
                    runtime.listManifests();
            boolean catalogVerified = manifests.size() == 3
                    && BoundedBuiltInSkillRuntime.SKILL_VEHICLE_STATE_QUERY.equals(
                            manifests.get(0).getSkillId())
                    && BoundedBuiltInSkillRuntime.SKILL_CABIN_PRECONDITION.equals(
                            manifests.get(1).getSkillId())
                    && BoundedBuiltInSkillRuntime.SKILL_CABIN_SCENE_NAP.equals(
                            manifests.get(2).getSkillId());
            boolean signerAllowlistVerified = true;
            boolean manifestSchemaVerified = true;
            for (BoundedBuiltInSkillRuntime.SkillManifest manifest : manifests) {
                signerAllowlistVerified &= manifest.isCompiledIn()
                        && manifest.isSignerAllowlistMatched()
                        && manifest.isArtifactDigestBound()
                        && !manifest.isCryptographicArtifactVerificationPerformed()
                        && !manifest.isDynamicLoadingAllowed();
                manifestSchemaVerified &= "0.1.0".equals(manifest.getVersion())
                        && manifest.getInputSchemaId().endsWith(".v1")
                        && manifest.getOutputSchemaId().endsWith(".v1")
                        && !manifest.getRequiredCapabilities().isEmpty()
                        && !manifest.getAllowedSafetyStates().isEmpty();
            }

            BoundedBuiltInSkillRuntime.TrustedInvocation ownerARequest = query(
                    OWNER_A,
                    "owner-a",
                    DIGEST_A);
            BoundedBuiltInSkillRuntime.AdmissionResult ownerA = runtime.admit(ownerARequest);
            boolean idempotencyVerified = ownerA.getOutcome()
                    == BoundedBuiltInSkillRuntime.AdmissionOutcome.ADMITTED
                    && runtime.admit(ownerARequest).getOutcome()
                    == BoundedBuiltInSkillRuntime.AdmissionOutcome.REPLAYED
                    && runtime.admit(query(OWNER_A, "owner-a", DIGEST_B)).getOutcome()
                    == BoundedBuiltInSkillRuntime.AdmissionOutcome.CONFLICT
                    && !ownerA.getInvocation().isDispatchAllowed();

            boolean capabilityPolicyVerified = runtime.admit(invocation(
                    OWNER_B,
                    "capability-denied",
                    BoundedBuiltInSkillRuntime.SKILL_CABIN_PRECONDITION,
                    "skill.cabin.precondition.input.v1",
                    DIGEST_A,
                    EnumSet.of(BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ),
                    BoundedBuiltInSkillRuntime.SafetyState.NORMAL)).getOutcome()
                    == BoundedBuiltInSkillRuntime.AdmissionOutcome.CAPABILITY_DENIED;
            boolean safetyStateVerified = runtime.admit(invocation(
                    OWNER_B,
                    "safety-denied",
                    BoundedBuiltInSkillRuntime.SKILL_CABIN_PRECONDITION,
                    "skill.cabin.precondition.input.v1",
                    DIGEST_A,
                    EnumSet.of(
                            BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ,
                            BoundedBuiltInSkillRuntime.Capability.VEHICLE_CONTROL),
                    BoundedBuiltInSkillRuntime.SafetyState.DEGRADED)).getOutcome()
                    == BoundedBuiltInSkillRuntime.AdmissionOutcome.SAFETY_STATE_DENIED;

            BoundedBuiltInSkillRuntime.AdmissionResult ownerB = runtime.admit(query(
                    OWNER_B,
                    "owner-b",
                    DIGEST_B));
            String ownerAId = ownerA.getInvocation().getInvocationId();
            boolean ownerIsolationVerified = ownerB.getOutcome()
                    == BoundedBuiltInSkillRuntime.AdmissionOutcome.ADMITTED
                    && runtime.findOwned(ownerAId, OWNER_B) == null
                    && runtime.cancelOwned(ownerAId, OWNER_B)
                    == BoundedBuiltInSkillRuntime.CancelOutcome.NOT_FOUND;
            boolean cancelIdempotencyVerified = runtime.cancelOwned(ownerAId, OWNER_A)
                    == BoundedBuiltInSkillRuntime.CancelOutcome.APPLIED
                    && runtime.cancelOwned(ownerAId, OWNER_A)
                    == BoundedBuiltInSkillRuntime.CancelOutcome.REPLAYED;
            BoundedBuiltInSkillRuntime.AdmissionResult replacement = runtime.admit(query(
                    OWNER_A,
                    "replacement",
                    DIGEST_B));
            runtime.cancelOwned(ownerB.getInvocation().getInvocationId(), OWNER_B);
            BoundedBuiltInSkillRuntime.Snapshot snapshot = runtime.snapshot();
            boolean recordBoundsVerified = replacement.getOutcome()
                    == BoundedBuiltInSkillRuntime.AdmissionOutcome.ADMITTED
                    && snapshot.getManifestCount() == 3
                    && snapshot.getActiveInvocationCount() == 1
                    && snapshot.getCancelledInvocationCount() == 1
                    && snapshot.getCancelledEvictionCount() == 1
                    && runtime.findOwned(ownerAId, OWNER_A) == null;
            boolean contractVerified = catalogVerified
                    && signerAllowlistVerified
                    && manifestSchemaVerified
                    && idempotencyVerified
                    && capabilityPolicyVerified
                    && safetyStateVerified
                    && ownerIsolationVerified
                    && cancelIdempotencyVerified
                    && recordBoundsVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " skill_runtime_probe_complete=" + contractVerified
                    + " skill_runtime_contract_verified=" + contractVerified
                    + " skill_catalog_verified=" + catalogVerified
                    + " skill_signer_allowlist_verified=" + signerAllowlistVerified
                    + " skill_manifest_schema_verified=" + manifestSchemaVerified
                    + " skill_invocation_idempotency_verified=" + idempotencyVerified
                    + " skill_capability_policy_verified=" + capabilityPolicyVerified
                    + " skill_safety_state_verified=" + safetyStateVerified
                    + " skill_owner_isolation_verified=" + ownerIsolationVerified
                    + " skill_cancel_idempotency_verified=" + cancelIdempotencyVerified
                    + " skill_record_bounds_verified=" + recordBoundsVerified
                    + " skill_process_only=true"
                    + " skill_manifest_signer_evidence_compile_time_only=true"
                    + " skill_dynamic_loading_enabled=false"
                    + " skill_cryptographic_artifact_verification_performed=false"
                    + " skill_production_service_wired=false"
                    + " raw_skill_input_stored=false"
                    + " skill_network_access_enabled=false"
                    + " service_dispatch_triggered=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " skill_runtime_probe_complete=false"
                    + " skill_dynamic_loading_enabled=false"
                    + " service_dispatch_triggered=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static BoundedBuiltInSkillRuntime.TrustedInvocation query(
            String owner,
            String clientId,
            String digest) {
        return invocation(
                owner,
                clientId,
                BoundedBuiltInSkillRuntime.SKILL_VEHICLE_STATE_QUERY,
                "skill.vehicle.state.query.input.v1",
                digest,
                EnumSet.of(BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ),
                BoundedBuiltInSkillRuntime.SafetyState.NORMAL);
    }

    private static BoundedBuiltInSkillRuntime.TrustedInvocation invocation(
            String owner,
            String clientId,
            String skillId,
            String inputSchema,
            String digest,
            Set<BoundedBuiltInSkillRuntime.Capability> capabilities,
            BoundedBuiltInSkillRuntime.SafetyState safetyState) {
        return BoundedBuiltInSkillRuntime.TrustedInvocation.fromRuntimePolicy(
                owner,
                clientId,
                skillId,
                "0.1.0",
                inputSchema,
                digest,
                capabilities,
                safetyState);
    }

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}
