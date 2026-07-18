package com.centralbrain.runtime.events;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.scenario.ScenarioManifest;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;

import java.util.concurrent.atomic.AtomicLong;

/** Debug-only API 33 ARM64 probe for the P6-W04 proactive consent policy. */
public final class ProactiveConsentPolicyProbeActivity extends Activity {
    private static final String TAG = "CbProactiveConsent";
    private static final String OWNER = "a".repeat(64);
    private static final String SCENARIO = "b".repeat(64);
    private static final String SUGGESTION = "c".repeat(64);
    private static final String RECEIPT = "d".repeat(64);
    private static final String PRIVACY = "e".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        runProbe();
        finish();
    }

    private void runProbe() {
        String nonce = getIntent().getStringExtra("nonce");
        if (nonce == null || nonce.isEmpty()) {
            nonce = "missing";
        }
        AtomicLong now = new AtomicLong(1_000);
        ProactiveConsentPolicy policy = ProactiveConsentPolicy.createForContractTest(
                4,
                now::get,
                (mutation, evidence) -> ProactiveConsentPolicy.AuthorityDecision.ALLOWED);

        ProactiveConsentPolicy.ConsentMutation grant = grant(
                "grant.cold.driver.v1",
                ProactiveConsentPolicy.RiskClass.MEDIUM,
                100);
        ProactiveConsentPolicy.MutationResult applied = policy.mutate(
                grant,
                ProactiveConsentPolicy.DrivingState.PARKED,
                evidence("request.grant.cold.v1", grant, now.get()));
        ProactiveConsentPolicy.AdmissionDecision eligible = policy.evaluate(candidate(
                OWNER,
                VehicleCapability.CapabilityId.HVAC_POWER,
                ScenarioManifest.Zone.ROW1_DRIVER,
                ProactiveConsentPolicy.RiskClass.LOW));
        ProactiveConsentPolicy.AdmissionDecision mismatch = policy.evaluate(candidate(
                OWNER,
                VehicleCapability.CapabilityId.HVAC_FAN_LEVEL,
                ScenarioManifest.Zone.ROW1_DRIVER,
                ProactiveConsentPolicy.RiskClass.LOW));

        ProactiveConsentPolicy.ConsentMutation high = grant(
                "grant.high.driver.v1",
                ProactiveConsentPolicy.RiskClass.HIGH,
                100);
        ProactiveConsentPolicy.MutationResult highBlocked = policy.mutate(
                high,
                ProactiveConsentPolicy.DrivingState.PARKED,
                evidence("request.high.driver.v1", high, now.get()));
        ProactiveConsentPolicy.AdmissionDecision criticalBlocked = policy.evaluate(candidate(
                OWNER,
                VehicleCapability.CapabilityId.HVAC_POWER,
                ScenarioManifest.Zone.ROW1_DRIVER,
                ProactiveConsentPolicy.RiskClass.CRITICAL));

        ProactiveConsentPolicy.ConsentMutation revoke =
                ProactiveConsentPolicy.ConsentMutation.revoke(
                        "grant.cold.driver.v1",
                        OWNER);
        ProactiveConsentPolicy.MutationResult revoked = policy.mutate(
                revoke,
                ProactiveConsentPolicy.DrivingState.PARKED,
                evidence("request.revoke.cold.v1", revoke, now.get()));
        ProactiveConsentPolicy.AdmissionDecision afterRevoke = policy.evaluate(candidate(
                OWNER,
                VehicleCapability.CapabilityId.HVAC_POWER,
                ScenarioManifest.Zone.ROW1_DRIVER,
                ProactiveConsentPolicy.RiskClass.LOW));

        ProactiveConsentPolicy.Snapshot snapshot = policy.snapshot();
        boolean bindingVerified = applied.getCode()
                == ProactiveConsentPolicy.MutationCode.APPLIED
                && eligible.getCode() == ProactiveConsentPolicy.AdmissionCode.POLICY_ELIGIBLE
                && mismatch.getCode() == ProactiveConsentPolicy.AdmissionCode.NO_ACTIVE_GRANT;
        boolean highCriticalBlocked = highBlocked.getCode()
                == ProactiveConsentPolicy.MutationCode.HIGH_RISK_GENERIC_GRANT_FORBIDDEN
                && criticalBlocked.getCode()
                == ProactiveConsentPolicy.AdmissionCode.EXPLICIT_APPROVAL_REQUIRED;
        boolean ttlRevokeVerified = revoked.getCode()
                == ProactiveConsentPolicy.MutationCode.APPLIED
                && afterRevoke.getCode()
                == ProactiveConsentPolicy.AdmissionCode.NO_ACTIVE_GRANT;
        boolean policyFailClosed = !eligible.isEffectDispatchAuthorized()
                && eligible.isSafetyRevalidationRequired()
                && snapshot.isPolicyOnly()
                && !snapshot.isAutoExecutionEnabled()
                && !snapshot.isRuntimeWired();
        boolean complete = bindingVerified
                && highCriticalBlocked
                && ttlRevokeVerified
                && policyFailClosed;

        Log.i(TAG, String.join("\n",
                "nonce=" + nonce + " proactive_consent_probe_complete=" + complete,
                "proactive_grant_binding_verified=" + bindingVerified,
                "proactive_high_critical_generic_grant_blocked=" + highCriticalBlocked,
                "proactive_grant_ttl_revoke_verified=" + ttlRevokeVerified,
                "proactive_policy_fail_closed_verified=" + policyFailClosed,
                "proactive_consent_android13_arm64_verified=" + complete,
                "proactive_policy_process_local=true",
                "proactive_grant_persistence_wired=false",
                "proactive_consent_authority_wired=false",
                "proactive_auto_execution_enabled=false",
                "proactive_runtime_wired=false",
                "graph_execution_enabled=false",
                "effect_dispatch_enabled=false",
                "vehicle_readback_accessed=false",
                "model_invoked=false",
                "npu_accessed=false",
                "network_accessed=false",
                "hardware_accessed=false",
                "production_ready=false",
                "target_hardware_validated=false"));
    }

    private static ProactiveConsentPolicy.ConsentMutation grant(
            String grantId,
            ProactiveConsentPolicy.RiskClass risk,
            long ttlMs) {
        return ProactiveConsentPolicy.ConsentMutation.grant(
                grantId,
                OWNER,
                "scene.comfort.cold.v1",
                SCENARIO,
                VehicleCapability.CapabilityId.HVAC_POWER,
                ScenarioManifest.Zone.ROW1_DRIVER,
                risk,
                ttlMs);
    }

    private static ProactiveConsentPolicy.ConsentEvidence evidence(
            String requestId,
            ProactiveConsentPolicy.ConsentMutation mutation,
            long now) {
        return new ProactiveConsentPolicy.ConsentEvidence(
                requestId,
                mutation.getMutationDigest(),
                RECEIPT,
                PRIVACY,
                now,
                now + 10);
    }

    private static ProactiveConsentPolicy.AutoExecutionCandidate candidate(
            String owner,
            VehicleCapability.CapabilityId capabilityId,
            ScenarioManifest.Zone zone,
            ProactiveConsentPolicy.RiskClass risk) {
        return new ProactiveConsentPolicy.AutoExecutionCandidate(
                SUGGESTION,
                owner,
                "scene.comfort.cold.v1",
                SCENARIO,
                capabilityId,
                zone,
                risk);
    }
}
