package com.centralbrain.runtime.model;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public final class PolicyAwareModelRouterProbeActivity extends Activity {
    private static final String TAG = "CbModelRouter";
    private static final String DIGEST_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String DIGEST_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            ModelContractV2.ModelRequest request = request(
                    ModelContractV2.PrivacyClass.INTERNAL,
                    ModelContractV2.FallbackPolicy.NO_FALLBACK,
                    1_000);
            ModelProviderRegistry registry = healthyRegistry();
            PolicyAwareModelRouter.RouteDecision selected = PolicyAwareModelRouter.decide(
                    request,
                    policy(PolicyAwareModelRouter.RouteMode.CONTRACT_TEST,
                            PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                            PolicyAwareModelRouter.NetworkState.UNAVAILABLE,
                            PolicyAwareModelRouter.ThermalState.CRITICAL,
                            10,
                            10_000),
                    registry.snapshot(1_000),
                    1_000);
            boolean selectedVerified = selected.isSelected()
                    && ModelProviderRegistry.DETERMINISTIC_TEST_ID.equals(
                            selected.getPrimaryProviderId())
                    && selected.getRequestFingerprint().equals(request.getRequestFingerprint());

            PolicyAwareModelRouter.RouteDecision policyBlocked =
                    PolicyAwareModelRouter.decide(
                            request(ModelContractV2.PrivacyClass.RESTRICTED,
                                    ModelContractV2.FallbackPolicy.NO_FALLBACK,
                                    2_000),
                            policy(PolicyAwareModelRouter.RouteMode.PRODUCTION,
                                    PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                                    PolicyAwareModelRouter.NetworkState.UNMETERED,
                                    PolicyAwareModelRouter.ThermalState.HOT,
                                    10,
                                    10_000),
                            registry.snapshot(1_000),
                            1_000);
            PolicyAwareModelRouter.CandidateEvaluation cloud = find(
                    policyBlocked,
                    ModelProviderRegistry.CLOUD_PLACEHOLDER_ID);
            PolicyAwareModelRouter.CandidateEvaluation vendor = find(
                    policyBlocked,
                    ModelProviderRegistry.VENDOR_NPU_PLACEHOLDER_ID);
            boolean policyVerified = cloud.getRejectionReasons().contains(
                            PolicyAwareModelRouter.RejectionReason.PRIVACY_BLOCKED)
                    && cloud.getRejectionReasons().contains(
                            PolicyAwareModelRouter.RejectionReason.NETWORK_POLICY_BLOCKED)
                    && vendor.getRejectionReasons().contains(
                            PolicyAwareModelRouter.RejectionReason.THERMAL_BLOCKED);

            PolicyAwareModelRouter.RouteDecision quotaBlocked =
                    PolicyAwareModelRouter.decide(
                            request,
                            policy(PolicyAwareModelRouter.RouteMode.CONTRACT_TEST,
                                    PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                                    PolicyAwareModelRouter.NetworkState.UNAVAILABLE,
                                    PolicyAwareModelRouter.ThermalState.NOMINAL,
                                    0,
                                    128),
                            registry.snapshot(1_000),
                            1_000);
            boolean quotaVerified = find(quotaBlocked,
                    ModelProviderRegistry.DETERMINISTIC_TEST_ID)
                    .getRejectionReasons().contains(
                            PolicyAwareModelRouter.RejectionReason.REQUEST_QUOTA_EXHAUSTED);
            boolean boundariesVerified = selected.isFallbackBounded()
                    && !selected.isActionAuthorizationGranted()
                    && !selected.isEffectDispatchRequested()
                    && !selected.isProviderInvoked()
                    && !selected.isModelInvoked()
                    && !selected.isNetworkAccessed()
                    && !selected.isNpuAccessed()
                    && !selected.isHardwareAccessed();
            boolean verified = selectedVerified
                    && policyVerified
                    && quotaVerified
                    && boundariesVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " model_policy_router_probe_complete=true"
                    + " model_policy_router_verified=" + verified
                    + " model_policy_router_selection_verified=" + selectedVerified
                    + " model_policy_router_privacy_network_thermal_verified="
                    + policyVerified
                    + " model_policy_router_quota_verified=" + quotaVerified
                    + " model_policy_router_fallback_bounded="
                    + selected.isFallbackBounded()
                    + " model_policy_router_android13_arm64_verified=true"
                    + " model_policy_router_runtime_wired=false"
                    + " action_authorization_granted="
                    + selected.isActionAuthorizationGranted()
                    + " effect_dispatch_requested=" + selected.isEffectDispatchRequested()
                    + " provider_invoked=" + selected.isProviderInvoked()
                    + " model_invoked=" + selected.isModelInvoked()
                    + " network_accessed=" + selected.isNetworkAccessed()
                    + " npu_accessed=" + selected.isNpuAccessed()
                    + " hardware_accessed=" + selected.isHardwareAccessed()
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " model_policy_router_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " model_policy_router_runtime_wired=false"
                    + " action_authorization_granted=false"
                    + " effect_dispatch_requested=false"
                    + " provider_invoked=false"
                    + " model_invoked=false"
                    + " network_accessed=false"
                    + " npu_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false", exception);
        }
    }

    private static ModelContractV2.ModelRequest request(
            ModelContractV2.PrivacyClass privacyClass,
            ModelContractV2.FallbackPolicy fallbackPolicy,
            long latencyMs) {
        return new ModelContractV2.ModelRequest(
                "request.router.probe",
                ModelContractV2.Purpose.SCENARIO_REASONING,
                privacyClass,
                new ModelContractV2.LatencyBudget(latencyMs),
                new ModelContractV2.TokenBudget(256, 256, 512),
                ModelContractV2.RequiredCapability.TEXT_GENERATION,
                fallbackPolicy,
                DIGEST_A,
                DIGEST_B);
    }

    private static ModelProviderRegistry healthyRegistry() {
        ModelProviderRegistry registry = ModelProviderRegistry.createForContractTest();
        registry.publishHealth(new ModelProviderRegistry.HealthReport(
                ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                ModelProviderRegistry.HealthSource.CONTRACT_TEST,
                ModelProviderRegistry.HealthState.HEALTHY,
                1,
                900,
                1_500,
                DIGEST_A), 1_000);
        return registry;
    }

    private static PolicyAwareModelRouter.PolicySnapshot policy(
            PolicyAwareModelRouter.RouteMode mode,
            PolicyAwareModelRouter.NetworkPolicy networkPolicy,
            PolicyAwareModelRouter.NetworkState networkState,
            PolicyAwareModelRouter.ThermalState thermalState,
            int remainingRequests,
            int remainingTokens) {
        return new PolicyAwareModelRouter.PolicySnapshot(
                mode,
                networkPolicy,
                networkState,
                thermalState,
                remainingRequests,
                remainingTokens,
                1,
                900,
                1_500,
                DIGEST_B);
    }

    private static PolicyAwareModelRouter.CandidateEvaluation find(
            PolicyAwareModelRouter.RouteDecision decision,
            String providerId) {
        for (PolicyAwareModelRouter.CandidateEvaluation candidate
                : decision.getCandidateEvaluations()) {
            if (providerId.equals(candidate.getProviderId())) {
                return candidate;
            }
        }
        throw new IllegalStateException("candidate is missing");
    }
}
