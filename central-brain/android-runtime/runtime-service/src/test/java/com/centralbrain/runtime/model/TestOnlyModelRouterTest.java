package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.scheduler.InferenceResourceScheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class TestOnlyModelRouterTest {
    private static final String OWNER_A = "a".repeat(64);
    private static final String OWNER_B = "b".repeat(64);
    private static final String MODEL_DIGEST = "c".repeat(64);
    private static final String INPUT_DIGEST = "d".repeat(64);

    @Test
    public void schedulerLeaseDrivesTwoSequentialProviderRequests() {
        Harness harness = harness();
        RecordingObserver first = new RecordingObserver();
        RecordingObserver second = new RecordingObserver();
        assertTrue(harness.router.submit(request("first", OWNER_A, 10_000, 9_000), first)
                .isDispatched());
        assertFalse(harness.router.submit(
                request("second", OWNER_B, 10_000, 9_000), second).isDispatched());
        assertEquals(1, harness.scheduler.snapshot().getRunningSlotCount());
        assertEquals(1, harness.scheduler.snapshot().getQueuedCount());

        harness.executor.drain();
        assertEquals(ModelProvider.TerminalState.COMPLETED, first.terminal.getState());
        assertEquals(ModelProvider.TerminalState.COMPLETED, second.terminal.getState());
        assertEquals(2, first.chunks.size());
        assertEquals(2, second.chunks.size());
        assertEquals(0, harness.scheduler.snapshot().getActiveCount());
        assertEquals(0, harness.router.snapshot().getActiveRouteCount());
        assertEquals(2, harness.router.snapshot().getCompletedCount());
        assertEquals(2, harness.router.snapshot().getProviderIdentityValidationCount());
        assertEquals(0, harness.router.snapshot().getFallbackAttemptCount());
    }

    @Test
    public void queuedAndRunningCancellationAreOwnerIsolated() {
        Harness harness = harness();
        RecordingObserver running = new RecordingObserver();
        RecordingObserver queued = new RecordingObserver();
        harness.router.submit(request("running", OWNER_A, 10_000, 9_000), running);
        harness.router.submit(request("queued", OWNER_B, 10_000, 9_000), queued);

        assertEquals(
                InferenceResourceScheduler.CancelOutcome.NOT_FOUND_OR_NOT_OWNER,
                harness.router.cancelOwned("running", OWNER_B, "wrong owner")
                        .getSchedulerOutcome());
        assertEquals(
                InferenceResourceScheduler.CancelOutcome.CANCELLED_QUEUED,
                harness.router.cancelOwned("queued", OWNER_B, "cancel queued")
                        .getSchedulerOutcome());
        assertEquals(ModelProvider.TerminalState.CANCELLED, queued.terminal.getState());
        TestOnlyModelRouter.CancelResult runningCancel = harness.router.cancelOwned(
                "running", OWNER_A, "cancel running");
        assertEquals(
                InferenceResourceScheduler.CancelOutcome.PROVIDER_CANCEL_REQUIRED,
                runningCancel.getSchedulerOutcome());
        assertEquals(ModelProvider.CancelState.PENDING_PROVIDER_ACK,
                runningCancel.getProviderState());
        harness.executor.drain();
        assertEquals(ModelProvider.TerminalState.CANCELLED, running.terminal.getState());
        assertEquals(2, harness.router.snapshot().getCancelledCount());
        assertEquals(1, harness.router.snapshot().getProviderCancelCount());
    }

    @Test
    public void tickExpiresQueueAndCancelsRunningDeadline() {
        Harness queuedHarness = harness();
        RecordingObserver active = new RecordingObserver();
        RecordingObserver queued = new RecordingObserver();
        queuedHarness.router.submit(
                request("active", OWNER_A, 10_000, 9_000), active);
        queuedHarness.router.submit(
                request("queue-expire", OWNER_B, 10_000, 500), queued);
        queuedHarness.clock.set(1_500);
        TestOnlyModelRouter.TickResult queueTick = queuedHarness.router.tick();
        assertEquals(1, queueTick.getDeadlineTerminals());
        assertEquals(ModelProvider.TerminalState.DEADLINE_EXCEEDED,
                queued.terminal.getState());

        Harness runningHarness = harness();
        RecordingObserver running = new RecordingObserver();
        runningHarness.router.submit(
                request("run-expire", OWNER_A, 2_000, 1_000), running);
        runningHarness.clock.set(2_000);
        TestOnlyModelRouter.TickResult runningTick = runningHarness.router.tick();
        assertEquals(1, runningTick.getProviderCancels());
        runningHarness.executor.drain();
        assertEquals(ModelProvider.TerminalState.DEADLINE_EXCEEDED,
                running.terminal.getState());
        assertEquals(1, runningHarness.router.snapshot().getDeadlineExceededCount());
    }

    @Test
    public void retryableAndIsolatedFaultsNeverFallback() {
        Harness retryable = harness();
        retryable.provider.setFaultModeForTest(
                DeterministicStubModelProvider.FaultMode
                        .RETRYABLE_BEFORE_FIRST_CHUNK);
        RecordingObserver retryableObserver = new RecordingObserver();
        retryable.router.submit(
                request("retryable", OWNER_A, 10_000, 5_000),
                retryableObserver);
        retryable.executor.drain();
        assertEquals(ModelProvider.TerminalState.RETRYABLE_FAILURE,
                retryableObserver.terminal.getState());
        assertEquals(1, retryable.router.snapshot().getFailedCount());
        assertEquals(0, retryable.router.snapshot().getFallbackAttemptCount());
        assertEquals(TestOnlyModelRouter.FallbackPolicy.NO_FALLBACK,
                retryable.router.snapshot().getFallbackPolicy());

        Harness isolated = harness();
        isolated.provider.setFaultModeForTest(
                DeterministicStubModelProvider.FaultMode
                        .FAULT_ISOLATE_BEFORE_FIRST_CHUNK);
        RecordingObserver isolatedObserver = new RecordingObserver();
        isolated.router.submit(
                request("isolated", OWNER_A, 10_000, 5_000),
                isolatedObserver);
        isolated.executor.drain();
        assertEquals(ModelProvider.TerminalState.TERMINAL_FAILURE,
                isolatedObserver.terminal.getState());
        assertEquals(
                TestOnlyModelRouter.SubmitOutcome.PROVIDER_UNAVAILABLE,
                isolated.router.submit(
                        request("blocked", OWNER_B, 10_000, 5_000),
                        new RecordingObserver()).getOutcome());
        assertEquals(0, isolated.router.snapshot().getFallbackAttemptCount());
    }

    @Test
    public void providerIdentityMismatchFailsClosed() {
        AtomicLong clock = new AtomicLong(1_000);
        MismatchedHandleProvider provider = new MismatchedHandleProvider();
        InferenceResourceScheduler scheduler = scheduler(clock, provider);
        TestOnlyModelRouter router = TestOnlyModelRouter.createForContractTest(
                scheduler,
                provider);
        RecordingObserver observer = new RecordingObserver();
        router.submit(request("identity", OWNER_A, 10_000, 5_000), observer);

        assertNotNull(observer.terminal);
        assertEquals(ModelProvider.TerminalState.TERMINAL_FAILURE,
                observer.terminal.getState());
        assertEquals(1, router.snapshot().getFailedCount());
        assertEquals(0, router.snapshot().getProviderIdentityValidationCount());
        assertEquals(0, scheduler.snapshot().getActiveCount());
    }

    @Test
    public void duplicateProviderTerminalIsIgnored() {
        AtomicLong clock = new AtomicLong(1_000);
        DuplicateTerminalProvider provider = new DuplicateTerminalProvider();
        InferenceResourceScheduler scheduler = scheduler(clock, provider);
        TestOnlyModelRouter router = TestOnlyModelRouter.createForContractTest(
                scheduler,
                provider);
        RecordingObserver observer = new RecordingObserver();
        router.submit(request("duplicate", OWNER_A, 10_000, 5_000), observer);

        assertEquals(1, observer.terminalCount);
        assertEquals(1, router.snapshot().getCompletedCount());
        assertEquals(1, router.snapshot().getDuplicateTerminalIgnoredCount());
        assertEquals(0, scheduler.snapshot().getActiveCount());
    }

    @Test
    public void exactReplayKeepsOriginalObserverAndChangedInputIsRejected() {
        Harness harness = harness();
        RecordingObserver original = new RecordingObserver();
        RecordingObserver replay = new RecordingObserver();
        TestOnlyModelRouter.TrustedRouteRequest request = request(
                "replay",
                OWNER_A,
                10_000,
                5_000);
        assertEquals(
                TestOnlyModelRouter.SubmitOutcome.ADMITTED,
                harness.router.submit(request, original).getOutcome());
        assertEquals(
                TestOnlyModelRouter.SubmitOutcome.REPLAYED,
                harness.router.submit(request, replay).getOutcome());
        assertEquals(
                TestOnlyModelRouter.SubmitOutcome.REJECTED,
                harness.router.submit(
                        requestWithInput(
                                "replay",
                                OWNER_A,
                                "f".repeat(64),
                                10_000,
                                5_000),
                        replay).getOutcome());
        harness.executor.drain();
        assertEquals(1, original.terminalCount);
        assertEquals(0, replay.terminalCount);
        assertEquals(1, harness.router.snapshot().getSubmittedCount());
    }

    private static Harness harness() {
        AtomicLong clock = new AtomicLong(1_000);
        ManualExecutor executor = new ManualExecutor();
        ModelProvider.ModelSpec model = model();
        DeterministicStubModelProvider provider = new DeterministicStubModelProvider(
                model,
                executor,
                clock::get);
        provider.warmup(model);
        InferenceResourceScheduler scheduler = scheduler(clock, provider);
        return new Harness(
                provider,
                scheduler,
                TestOnlyModelRouter.createForContractTest(scheduler, provider),
                executor,
                clock);
    }

    private static InferenceResourceScheduler scheduler(
            AtomicLong clock,
            ModelProvider provider) {
        AtomicInteger leases = new AtomicInteger();
        return new InferenceResourceScheduler(
                new InferenceResourceScheduler.Limits(8, 4, 2, 1, 10_000),
                clock::get,
                () -> "router-lease-" + leases.incrementAndGet(),
                Collections.singletonList(
                        TestOnlyModelRouter.routeTargetForContractTest(provider)));
    }

    private static ModelProvider.ModelSpec model() {
        return new ModelProvider.ModelSpec(
                "central-intent-v0",
                "1",
                MODEL_DIGEST);
    }

    private static TestOnlyModelRouter.TrustedRouteRequest request(
            String requestId,
            String owner,
            long deadline,
            long queueWait) {
        return requestWithInput(
                requestId,
                owner,
                INPUT_DIGEST,
                deadline,
                queueWait);
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

    private abstract static class FakeProvider implements ModelProvider {
        private final Descriptor descriptor = ModelProviderProfiles
                .deterministicStub()
                .getDescriptor();

        @Override
        public Descriptor descriptor() {
            return descriptor;
        }

        @Override
        public Snapshot snapshot() {
            return new Snapshot(
                    descriptor,
                    LifecycleState.READY,
                    HealthState.HEALTHY,
                    1,
                    0,
                    0,
                    false,
                    "FAKE_READY");
        }

        @Override
        public Snapshot warmup(ModelSpec modelSpec) {
            return snapshot();
        }

        @Override
        public CancelState cancel(String requestId, String reason) {
            return CancelState.PENDING_PROVIDER_ACK;
        }

        @Override
        public Metrics metrics() {
            return new Metrics(descriptor.getProviderId(), 0, 0, 0, 0);
        }

        @Override
        public FaultSnapshot lastFault() {
            return new FaultSnapshot(descriptor.getProviderId(), "NONE", false, false);
        }

        @Override
        public void close() {
        }
    }

    private static final class MismatchedHandleProvider extends FakeProvider {
        @Override
        public InferenceHandle infer(
                InferenceRequest request,
                StreamObserver observer) {
            return new InferenceHandle("wrong.provider", request.getRequestId());
        }
    }

    private static final class DuplicateTerminalProvider extends FakeProvider {
        @Override
        public InferenceHandle infer(
                InferenceRequest request,
                StreamObserver observer) {
            TerminalResult result = new TerminalResult(
                    request.getRequestId(),
                    TerminalState.COMPLETED,
                    "e".repeat(64),
                    "FAKE_COMPLETED");
            observer.onTerminal(result);
            observer.onTerminal(result);
            return new InferenceHandle(
                    descriptor().getProviderId(),
                    request.getRequestId());
        }
    }
}
