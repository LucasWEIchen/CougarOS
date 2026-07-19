package com.centralbrain.runtime.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class LocalModelProviderTest {
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    @Test
    public void developmentProfileStreamsBoundedInProcessOutput() {
        Harness harness = harness((model, request, signal) -> LocalModelProvider.EngineOutput.of(
                bytes("first"),
                bytes("second")),
                new LocalModelProvider.StreamLimits(2, 16, 32));
        assertEquals(ModelProvider.LifecycleState.READY,
                harness.provider.warmup(harness.model).getLifecycleState());
        RecordingObserver observer = new RecordingObserver();
        ModelProvider.InferenceHandle handle = harness.provider.infer(
                request("bounded", true, 10_000),
                observer);
        harness.executor.drain();

        assertEquals(ModelProviderProfiles.ANDROID_LOCAL_DEVELOPMENT_ID,
                handle.getProviderId());
        assertEquals(2, observer.chunks.size());
        assertArrayEquals(bytes("firstsecond"), observer.joined());
        assertEquals(ModelProvider.TerminalState.COMPLETED, observer.terminal.getState());
        assertEquals(1, harness.provider.metrics().getCompletedCount());
        assertEquals(ModelProvider.Assurance.DEBUG_ONLY,
                harness.provider.descriptor().getAssurance());
        assertEquals(ModelProvider.FallbackClass.NEVER,
                harness.provider.descriptor().getFallbackClass());
        assertFalse(harness.provider.descriptor().isProductionEligible());
        assertFalse(harness.provider.descriptor().isHardwareBacked());
        assertFalse(harness.provider.snapshot().isHardwareAccessed());
    }

    @Test
    public void cancellationIsVisibleToEngineAndAcknowledged() {
        AtomicBoolean engineInvoked = new AtomicBoolean();
        Harness harness = harness((model, request, signal) -> {
            engineInvoked.set(true);
            return LocalModelProvider.EngineOutput.of(bytes("not-delivered"));
        }, LocalModelProvider.StreamLimits.defaults());
        harness.provider.warmup(harness.model);
        RecordingObserver observer = new RecordingObserver();
        harness.provider.infer(request("cancel", true, 10_000), observer);
        assertEquals(ModelProvider.CancelState.PENDING_PROVIDER_ACK,
                harness.provider.cancel("cancel", "unit test"));
        harness.executor.drain();

        assertFalse(engineInvoked.get());
        assertEquals(ModelProvider.TerminalState.CANCELLED, observer.terminal.getState());
        assertEquals(1, harness.provider.metrics().getCancelledCount());
        assertEquals(ModelProvider.CancelState.ALREADY_TERMINAL,
                harness.provider.cancel("cancel", "repeat"));
    }

    @Test
    public void deadlineIsCheckedBeforeAdmissionAndAfterEngine() {
        Harness expired = harness((model, request, signal) ->
                LocalModelProvider.EngineOutput.of(bytes("unused")),
                LocalModelProvider.StreamLimits.defaults());
        expired.provider.warmup(expired.model);
        expired.clock.set(10_000);
        expect(LocalModelProvider.DeadlineExceededException.class,
                () -> expired.provider.infer(
                        request("expired", false, 10_000),
                        new RecordingObserver()));

        Harness during = harness((model, request, signal) -> {
            assertFalse(signal.isDeadlineExceeded());
            return LocalModelProvider.EngineOutput.of(bytes("late"));
        }, LocalModelProvider.StreamLimits.defaults());
        during.provider.warmup(during.model);
        RecordingObserver observer = new RecordingObserver();
        during.provider.infer(request("during", true, 1_500), observer);
        during.executor.runNext();
        assertEquals(ModelProvider.TerminalState.COMPLETED, observer.terminal.getState());

        AtomicLong crossedClock = new AtomicLong(1_000);
        Harness crossed = harness((model, request, signal) -> {
            crossedClock.set(2_000);
            return LocalModelProvider.EngineOutput.of(bytes("late"));
        }, LocalModelProvider.StreamLimits.defaults(), crossedClock);
        crossed.provider.warmup(crossed.model);
        RecordingObserver crossedObserver = new RecordingObserver();
        crossed.provider.infer(request("crossed", true, 1_500), crossedObserver);
        crossed.executor.drain();
        assertTrue(crossedObserver.chunks.isEmpty());
        assertEquals(ModelProvider.TerminalState.DEADLINE_EXCEEDED,
                crossedObserver.terminal.getState());
    }

    @Test
    public void chunkCountAndByteLimitsFailClosed() {
        Harness chunks = harness((model, request, signal) -> LocalModelProvider.EngineOutput.of(
                bytes("a"), bytes("b"), bytes("c")),
                new LocalModelProvider.StreamLimits(2, 4, 8));
        chunks.provider.warmup(chunks.model);
        RecordingObserver chunkObserver = new RecordingObserver();
        chunks.provider.infer(request("too-many", true, 10_000), chunkObserver);
        chunks.executor.drain();
        assertEquals(ModelProvider.TerminalState.TERMINAL_FAILURE,
                chunkObserver.terminal.getState());
        assertEquals("LOCAL_OUTPUT_LIMIT_EXCEEDED",
                chunks.provider.lastFault().getFaultCode());

        Harness bytes = harness((model, request, signal) ->
                LocalModelProvider.EngineOutput.of(bytes("12345")),
                new LocalModelProvider.StreamLimits(2, 4, 8));
        bytes.provider.warmup(bytes.model);
        RecordingObserver byteObserver = new RecordingObserver();
        bytes.provider.infer(request("too-large", true, 10_000), byteObserver);
        bytes.executor.drain();
        assertEquals(ModelProvider.TerminalState.TERMINAL_FAILURE,
                byteObserver.terminal.getState());
        assertTrue(byteObserver.chunks.isEmpty());
    }

    @Test
    public void nonStreamingOutputIsMergedAndCloseCancelsQueuedWork() {
        Harness harness = harness((model, request, signal) -> LocalModelProvider.EngineOutput.of(
                bytes("one"), bytes("two")),
                LocalModelProvider.StreamLimits.defaults());
        harness.provider.warmup(harness.model);
        RecordingObserver merged = new RecordingObserver();
        harness.provider.infer(request("merged", false, 10_000), merged);
        harness.executor.drain();
        assertEquals(1, merged.chunks.size());
        assertArrayEquals(bytes("onetwo"), merged.joined());

        RecordingObserver closed = new RecordingObserver();
        harness.provider.infer(request("close", true, 10_000), closed);
        harness.provider.close();
        assertEquals(ModelProvider.TerminalState.CANCELLED, closed.terminal.getState());
        assertEquals(ModelProvider.LifecycleState.STOPPED,
                harness.provider.snapshot().getLifecycleState());
        expect(LocalModelProvider.ProviderUnavailableException.class,
                () -> harness.provider.warmup(harness.model));
    }

    @Test
    public void registryAndPolicyKeepLocalProviderOutOfProduction() {
        ModelProviderRegistry registry = ModelProviderRegistry.createForContractTest();
        assertEquals(1, registry.snapshot(1_000).getDevelopmentAvailableCount());
        registry.publishHealth(new ModelProviderRegistry.HealthReport(
                ModelProviderRegistry.ANDROID_LOCAL_DEVELOPMENT_ID,
                ModelProviderRegistry.HealthSource.LOCAL_DEVELOPMENT_RUNTIME,
                ModelProviderRegistry.HealthState.HEALTHY,
                1,
                900,
                1_500,
                DIGEST_A), 1_000);
        PolicyAwareModelRouter.RouteDecision development = PolicyAwareModelRouter.decide(
                modelRequest(),
                policy(PolicyAwareModelRouter.RouteMode.DEVELOPMENT),
                registry.snapshot(1_000),
                1_000);
        PolicyAwareModelRouter.RouteDecision production = PolicyAwareModelRouter.decide(
                modelRequest(),
                policy(PolicyAwareModelRouter.RouteMode.PRODUCTION),
                registry.snapshot(1_000),
                1_000);

        assertEquals(ModelProviderRegistry.ANDROID_LOCAL_DEVELOPMENT_ID,
                development.getPrimaryProviderId());
        assertEquals(PolicyAwareModelRouter.DecisionCode.NO_ELIGIBLE_PROVIDER,
                production.getCode());
        assertFalse(development.isProviderInvoked());
        assertFalse(development.isModelInvoked());
        assertFalse(development.isNpuAccessed());
        assertFalse(development.isHardwareAccessed());
    }

    private static Harness harness(
            LocalModelProvider.LocalInferenceEngine engine,
            LocalModelProvider.StreamLimits limits) {
        return harness(engine, limits, new AtomicLong(1_000));
    }

    private static Harness harness(
            LocalModelProvider.LocalInferenceEngine engine,
            LocalModelProvider.StreamLimits limits,
            AtomicLong clock) {
        ManualExecutor executor = new ManualExecutor();
        ModelProvider.ModelSpec model = new ModelProvider.ModelSpec(
                "central-intent-v0",
                "1",
                DIGEST_A);
        return new Harness(
                LocalModelProvider.createForDevelopment(
                        model,
                        engine,
                        executor,
                        clock::get,
                        limits),
                model,
                executor,
                clock);
    }

    private static ModelProvider.InferenceRequest request(
            String requestId,
            boolean streaming,
            long deadline) {
        return new ModelProvider.InferenceRequest(
                requestId,
                "central-intent-v0",
                DIGEST_B,
                deadline,
                streaming);
    }

    private static ModelContractV2.ModelRequest modelRequest() {
        return new ModelContractV2.ModelRequest(
                "request.local",
                ModelContractV2.Purpose.CONTEXT_SUMMARY,
                ModelContractV2.PrivacyClass.INTERNAL,
                new ModelContractV2.LatencyBudget(1_000),
                new ModelContractV2.TokenBudget(128, 128, 256),
                ModelContractV2.RequiredCapability.SUMMARIZATION,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                DIGEST_A,
                DIGEST_B);
    }

    private static PolicyAwareModelRouter.PolicySnapshot policy(
            PolicyAwareModelRouter.RouteMode mode) {
        return new PolicyAwareModelRouter.PolicySnapshot(
                mode,
                mode == PolicyAwareModelRouter.RouteMode.DEVELOPMENT
                        ? PolicyAwareModelRouter.NetworkPolicy.ALLOW_ANY
                        : PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                mode == PolicyAwareModelRouter.RouteMode.DEVELOPMENT
                        ? PolicyAwareModelRouter.NetworkState.UNMETERED
                        : PolicyAwareModelRouter.NetworkState.UNAVAILABLE,
                PolicyAwareModelRouter.ThermalState.NOMINAL,
                10,
                10_000,
                1,
                900,
                1_500,
                DIGEST_B);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void expect(
            Class<? extends RuntimeException> expected,
            Runnable operation) {
        try {
            operation.run();
            fail("expected " + expected.getSimpleName());
        } catch (RuntimeException exception) {
            assertTrue(expected.isInstance(exception));
        }
    }

    private static final class Harness {
        final LocalModelProvider provider;
        final ModelProvider.ModelSpec model;
        final ManualExecutor executor;
        final AtomicLong clock;

        Harness(
                LocalModelProvider provider,
                ModelProvider.ModelSpec model,
                ManualExecutor executor,
                AtomicLong clock) {
            this.provider = provider;
            this.model = model;
            this.executor = executor;
            this.clock = clock;
        }
    }

    private static final class ManualExecutor implements java.util.concurrent.Executor {
        private final Queue<Runnable> pending = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }

        void runNext() {
            pending.remove().run();
        }

        void drain() {
            while (!pending.isEmpty()) {
                runNext();
            }
        }
    }

    private static final class RecordingObserver implements ModelProvider.StreamObserver {
        final List<ModelProvider.StreamChunk> chunks = new ArrayList<>();
        ModelProvider.TerminalResult terminal;

        @Override
        public void onChunk(ModelProvider.StreamChunk chunk) {
            chunks.add(chunk);
        }

        @Override
        public void onTerminal(ModelProvider.TerminalResult result) {
            terminal = result;
        }

        byte[] joined() {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (ModelProvider.StreamChunk chunk : chunks) {
                output.writeBytes(chunk.getContent());
            }
            return output.toByteArray();
        }
    }
}
