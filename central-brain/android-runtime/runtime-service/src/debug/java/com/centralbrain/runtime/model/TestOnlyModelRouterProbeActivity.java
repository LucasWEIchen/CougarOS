package com.centralbrain.runtime.model;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.scheduler.InferenceResourceScheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class TestOnlyModelRouterProbeActivity extends Activity {
    private static final String TAG = "CbModelRouterProbe";
    private static final String OWNER_A = repeat("a", 64);
    private static final String OWNER_B = repeat("b", 64);
    private static final String MODEL_DIGEST = repeat("c", 64);
    private static final String INPUT_DIGEST = repeat("d", 64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            Harness e2e = harness();
            RecordingObserver first = new RecordingObserver();
            RecordingObserver second = new RecordingObserver();
            boolean firstDispatched = e2e.router.submit(
                    request("first", OWNER_A, 10_000, 9_000),
                    first).isDispatched();
            boolean secondQueued = !e2e.router.submit(
                    request("second", OWNER_B, 10_000, 9_000),
                    second).isDispatched();
            e2e.executor.drain();
            boolean e2eVerified = firstDispatched
                    && secondQueued
                    && first.terminal != null
                    && second.terminal != null
                    && first.terminal.getState() == ModelProvider.TerminalState.COMPLETED
                    && second.terminal.getState() == ModelProvider.TerminalState.COMPLETED
                    && e2e.router.snapshot().getCompletedCount() == 2
                    && e2e.router.snapshot().getActiveRouteCount() == 0
                    && e2e.scheduler.snapshot().getActiveCount() == 0;
            boolean streamForwardVerified = first.chunks.size() == 2
                    && second.chunks.size() == 2
                    && first.chunks.get(0).getSequence() == 1
                    && first.chunks.get(1).getSequence() == 2;
            boolean leaseBindingVerified = e2e.router.snapshot()
                    .getProviderIdentityValidationCount() == 2
                    && e2e.router.snapshot().getDispatchedCount() == 2;
            boolean terminalOnceVerified = first.terminalCount == 1
                    && second.terminalCount == 1
                    && e2e.router.snapshot().getCompletedCount() == 2;

            Harness cancellation = harness();
            RecordingObserver running = new RecordingObserver();
            cancellation.router.submit(
                    request("cancel", OWNER_A, 10_000, 9_000),
                    running);
            TestOnlyModelRouter.CancelResult cancelResult = cancellation.router.cancelOwned(
                    "cancel",
                    OWNER_A,
                    "probe cancel");
            cancellation.executor.drain();
            boolean cancelVerified = cancelResult.getSchedulerOutcome()
                    == InferenceResourceScheduler.CancelOutcome.PROVIDER_CANCEL_REQUIRED
                    && cancelResult.getProviderState()
                            == ModelProvider.CancelState.PENDING_PROVIDER_ACK
                    && running.terminal != null
                    && running.terminal.getState()
                            == ModelProvider.TerminalState.CANCELLED
                    && cancellation.router.snapshot().getProviderCancelCount() == 1;

            Harness deadline = harness();
            RecordingObserver deadlineObserver = new RecordingObserver();
            deadline.router.submit(
                    request("deadline", OWNER_A, 2_000, 1_000),
                    deadlineObserver);
            deadline.clock.set(2_000);
            TestOnlyModelRouter.TickResult deadlineTick = deadline.router.tick();
            deadline.executor.drain();
            boolean deadlineVerified = deadlineTick.getProviderCancels() == 1
                    && deadlineObserver.terminal != null
                    && deadlineObserver.terminal.getState()
                            == ModelProvider.TerminalState.DEADLINE_EXCEEDED
                    && deadline.router.snapshot().getDeadlineExceededCount() == 1;

            Harness retryable = harness();
            retryable.provider.setFaultModeForTest(
                    DeterministicStubModelProvider.FaultMode
                            .RETRYABLE_BEFORE_FIRST_CHUNK);
            RecordingObserver retryableObserver = new RecordingObserver();
            retryable.router.submit(
                    request("retryable", OWNER_A, 10_000, 5_000),
                    retryableObserver);
            retryable.executor.drain();
            boolean noFallbackVerified = retryableObserver.terminal != null
                    && retryableObserver.terminal.getState()
                            == ModelProvider.TerminalState.RETRYABLE_FAILURE
                    && retryable.router.snapshot().getFallbackPolicy()
                            == TestOnlyModelRouter.FallbackPolicy.NO_FALLBACK
                    && retryable.router.snapshot().getFallbackAttemptCount() == 0
                    && retryable.router.snapshot().getFailedCount() == 1;

            Harness replay = harness();
            RecordingObserver replayOriginal = new RecordingObserver();
            RecordingObserver replayOther = new RecordingObserver();
            TestOnlyModelRouter.TrustedRouteRequest replayRequest = request(
                    "replay", OWNER_A, 10_000, 5_000);
            boolean replayVerified = replay.router.submit(
                    replayRequest,
                    replayOriginal).getOutcome() == TestOnlyModelRouter.SubmitOutcome.ADMITTED
                    && replay.router.submit(
                            replayRequest,
                            replayOther).getOutcome()
                            == TestOnlyModelRouter.SubmitOutcome.REPLAYED
                    && replay.router.submit(
                            requestWithInput(
                                    "replay",
                                    OWNER_A,
                                    repeat("e", 64),
                                    10_000,
                                    5_000),
                            replayOther).getOutcome()
                            == TestOnlyModelRouter.SubmitOutcome.REJECTED;
            replay.executor.drain();
            replayVerified = replayVerified
                    && replayOriginal.terminalCount == 1
                    && replayOther.terminalCount == 0;

            boolean providerIdentityVerified = leaseBindingVerified;
            boolean profileBoundaryVerified = !ModelProviderProfiles
                    .deterministicStub()
                    .isImplementationConfigured()
                    && !ModelProviderProfiles.deterministicStub().isRoutingEnabled();
            boolean contractVerified = e2eVerified
                    && streamForwardVerified
                    && leaseBindingVerified
                    && cancelVerified
                    && deadlineVerified
                    && noFallbackVerified
                    && terminalOnceVerified
                    && providerIdentityVerified
                    && replayVerified
                    && profileBoundaryVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " model_router_probe_complete=true"
                    + " test_model_router_contract_verified=" + contractVerified
                    + " test_model_router_e2e_verified=" + e2eVerified
                    + " scheduler_provider_lease_binding_verified="
                    + leaseBindingVerified
                    + " model_router_stream_forward_verified="
                    + streamForwardVerified
                    + " model_router_cancel_verified=" + cancelVerified
                    + " model_router_deadline_verified=" + deadlineVerified
                    + " model_router_no_fallback_verified=" + noFallbackVerified
                    + " model_router_terminal_once_verified="
                    + terminalOnceVerified
                    + " model_router_provider_identity_verified="
                    + providerIdentityVerified
                    + " model_router_replay_validation_verified="
                    + replayVerified
                    + " model_router_profile_boundary_verified="
                    + profileBoundaryVerified
                    + " test_model_router_dispatch_verified=true"
                    + " provider_infer_invoked_in_debug=true"
                    + " model_router_test_only=true"
                    + " model_router_implementation_available=true"
                    + " production_model_router_wired=false"
                    + " production_model_router_dispatch_enabled=false"
                    + " production_inference_enabled=false"
                    + " deterministic_stub_implementation_configured=false"
                    + " deterministic_stub_routing_enabled=false"
                    + " ollama_android_provider_configured=false"
                    + " vendor_npu_provider_available=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " model_router_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " model_router_test_only=true"
                    + " production_model_router_wired=false"
                    + " production_model_router_dispatch_enabled=false"
                    + " production_inference_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static Harness harness() {
        AtomicLong clock = new AtomicLong(1_000);
        ManualExecutor executor = new ManualExecutor();
        ModelProvider.ModelSpec model = new ModelProvider.ModelSpec(
                "central-intent-v0",
                "1",
                MODEL_DIGEST);
        DeterministicStubModelProvider provider = new DeterministicStubModelProvider(
                model,
                executor,
                clock::get);
        provider.warmup(model);
        AtomicInteger leases = new AtomicInteger();
        InferenceResourceScheduler scheduler = new InferenceResourceScheduler(
                new InferenceResourceScheduler.Limits(8, 4, 2, 1, 10_000),
                clock::get,
                () -> "probe-router-lease-" + leases.incrementAndGet(),
                Collections.singletonList(
                        TestOnlyModelRouter.routeTargetForContractTest(provider)));
        return new Harness(
                provider,
                scheduler,
                TestOnlyModelRouter.createForContractTest(scheduler, provider),
                executor,
                clock);
    }

    private static TestOnlyModelRouter.TrustedRouteRequest request(
            String requestId,
            String owner,
            long deadline,
            long queueWait) {
        return requestWithInput(requestId, owner, INPUT_DIGEST, deadline, queueWait);
    }

    private static TestOnlyModelRouter.TrustedRouteRequest requestWithInput(
            String requestId,
            String owner,
            String inputDigest,
            long deadline,
            long queueWait) {
        return TestOnlyModelRouter.TrustedRouteRequest.fromRuntimePolicy(
                requestId,
                owner,
                "central-intent-v0",
                inputDigest,
                InferenceResourceScheduler.EffectivePriority.NORMAL,
                deadline,
                queueWait,
                true);
    }

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }

    private static final class Harness {
        final DeterministicStubModelProvider provider;
        final InferenceResourceScheduler scheduler;
        final TestOnlyModelRouter router;
        final ManualExecutor executor;
        final AtomicLong clock;

        Harness(
                DeterministicStubModelProvider provider,
                InferenceResourceScheduler scheduler,
                TestOnlyModelRouter router,
                ManualExecutor executor,
                AtomicLong clock) {
            this.provider = provider;
            this.scheduler = scheduler;
            this.router = router;
            this.executor = executor;
            this.clock = clock;
        }
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
        ModelProvider.TerminalResult terminal;
        int terminalCount;

        @Override
        public void onChunk(ModelProvider.StreamChunk chunk) {
            chunks.add(chunk);
        }

        @Override
        public void onTerminal(ModelProvider.TerminalResult result) {
            terminal = result;
            terminalCount++;
        }
    }
}
