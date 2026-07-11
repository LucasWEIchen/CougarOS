package com.centralbrain.runtime.model;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public final class DeterministicStubProviderProbeActivity extends Activity {
    private static final String TAG = "CbStubProviderProbe";
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
            AtomicLong clock = new AtomicLong(1_000);
            ModelProvider.ModelSpec model = model();

            ManualExecutor firstExecutor = new ManualExecutor();
            DeterministicStubModelProvider first = new DeterministicStubModelProvider(
                    model,
                    firstExecutor,
                    clock::get);
            boolean lifecycleVerified = first.snapshot().getLifecycleState()
                    == ModelProvider.LifecycleState.COLD
                    && first.warmup(model).getLifecycleState()
                            == ModelProvider.LifecycleState.READY
                    && first.snapshot().getLoadedModelCount() == 1
                    && !first.snapshot().isHardwareAccessed();
            RecordingObserver firstObserver = new RecordingObserver();
            first.infer(request("first", true), firstObserver);
            firstExecutor.drain();
            boolean streamVerified = firstObserver.chunks.size() == 2
                    && firstObserver.chunks.get(0).getSequence() == 1
                    && firstObserver.chunks.get(1).getSequence() == 2
                    && firstObserver.events.equals(
                            Arrays.asList("chunk-1", "chunk-2", "terminal"))
                    && firstObserver.terminal != null
                    && firstObserver.terminal.getState()
                            == ModelProvider.TerminalState.COMPLETED
                    && firstObserver.joined().length > 0;

            ManualExecutor secondExecutor = new ManualExecutor();
            DeterministicStubModelProvider second = new DeterministicStubModelProvider(
                    model,
                    secondExecutor,
                    clock::get);
            second.warmup(model);
            RecordingObserver secondObserver = new RecordingObserver();
            second.infer(request("second", true), secondObserver);
            secondExecutor.drain();
            boolean outputDeterministic = Arrays.equals(
                    firstObserver.joined(),
                    secondObserver.joined())
                    && firstObserver.terminal.getOutputDigest().equals(
                            secondObserver.terminal.getOutputDigest());

            ManualExecutor cancelExecutor = new ManualExecutor();
            DeterministicStubModelProvider cancelProvider =
                    new DeterministicStubModelProvider(model, cancelExecutor, clock::get);
            cancelProvider.warmup(model);
            RecordingObserver cancelObserver = new RecordingObserver();
            cancelProvider.infer(request("cancel", true), cancelObserver);
            boolean cancelPending = cancelProvider.cancel(
                    "cancel", "probe cancel") == ModelProvider.CancelState
                            .PENDING_PROVIDER_ACK;
            cancelExecutor.drain();
            boolean cancelAckVerified = cancelPending
                    && cancelObserver.terminal != null
                    && cancelObserver.terminal.getState()
                            == ModelProvider.TerminalState.CANCELLED
                    && cancelProvider.snapshot().getActiveRequestCount() == 0;

            ManualExecutor retryExecutor = new ManualExecutor();
            DeterministicStubModelProvider retryProvider =
                    new DeterministicStubModelProvider(model, retryExecutor, clock::get);
            retryProvider.warmup(model);
            retryProvider.setFaultModeForTest(
                    DeterministicStubModelProvider.FaultMode
                            .RETRYABLE_BEFORE_FIRST_CHUNK);
            RecordingObserver retryObserver = new RecordingObserver();
            retryProvider.infer(request("retry", true), retryObserver);
            retryExecutor.drain();
            boolean retryableFaultVerified = retryObserver.terminal != null
                    && retryObserver.terminal.getState()
                            == ModelProvider.TerminalState.RETRYABLE_FAILURE
                    && retryProvider.lastFault().isRetryable()
                    && !retryProvider.lastFault().isIsolated();

            ManualExecutor isolatedExecutor = new ManualExecutor();
            DeterministicStubModelProvider isolatedProvider =
                    new DeterministicStubModelProvider(model, isolatedExecutor, clock::get);
            isolatedProvider.warmup(model);
            isolatedProvider.setFaultModeForTest(
                    DeterministicStubModelProvider.FaultMode
                            .FAULT_ISOLATE_BEFORE_FIRST_CHUNK);
            RecordingObserver isolatedObserver = new RecordingObserver();
            isolatedProvider.infer(request("isolated", true), isolatedObserver);
            isolatedExecutor.drain();
            boolean faultIsolationVerified = isolatedObserver.terminal != null
                    && isolatedObserver.terminal.getState()
                            == ModelProvider.TerminalState.TERMINAL_FAILURE
                    && isolatedProvider.lastFault().isIsolated()
                    && isolatedProvider.snapshot().getLifecycleState()
                            == ModelProvider.LifecycleState.FAULT_ISOLATED
                    && isolatedProvider.snapshot().getActiveRequestCount() == 0;

            boolean metricsVerified = first.metrics().getAcceptedCount() == 1
                    && first.metrics().getCompletedCount() == 1
                    && cancelProvider.metrics().getCancelledCount() == 1
                    && retryProvider.metrics().getFailureCount() == 1
                    && isolatedProvider.metrics().getFailureCount() == 1;
            boolean profileBoundaryVerified = first.descriptor().getAssurance()
                    == ModelProvider.Assurance.TEST_ONLY
                    && !first.descriptor().isHardwareBacked()
                    && !first.descriptor().isProductionEligible()
                    && !ModelProviderProfiles.deterministicStub()
                            .isImplementationConfigured()
                    && !ModelProviderProfiles.deterministicStub().isRoutingEnabled();
            boolean contractVerified = lifecycleVerified
                    && streamVerified
                    && outputDeterministic
                    && cancelAckVerified
                    && metricsVerified
                    && retryableFaultVerified
                    && faultIsolationVerified
                    && profileBoundaryVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " stub_provider_probe_complete=true"
                    + " deterministic_stub_provider_contract_verified="
                    + contractVerified
                    + " deterministic_stub_lifecycle_verified=" + lifecycleVerified
                    + " deterministic_stub_stream_verified=" + streamVerified
                    + " deterministic_stub_output_deterministic="
                    + outputDeterministic
                    + " deterministic_stub_cancel_ack_verified="
                    + cancelAckVerified
                    + " deterministic_stub_metrics_verified=" + metricsVerified
                    + " deterministic_stub_retryable_fault_verified="
                    + retryableFaultVerified
                    + " deterministic_stub_fault_isolation_verified="
                    + faultIsolationVerified
                    + " deterministic_stub_profile_boundary_verified="
                    + profileBoundaryVerified
                    + " deterministic_stub_test_only=true"
                    + " deterministic_stub_implementation_available=true"
                    + " deterministic_stub_implementation_configured=false"
                    + " deterministic_stub_routing_enabled=false"
                    + " model_provider_runtime_wired=false"
                    + " model_router_dispatch_enabled=false"
                    + " production_inference_enabled=false"
                    + " ollama_android_provider_configured=false"
                    + " vendor_npu_provider_available=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " stub_provider_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " deterministic_stub_test_only=true"
                    + " deterministic_stub_implementation_configured=false"
                    + " deterministic_stub_routing_enabled=false"
                    + " model_provider_runtime_wired=false"
                    + " model_router_dispatch_enabled=false"
                    + " production_inference_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static ModelProvider.ModelSpec model() {
        return new ModelProvider.ModelSpec(
                "central-intent-v0",
                "1",
                MODEL_DIGEST);
    }

    private static ModelProvider.InferenceRequest request(
            String requestId,
            boolean streaming) {
        return new ModelProvider.InferenceRequest(
                requestId,
                "central-intent-v0",
                INPUT_DIGEST,
                10_000,
                streaming);
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
        final List<ModelProvider.StreamChunk> chunks = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        ModelProvider.TerminalResult terminal;

        @Override
        public void onChunk(ModelProvider.StreamChunk chunk) {
            chunks.add(chunk);
            events.add("chunk-" + chunk.getSequence());
        }

        @Override
        public void onTerminal(ModelProvider.TerminalResult result) {
            terminal = result;
            events.add("terminal");
        }

        byte[] joined() {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (ModelProvider.StreamChunk chunk : chunks) {
                byte[] content = chunk.getContent();
                output.write(content, 0, content.length);
            }
            return output.toByteArray();
        }
    }
}
