package com.centralbrain.runtime.orchestration;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.BuildConfig;
import com.centralbrain.runtime.model.ModelProviderRegistry;
import com.centralbrain.runtime.model.ModelProfileRouter;
import com.centralbrain.runtime.model.VllmEndpointConfig;
import com.centralbrain.runtime.scenario.ScenarioCatalog;

import java.util.UUID;

/** DUMP-protected Android-to-TY1100 vLLM probe that emits metadata only. */
public final class VllmPrototypeIntegrationProbeActivity extends Activity {
    private static final String TAG = "CbVllmPrototypeProbe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        String boundedNonce = nonce != null && nonce.matches("[0-9]{10,24}")
                ? nonce : "invalid";
        new Thread(() -> {
            runProbe(boundedNonce);
            runOnUiThread(this::finish);
        }, "cb-vllm-validation-probe").start();
    }

    private void runProbe(String nonce) {
        DebugDecisionCompositionBoundary decision = null;
        try {
            boolean targetEthernet = BuildConfig.VLLM_TARGET_ETHERNET_ENABLED;
            String androidTransport = targetEthernet ? "TARGET_ETHERNET" : "ADB_REVERSE";
            String aiTransport = targetEthernet
                    ? "DIRECT_ETHERNET" : "ETHERNET_SSH_TUNNEL";
            String endpointProfile = targetEthernet
                    ? "ty1100_general_2b_via_target_ethernet"
                    : "ty1100_general_9b_via_adb_reverse";
            String servedModel = targetEthernet
                    ? VllmEndpointConfig.TARGET_ETHERNET_MODEL
                    : VllmEndpointConfig.GENERAL_MODEL;
            Log.i(TAG, "nonce=" + nonce
                    + " vllm_prototype_probe_started=true"
                    + " android_transport=" + androidTransport
                    + " ai_transport=" + aiTransport
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false"
                    + " credential_logged=false");
            requireValidationBuild();
            ScenarioCatalog catalog = DebugSimulatedOrchestrationBackend.loadCatalog(this);
            decision = new DebugDecisionCompositionBoundary(catalog);
            OrchestrationBackend.SessionDescriptor session =
                    new OrchestrationBackend.SessionDescriptor(
                            "a".repeat(64),
                            UUID.randomUUID().toString(),
                            "scene.comfort.cold.v1",
                            1,
                            0,
                            System.currentTimeMillis(),
                            System.currentTimeMillis() + 180_000L);
            DebugDecisionCompositionBoundary.Evidence evidence = decision.prepare(
                    session, "scene.comfort.cold.v1", "b".repeat(64));
            DebugDecisionCompositionBoundary.Completion completion =
                    decision.complete(session, evidence);

            boolean model = evidence.getModelRouteDigest().matches("[0-9a-f]{64}")
                    && evidence.getModelOutputDigest().matches("[0-9a-f]{64}")
                    && evidence.getAssistantDisplayText().length() >= 1
                    && evidence.getAssistantDisplayText().length() <= 256
                    && ModelProviderRegistry.ANDROID_LOCAL_DEVELOPMENT_ID.equals(
                            evidence.getModelProviderId())
                    && (targetEthernet
                            ? ModelProfileRouter.TARGET_GENERAL_PROFILE_ID
                            : ModelProfileRouter.GENERAL_PROFILE_ID).equals(
                                    evidence.getModelProfileId())
                    && "central-intent-general-v1".equals(evidence.getModelId())
                    && evidence.getModelLatencyMs() >= 0L
                    && evidence.getModelLatencyMs() <= 120_000L;
            boolean authorityClosed = !evidence.isAutoExecutionAuthorized()
                    && !evidence.isProductionAuthority()
                    && !evidence.isNpuAccessed()
                    && !evidence.isHardwareAccessed();
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            boolean complete = model
                    && evidence.isNetworkAccessed()
                    && authorityClosed
                    && !completion.isReplayed()
                    && android13Arm64;

            Log.i(TAG, "nonce=" + nonce
                    + " vllm_prototype_probe_complete=" + complete
                    + " provider_id=" + evidence.getModelProviderId()
                    + " model_profile_id=" + evidence.getModelProfileId()
                    + " model_id=" + evidence.getModelId()
                    + " model_latency_ms=" + evidence.getModelLatencyMs()
                    + " response_chars=" + evidence.getAssistantDisplayText().length()
                    + " endpoint_profile=" + endpointProfile
                    + " model=" + servedModel
                    + " context_tokens="
                    + VllmEndpointConfig.GENERAL_MAX_CONTEXT_TOKENS
                    + " model_routing_enabled=true"
                    + " android_transport=" + androidTransport
                    + " ai_transport=" + aiTransport
                    + " network_accessed=" + evidence.isNetworkAccessed()
                    + " external_compute_accessed=" + evidence.isNetworkAccessed()
                    + " direct_npu_accessed=false"
                    + " model_action_authority=false"
                    + " vehicle_effect_dispatch_authorized=false"
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false"
                    + " credential_logged=false"
                    + " android13_arm64_verified=" + android13Arm64
                    + " ethernet_validated=true"
                    + " direct_android_ethernet_validated=" + targetEthernet
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException failure) {
            Log.e(TAG, "nonce=" + nonce
                    + " vllm_prototype_probe_complete=false"
                    + " failure_code=" + failureCode(failure)
                    + " android_transport=" + (BuildConfig.VLLM_TARGET_ETHERNET_ENABLED
                            ? "TARGET_ETHERNET" : "ADB_REVERSE")
                    + " ai_transport=" + (BuildConfig.VLLM_TARGET_ETHERNET_ENABLED
                            ? "DIRECT_ETHERNET" : "ETHERNET_SSH_TUNNEL")
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false"
                    + " credential_logged=false"
                    + " direct_npu_accessed=false"
                    + " vehicle_effect_dispatch_authorized=false"
                    + " ethernet_validated=false"
                    + " direct_android_ethernet_validated=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } finally {
            if (decision != null) {
                decision.close();
            }
        }
    }

    private static void requireValidationBuild() {
        boolean targetEthernet = BuildConfig.VLLM_TARGET_ETHERNET_ENABLED;
        String expectedProfile = targetEthernet
                ? "target_ty1100_vllm_ethernet" : "development_ty1100_vllm";
        String expectedGeneralBaseUrl = targetEthernet
                ? "http://169.254.202.110:8000" : "http://127.0.0.1:10030";
        String expectedSmokingBaseUrl = targetEthernet
                ? "http://169.254.202.110:8000" : "http://127.0.0.1:10031";
        String expectedGeneralModel = targetEthernet
                ? VllmEndpointConfig.TARGET_ETHERNET_MODEL
                : VllmEndpointConfig.GENERAL_MODEL;
        if (!BuildConfig.DEBUG
                || !BuildConfig.VLLM_DEVELOPMENT_ENABLED
                || BuildConfig.OPENCLAW_TARGET_ROUTING_ENABLED
                || BuildConfig.OLLAMA_DEVELOPMENT_ENABLED
                || BuildConfig.OPENCLAW_DEVELOPMENT_ROUTING_ENABLED
                || !expectedProfile.equals(BuildConfig.MODEL_GATEWAY_PROFILE)
                || !expectedGeneralBaseUrl.equals(BuildConfig.VLLM_BASE_URL)
                || !expectedGeneralModel.equals(BuildConfig.VLLM_MODEL)
                || !BuildConfig.VLLM_MODEL_ROUTING_ENABLED
                || !BuildConfig.VLLM_PREWARM_REQUIRED
                || !expectedGeneralBaseUrl.equals(BuildConfig.VLLM_GENERAL_BASE_URL)
                || !expectedGeneralModel.equals(
                        BuildConfig.VLLM_GENERAL_MODEL)
                || BuildConfig.VLLM_GENERAL_CONTEXT_TOKENS
                        != ModelProfileRouter.GENERAL_MAX_CONTEXT_TOKENS
                || !expectedSmokingBaseUrl.equals(BuildConfig.VLLM_SMOKING_BASE_URL)
                || !VllmEndpointConfig.SMOKING_MODEL.equals(
                        BuildConfig.VLLM_SMOKING_MODEL)
                || BuildConfig.VLLM_SMOKING_CONTEXT_TOKENS
                        != ModelProfileRouter.SMOKING_MAX_CONTEXT_TOKENS) {
            throw new IllegalStateException("TY1100 vLLM prototype profile disabled");
        }
    }

    private static String failureCode(RuntimeException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (message.contains("timeout") || message.contains("deadline")) {
            return "DEADLINE_EXCEEDED";
        }
        if (message.contains("structured") || message.contains("model inference")) {
            return "MODEL_OUTPUT_REJECTED";
        }
        if (message.contains("profile")) return "DEVELOPMENT_PROFILE_DISABLED";
        return "DEVELOPMENT_PROBE_FAILED";
    }
}
