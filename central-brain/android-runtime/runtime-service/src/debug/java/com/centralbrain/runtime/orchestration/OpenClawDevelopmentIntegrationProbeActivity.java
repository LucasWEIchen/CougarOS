package com.centralbrain.runtime.orchestration;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.BuildConfig;
import com.centralbrain.runtime.model.OpenClawEndpointConfig;
import com.centralbrain.runtime.model.ModelProviderRegistry;
import com.centralbrain.runtime.scenario.ScenarioCatalog;

import java.util.UUID;

/** DUMP-protected WSL OpenClaw probe that emits metadata only. */
public final class OpenClawDevelopmentIntegrationProbeActivity extends Activity {
    private static final String TAG = "CbOpenClawDevProbe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        String boundedNonce = nonce != null && nonce.matches("[0-9]{10,24}")
                ? nonce : "invalid";
        new Thread(() -> {
            runProbe(boundedNonce);
            runOnUiThread(this::finish);
        }, "cb-openclaw-development-probe").start();
    }

    private void runProbe(String nonce) {
        DebugDecisionCompositionBoundary decision = null;
        try {
            Log.i(TAG, "nonce=" + nonce
                    + " openclaw_development_probe_started=true"
                    + " transport=ADB_REVERSE"
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false"
                    + " credential_logged=false");
            requireDevelopmentBuild();
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
                    + " openclaw_development_probe_complete=" + complete
                    + " provider_id=" + evidence.getModelProviderId()
                    + " model_latency_ms=" + evidence.getModelLatencyMs()
                    + " response_chars=" + evidence.getAssistantDisplayText().length()
                    + " endpoint_profile=development_wsl_openclaw"
                    + " transport=ADB_REVERSE"
                    + " websocket_protocol="
                    + OpenClawEndpointConfig.DEVELOPMENT_PROTOCOL_VERSION
                    + " network_accessed=" + evidence.isNetworkAccessed()
                    + " external_compute_accessed=" + evidence.isNetworkAccessed()
                    + " direct_npu_accessed=false"
                    + " model_action_authority=false"
                    + " vehicle_effect_dispatch_authorized=false"
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false"
                    + " credential_logged=false"
                    + " android13_arm64_verified=" + android13Arm64
                    + " ethernet_validated=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException failure) {
            Log.e(TAG, "nonce=" + nonce
                    + " openclaw_development_probe_complete=false"
                    + " failure_code=" + failureCode(failure)
                    + " transport=ADB_REVERSE"
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false"
                    + " credential_logged=false"
                    + " direct_npu_accessed=false"
                    + " vehicle_effect_dispatch_authorized=false"
                    + " ethernet_validated=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } finally {
            if (decision != null) {
                decision.close();
            }
        }
    }

    private static void requireDevelopmentBuild() {
        if (!BuildConfig.DEBUG
                || !BuildConfig.OPENCLAW_DEVELOPMENT_ROUTING_ENABLED
                || BuildConfig.OPENCLAW_TARGET_ROUTING_ENABLED
                || BuildConfig.OLLAMA_DEVELOPMENT_ENABLED
                || !BuildConfig.OPENCLAW_TARGET_ENDPOINT_CONFIGURED
                || !"development_wsl_openclaw".equals(
                        BuildConfig.MODEL_GATEWAY_PROFILE)
                || !"ws://127.0.0.1:18789".equals(
                        BuildConfig.OPENCLAW_BASE_URL)
                || BuildConfig.OPENCLAW_PROTOCOL_VERSION
                        != OpenClawEndpointConfig.DEVELOPMENT_PROTOCOL_VERSION) {
            throw new IllegalStateException("development OpenClaw profile disabled");
        }
    }

    private static String failureCode(RuntimeException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (message.contains("credential")) return "CREDENTIAL_UNAVAILABLE";
        if (message.contains("timeout") || message.contains("deadline")) {
            return "DEADLINE_EXCEEDED";
        }
        if (message.contains("authentication")) return "AUTHENTICATION_REJECTED";
        if (message.contains("structured") || message.contains("model inference")) {
            return "MODEL_OUTPUT_REJECTED";
        }
        if (message.contains("profile")) return "DEVELOPMENT_PROFILE_DISABLED";
        return "DEVELOPMENT_PROBE_FAILED";
    }
}
