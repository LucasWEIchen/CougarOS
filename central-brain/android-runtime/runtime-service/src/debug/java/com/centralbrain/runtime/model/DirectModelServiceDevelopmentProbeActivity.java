package com.centralbrain.runtime.model;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import com.centralbrain.runtime.BuildConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DUMP-protected debug probe for Android -> ADB reverse -> WSL Ollama.
 *
 * <p>The probe exercises the production-source direct Provider and protocol adapter. It records
 * metadata only and never grants model output authority over tools or vehicle effects.</p>
 */
public final class DirectModelServiceDevelopmentProbeActivity extends Activity {
    private static final String TAG = "CbDirectModelDevProbe";
    private static final long DEADLINE_MS = 150_000L;
    private static final String SYSTEM_PROMPT =
            "你是运行在汽车座舱中的 AIOS 模型服务，目标是服务驾驶员。"
                    + "你不得直接调用工具或车身执行器。请仅输出一个 JSON 对象，"
                    + "且唯一字段必须是 reply，格式示例为 {\"reply\":\"一句简短回复\"}。"
                    + "禁止 Markdown 代码块，禁止 intent、action、message、suggestions "
                    + "或任何其他字段，reply 不得超过 128 个字符。";
    private static final String USER_TEXT = "我有些疲惫";
    private static final String RESPONSE_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"reply\":{\"type\":\"string\","
                    + "\"minLength\":1,\"maxLength\":128}},\"required\":[\"reply\"],"
                    + "\"additionalProperties\":false}";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        String boundedNonce = nonce != null && nonce.matches("[0-9]{10,24}")
                ? nonce : "invalid";
        new Thread(() -> {
            runProbe(boundedNonce);
            runOnUiThread(this::finish);
        }, "cb-direct-model-development-probe").start();
    }

    private void runProbe(String nonce) {
        ExecutorService providerExecutor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "cb-direct-model-provider"));
        DirectModelServiceProvider provider = null;
        try {
            Log.i(TAG, "nonce=" + nonce
                    + " direct_model_development_probe_started=true"
                    + " endpoint_profile=development_wsl_adb_reverse"
                    + " transport=ADB_REVERSE"
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false");
            requireDevelopmentBuild();

            DirectModelServiceContract.Endpoint endpoint =
                    DirectModelServiceContract.developmentOllama(
                            BuildConfig.OLLAMA_MODEL);
            if (!DirectModelServiceContract.DEVELOPMENT_PROFILE_ID.equals(
                            endpoint.getProfileId())
                    || !"http://127.0.0.1:11434/api/chat".equals(
                            endpoint.getChatUri().toString())
                    || endpoint.isAgentGatewayRequired()
                    || endpoint.isArbitraryEndpointOverrideAllowed()) {
                throw new IllegalStateException("development endpoint contract rejected");
            }

            String sourceInputDigest = sha256(
                    SYSTEM_PROMPT + "\u0000" + USER_TEXT + "\u0000" + RESPONSE_SCHEMA);
            String requestId = "request.dev." + nonce;
            long startedAt = SystemClock.elapsedRealtime();
            long deadline = startedAt + DEADLINE_MS;
            OllamaChatProtocolAdapter.Payload payload =
                    OllamaChatProtocolAdapter.Payload.text(
                            SYSTEM_PROMPT,
                            USER_TEXT,
                            RESPONSE_SCHEMA);
            DirectModelServiceContract.Request protocolRequest =
                    new DirectModelServiceContract.Request(
                            endpoint,
                            requestId,
                            "session.dev." + nonce,
                            "idempotency.dev." + nonce,
                            DirectModelServiceContract.Modality.TEXT,
                            sha256(USER_TEXT),
                            sha256(SYSTEM_PROMPT),
                            sha256(RESPONSE_SCHEMA),
                            null,
                            deadline);
            DirectModelServiceProvider.ResolvedInput resolvedInput =
                    new DirectModelServiceProvider.ResolvedInput(
                            sourceInputDigest,
                            protocolRequest,
                            payload);
            ModelProvider.ModelSpec modelSpec = new ModelProvider.ModelSpec(
                    BuildConfig.OLLAMA_MODEL,
                    "ollama-chat-v1",
                    sha256(BuildConfig.OLLAMA_MODEL));

            provider = new DirectModelServiceProvider(
                    modelSpec,
                    endpoint,
                    request -> {
                        if (!requestId.equals(request.getRequestId())) {
                            throw new IllegalArgumentException("unexpected request identity");
                        }
                        return resolvedInput;
                    },
                    (candidateEndpoint, candidateModel) ->
                            endpoint.getChatUri().equals(candidateEndpoint.getChatUri())
                                    && modelSpec.getModelId().equals(
                                            candidateModel.getModelId()),
                    (request, resolved, content) -> validateStructuredReply(content),
                    new OllamaChatProtocolAdapter(),
                    providerExecutor,
                    SystemClock::elapsedRealtime);
            ModelProvider.Snapshot ready = provider.warmup(modelSpec);
            if (ready.getLifecycleState() != ModelProvider.LifecycleState.READY
                    || ready.getHealthState() != ModelProvider.HealthState.HEALTHY) {
                throw new IllegalStateException("direct model provider did not become ready");
            }

            CountDownLatch terminalLatch = new CountDownLatch(1);
            AtomicInteger chunkCount = new AtomicInteger();
            AtomicLong byteCount = new AtomicLong();
            AtomicLong nextSequence = new AtomicLong(1L);
            AtomicReference<ModelProvider.TerminalResult> terminal =
                    new AtomicReference<>();
            provider.infer(
                    new ModelProvider.InferenceRequest(
                            requestId,
                            BuildConfig.OLLAMA_MODEL,
                            sourceInputDigest,
                            deadline,
                            true),
                    new ModelProvider.StreamObserver() {
                        @Override
                        public void onChunk(ModelProvider.StreamChunk chunk) {
                            if (!requestId.equals(chunk.getRequestId())
                                    || chunk.getSequence()
                                            != nextSequence.getAndIncrement()) {
                                throw new IllegalStateException(
                                        "stream sequence contract rejected");
                            }
                            chunkCount.incrementAndGet();
                            byteCount.addAndGet(chunk.getContent().length);
                        }

                        @Override
                        public void onTerminal(ModelProvider.TerminalResult result) {
                            terminal.set(result);
                            terminalLatch.countDown();
                        }
                    });

            if (!terminalLatch.await(DEADLINE_MS + 5_000L, TimeUnit.MILLISECONDS)) {
                provider.cancel(requestId, "development probe timeout");
                throw new IllegalStateException("direct model probe deadline exceeded");
            }
            ModelProvider.TerminalResult result = terminal.get();
            ModelProvider.Metrics metrics = provider.metrics();
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            boolean complete = result != null
                    && result.getState() == ModelProvider.TerminalState.COMPLETED
                    && "DIRECT_MODEL_COMPLETED".equals(result.getDetailCode())
                    && chunkCount.get() > 0
                    && byteCount.get() > 0
                    && nextSequence.get() == chunkCount.get() + 1L
                    && metrics.getAcceptedCount() == 1L
                    && metrics.getCompletedCount() == 1L
                    && metrics.getCancelledCount() == 0L
                    && metrics.getFailureCount() == 0L
                    && provider.snapshot().getActiveRequestCount() == 0
                    && android13Arm64;
            long latencyMs = SystemClock.elapsedRealtime() - startedAt;
            boolean networkAccessed = chunkCount.get() > 0;

            Log.i(TAG, "nonce=" + nonce
                    + " direct_model_development_probe_complete=" + complete
                    + " provider_id=" + provider.descriptor().getProviderId()
                    + " endpoint_profile=development_wsl_adb_reverse"
                    + " model=" + BuildConfig.OLLAMA_MODEL
                    + " chunks=" + chunkCount.get()
                    + " response_bytes=" + byteCount.get()
                    + " latency_ms=" + latencyMs
                    + " terminal_state="
                    + (result == null ? "MISSING" : result.getState())
                    + " terminal_detail="
                    + (result == null ? "MISSING" : result.getDetailCode())
                    + " network_accessed=" + networkAccessed
                    + " wsl_ollama_accessed=" + networkAccessed
                    + " agent_gateway_used=false"
                    + " tool_authority=false"
                    + " effect_authority=false"
                    + " raw_prompt_logged=false"
                    + " raw_response_logged=false"
                    + " android13_arm64_verified=" + android13Arm64
                    + " ethernet_validated=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            logFailure(nonce, "PROBE_INTERRUPTED");
        } catch (RuntimeException failure) {
            logFailure(nonce, failureCode(failure));
        } finally {
            if (provider != null) {
                provider.close();
            }
            providerExecutor.shutdownNow();
        }
    }

    private static void requireDevelopmentBuild() {
        if (!BuildConfig.DEBUG
                || !BuildConfig.OLLAMA_DEVELOPMENT_ENABLED
                || !"development_wsl_ollama".equals(BuildConfig.MODEL_GATEWAY_PROFILE)
                || !"http://127.0.0.1:11434".equals(BuildConfig.OLLAMA_BASE_URL)
                || BuildConfig.DIRECT_MODEL_SERVICE_ENDPOINT_CONFIGURED
                || BuildConfig.DIRECT_MODEL_SERVICE_ROUTING_ENABLED
                || BuildConfig.OPENCLAW_TARGET_ROUTING_ENABLED
                || BuildConfig.OPENCLAW_DEVELOPMENT_ROUTING_ENABLED) {
            throw new IllegalStateException("development Ollama profile disabled");
        }
    }

    private static void validateStructuredReply(byte[] content) {
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(content, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("structured reply is not an object");
            }
            JsonObject object = parsed.getAsJsonObject();
            JsonElement replyElement = object.get("reply");
            if (object.entrySet().size() != 1
                    || replyElement == null
                    || !replyElement.isJsonPrimitive()
                    || !replyElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("structured reply shape rejected");
            }
            String reply = replyElement.getAsString();
            if (reply.trim().isEmpty() || reply.length() > 128) {
                throw new IllegalArgumentException("structured reply length rejected");
            }
            for (int index = 0; index < reply.length(); index++) {
                if (Character.isISOControl(reply.charAt(index))) {
                    throw new IllegalArgumentException(
                            "structured reply control character rejected");
                }
            }
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("structured reply parsing failed", failure);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder digest = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                digest.append(String.format("%02x", item & 0xff));
            }
            return digest.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void logFailure(String nonce, String code) {
        Log.e(TAG, "nonce=" + nonce
                + " direct_model_development_probe_complete=false"
                + " failure_code=" + code
                + " endpoint_profile=development_wsl_adb_reverse"
                + " network_accessed=false"
                + " wsl_ollama_accessed=false"
                + " agent_gateway_used=false"
                + " tool_authority=false"
                + " effect_authority=false"
                + " raw_prompt_logged=false"
                + " raw_response_logged=false"
                + " ethernet_validated=false"
                + " production_ready=false"
                + " target_hardware_validated=false");
    }

    private static String failureCode(RuntimeException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (message.contains("profile")) return "DEVELOPMENT_PROFILE_DISABLED";
        if (message.contains("deadline") || message.contains("timeout")) {
            return "DEADLINE_EXCEEDED";
        }
        if (message.contains("structured") || message.contains("output")) {
            return "MODEL_OUTPUT_REJECTED";
        }
        if (message.contains("transport") || message.contains("HTTP")) {
            return "MODEL_TRANSPORT_FAILED";
        }
        return "DIRECT_MODEL_PROBE_FAILED";
    }
}
