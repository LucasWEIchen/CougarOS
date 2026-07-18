package com.centralbrain.runtime.model;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class LocalModelProviderProbeActivity extends Activity {
    private static final String TAG = "CbLocalProvider";
    private static final String MODEL_DIGEST = repeat("a", 64);
    private static final String INPUT_DIGEST = repeat("b", 64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            ModelProvider.ModelSpec model = model();
            AtomicLong clock = new AtomicLong(1_000);
            ManualExecutor streamExecutor = new ManualExecutor();
            LocalModelProvider streamProvider = LocalModelProvider.createForDevelopment(
                    model,
                    (spec, request, signal) -> LocalModelProvider.EngineOutput.of(
                            bytes("bounded-one"),
                            bytes("bounded-two")),
                    streamExecutor,
                    clock::get,
                    new LocalModelProvider.StreamLimits(2, 32, 64));
            boolean lifecycleVerified = streamProvider.snapshot().getLifecycleState()
                            == ModelProvider.LifecycleState.COLD
                    && streamProvider.warmup(model).getLifecycleState()
                            == ModelProvider.LifecycleState.READY
                    && streamProvider.snapshot().getLoadedModelCount() == 1;
            RecordingObserver streamObserver = new RecordingObserver();
            streamProvider.infer(request("stream", true, 10_000), streamObserver);
            streamExecutor.drain();
            boolean streamLimitVerified = streamObserver.chunks.size() == 2
                    && streamObserver.terminal != null
                    && streamObserver.terminal.getState()
                            == ModelProvider.TerminalState.COMPLETED
                    && streamProvider.metrics().getCompletedCount() == 1;

            AtomicBoolean cancelEngineInvoked = new AtomicBoolean();
            ManualExecutor cancelExecutor = new ManualExecutor();
            LocalModelProvider cancelProvider = LocalModelProvider.createForDevelopment(
                    model,
                    (spec, request, signal) -> {
                        cancelEngineInvoked.set(true);
                        return LocalModelProvider.EngineOutput.of(bytes("blocked"));
                    },
                    cancelExecutor,
                    clock::get,
                    LocalModelProvider.StreamLimits.defaults());
            cancelProvider.warmup(model);
            RecordingObserver cancelObserver = new RecordingObserver();
            cancelProvider.infer(request("cancel", true, 10_000), cancelObserver);
            boolean cancelPending = cancelProvider.cancel("cancel", "probe")
                    == ModelProvider.CancelState.PENDING_PROVIDER_ACK;
            cancelExecutor.drain();
            boolean cancelVerified = cancelPending
                    && !cancelEngineInvoked.get()
                    && cancelObserver.terminal != null
                    && cancelObserver.terminal.getState()
                            == ModelProvider.TerminalState.CANCELLED;

            AtomicLong deadlineClock = new AtomicLong(1_000);
            ManualExecutor deadlineExecutor = new ManualExecutor();
            LocalModelProvider deadlineProvider = LocalModelProvider.createForDevelopment(
                    model,
                    (spec, request, signal) -> {
                        deadlineClock.set(2_000);
                        return LocalModelProvider.EngineOutput.of(bytes("late"));
                    },
                    deadlineExecutor,
                    deadlineClock::get,
                    LocalModelProvider.StreamLimits.defaults());
            deadlineProvider.warmup(model);
            RecordingObserver deadlineObserver = new RecordingObserver();
            deadlineProvider.infer(request("deadline", true, 1_500), deadlineObserver);
            deadlineExecutor.drain();
            boolean deadlineVerified = deadlineObserver.chunks.isEmpty()
                    && deadlineObserver.terminal != null
                    && deadlineObserver.terminal.getState()
                            == ModelProvider.TerminalState.DEADLINE_EXCEEDED;

            ManualExecutor limitExecutor = new ManualExecutor();
            LocalModelProvider limitProvider = LocalModelProvider.createForDevelopment(
                    model,
                    (spec, request, signal) -> LocalModelProvider.EngineOutput.of(
                            bytes("one"), bytes("two"), bytes("three")),
                    limitExecutor,
                    clock::get,
                    new LocalModelProvider.StreamLimits(2, 16, 32));
            limitProvider.warmup(model);
            RecordingObserver limitObserver = new RecordingObserver();
            limitProvider.infer(request("limit", true, 10_000), limitObserver);
            limitExecutor.drain();
            boolean overflowRejected = limitObserver.chunks.isEmpty()
                    && limitObserver.terminal != null
                    && limitObserver.terminal.getState()
                            == ModelProvider.TerminalState.TERMINAL_FAILURE
                    && "LOCAL_OUTPUT_LIMIT_EXCEEDED".equals(
                            limitProvider.lastFault().getFaultCode());

            ModelProvider.Descriptor descriptor = streamProvider.descriptor();
            boolean profileBoundaryVerified = descriptor.getBackendKind()
                            == ModelProvider.BackendKind.ANDROID_LOCAL_DEVELOPMENT
                    && descriptor.getAssurance() == ModelProvider.Assurance.DEBUG_ONLY
                    && descriptor.getFallbackClass() == ModelProvider.FallbackClass.NEVER
                    && !descriptor.isProductionEligible()
                    && !descriptor.isHardwareBacked()
                    && !streamProvider.snapshot().isHardwareAccessed()
                    && !ModelProviderProfiles.androidLocalDevelopment()
                            .isImplementationConfigured()
                    && !ModelProviderProfiles.androidLocalDevelopment().isRoutingEnabled();
            ModelProviderRegistry.RegistrySnapshot registry =
                    ModelProviderRegistry.createForContractTest().snapshot(1_000);
            boolean registryBoundaryVerified = registry.getDevelopmentAvailableCount() == 1
                    && registry.getProductionReadyCount() == 0
                    && !registry.isProductionRoutingEnabled();
            boolean verified = lifecycleVerified
                    && streamLimitVerified
                    && cancelVerified
                    && deadlineVerified
                    && overflowRejected
                    && profileBoundaryVerified
                    && registryBoundaryVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " local_model_provider_probe_complete=true"
                    + " local_model_provider_verified=" + verified
                    + " local_model_provider_lifecycle_verified=" + lifecycleVerified
                    + " local_model_provider_stream_limit_verified="
                    + streamLimitVerified
                    + " local_model_provider_cancel_verified=" + cancelVerified
                    + " local_model_provider_deadline_verified=" + deadlineVerified
                    + " local_model_provider_overflow_rejected=" + overflowRejected
                    + " local_model_provider_profile_boundary_verified="
                    + profileBoundaryVerified
                    + " local_model_provider_registry_boundary_verified="
                    + registryBoundaryVerified
                    + " local_model_provider_debug_only=true"
                    + " local_model_provider_release_source_absent=true"
                    + " local_model_provider_runtime_wired=false"
                    + " local_model_provider_vendor_npu_fallback_enabled=false"
                    + " production_inference_enabled=false"
                    + " raw_model_content_logged=false"
                    + " network_accessed=false"
                    + " npu_accessed=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " local_model_provider_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " local_model_provider_debug_only=true"
                    + " local_model_provider_release_source_absent=true"
                    + " local_model_provider_runtime_wired=false"
                    + " local_model_provider_vendor_npu_fallback_enabled=false"
                    + " production_inference_enabled=false"
                    + " raw_model_content_logged=false"
                    + " network_accessed=false"
                    + " npu_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false", exception);
        }
    }

    private static ModelProvider.ModelSpec model() {
        return new ModelProvider.ModelSpec("central-intent-v0", "1", MODEL_DIGEST);
    }

    private static ModelProvider.InferenceRequest request(
            String requestId,
            boolean streaming,
            long deadline) {
        return new ModelProvider.InferenceRequest(
                requestId,
                "central-intent-v0",
                INPUT_DIGEST,
                deadline,
                streaming);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> pending = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }

        void drain() {
            while (!pending.isEmpty()) {
                pending.remove().run();
            }
        }
    }

    private static final class RecordingObserver implements ModelProvider.StreamObserver {
        private final List<ModelProvider.StreamChunk> chunks = new ArrayList<>();
        private ModelProvider.TerminalResult terminal;

        @Override
        public void onChunk(ModelProvider.StreamChunk chunk) {
            chunks.add(chunk);
        }

        @Override
        public void onTerminal(ModelProvider.TerminalResult result) {
            terminal = result;
        }
    }
}
