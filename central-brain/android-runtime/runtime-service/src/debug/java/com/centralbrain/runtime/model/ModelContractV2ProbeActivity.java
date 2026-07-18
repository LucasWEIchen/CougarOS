package com.centralbrain.runtime.model;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public final class ModelContractV2ProbeActivity extends Activity {
    private static final String TAG = "CbModelV2Probe";
    private static final String TRACE =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String INPUT =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String OUTPUT =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            ModelContractV2.ModelRequest request = new ModelContractV2.ModelRequest(
                    "probe-request",
                    ModelContractV2.Purpose.SCENARIO_REASONING,
                    ModelContractV2.PrivacyClass.INTERNAL,
                    new ModelContractV2.LatencyBudget(1_500),
                    new ModelContractV2.TokenBudget(2_048, 512, 2_560),
                    ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE,
                    ModelContractV2.FallbackPolicy.POLICY_CONTROLLED,
                    TRACE,
                    INPUT);
            ModelContractV2.ModelResult result = ModelContractV2.ModelResult.completed(
                    request,
                    "deterministic.stub",
                    OUTPUT,
                    1_900,
                    500);
            boolean privacyFallbackFailClosed = rejectsUnsafeFallback();
            boolean requestFieldsVerified = request.getSchemaVersion() == 2
                    && request.getPurpose() == ModelContractV2.Purpose.SCENARIO_REASONING
                    && request.getPrivacyClass() == ModelContractV2.PrivacyClass.INTERNAL
                    && request.getLatencyBudget().getMaximumEndToEndMs() == 1_500
                    && request.getTokenBudget().getMaximumTotalTokens() == 2_560
                    && request.getRequiredCapability()
                            == ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE
                    && request.getFallbackPolicy()
                            == ModelContractV2.FallbackPolicy.POLICY_CONTROLLED
                    && TRACE.equals(request.getTraceId());
            boolean resultBindingVerified = request.getRequestId().equals(result.getRequestId())
                    && request.getRequestFingerprint().equals(result.getRequestFingerprint())
                    && request.getTraceId().equals(result.getTraceId())
                    && result.getState() == ModelContractV2.ResultState.COMPLETED
                    && !result.isActionAuthorizationGranted()
                    && !result.isEffectDispatchRequested();
            ModelContractV2.ContractSnapshot snapshot = ModelContractV2.snapshot();
            boolean contractVerified = snapshot.isContractV2Defined()
                    && requestFieldsVerified
                    && resultBindingVerified
                    && privacyFallbackFailClosed;

            Log.i(TAG, "nonce=" + nonce
                    + " model_contract_v2_probe_complete=true"
                    + " model_contract_v2_verified=" + contractVerified
                    + " model_request_v2_fields_verified=" + requestFieldsVerified
                    + " model_result_v2_binding_verified=" + resultBindingVerified
                    + " model_privacy_fallback_fail_closed=" + privacyFallbackFailClosed
                    + " model_raw_content_accepted=" + snapshot.isRawContentAccepted()
                    + " model_provider_registry_wired=" + snapshot.isProviderRegistryWired()
                    + " model_policy_router_wired=" + snapshot.isPolicyRouterWired()
                    + " model_invoked=" + snapshot.isModelInvoked()
                    + " npu_accessed=" + snapshot.isNpuAccessed()
                    + " hardware_accessed=" + snapshot.isHardwareAccessed()
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " model_contract_v2_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " model_provider_registry_wired=false"
                    + " model_policy_router_wired=false"
                    + " model_invoked=false"
                    + " npu_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false", exception);
        }
    }

    private static boolean rejectsUnsafeFallback() {
        try {
            new ModelContractV2.ModelRequest(
                    "unsafe-request",
                    ModelContractV2.Purpose.USER_DIALOGUE,
                    ModelContractV2.PrivacyClass.RESTRICTED,
                    new ModelContractV2.LatencyBudget(1_000),
                    new ModelContractV2.TokenBudget(100, 20, 120),
                    ModelContractV2.RequiredCapability.TEXT_GENERATION,
                    ModelContractV2.FallbackPolicy.POLICY_CONTROLLED,
                    TRACE,
                    INPUT);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}
