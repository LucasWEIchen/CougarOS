package com.centralbrain.runtime.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class DeterministicStubModelProviderTest {
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    @Test
    public void warmupAndStreamingInferenceAreDeterministic() {
        Harness first = harness();
        assertEquals(ModelProvider.LifecycleState.COLD,
                first.provider.snapshot().getLifecycleState());
        assertEquals(ModelProvider.LifecycleState.READY,
                first.provider.warmup(first.model).getLifecycleState());
        RecordingObserver firstObserver = new RecordingObserver();
        first.provider.infer(request("request-1", true, 10_000), firstObserver);
        first.executor.drain();

        assertEquals(2, firstObserver.chunks.size());
        assertEquals(1, firstObserver.chunks.get(0).getSequence());
        assertEquals(2, firstObserver.chunks.get(1).getSequence());
        assertEquals(
                java.util.Arrays.asList("chunk-1", "chunk-2", "terminal"),
                firstObserver.events);
        assertEquals(ModelProvider.TerminalState.COMPLETED,
                firstObserver.terminal.getState());
        assertEquals(sha256(firstObserver.joined()),
                firstObserver.terminal.getOutputDigest());
        assertEquals(1, first.provider.metrics().getAcceptedCount());
        assertEquals(1, first.provider.metrics().getCompletedCount());
        assertFalse(first.provider.descriptor().isHardwareBacked());

        Harness second = harness();
        second.provider.warmup(second.model);
        RecordingObserver secondObserver = new RecordingObserver();
        second.provider.infer(request("request-2", true, 10_000), secondObserver);
        second.executor.drain();
        assertArrayEquals(firstObserver.joined(), secondObserver.joined());
        assertEquals(firstObserver.terminal.getOutputDigest(),
                secondObserver.terminal.getOutputDigest());
    }

    @Test
    public void cancellationIsAcknowledgedAsynchronouslyAndReleasesSlot() {
        Harness harness = harness();
        harness.provider.warmup(harness.model);
        RecordingObserver observer = new RecordingObserver();
        harness.provider.infer(request("cancel-me", true, 10_000), observer);
        assertEquals(ModelProvider.CancelState.PENDING_PROVIDER_ACK,
                harness.provider.cancel("cancel-me", "test cancel"));
        assertEquals(1, harness.provider.snapshot().getActiveRequestCount());
        harness.executor.drain();

        assertEquals(ModelProvider.TerminalState.CANCELLED, observer.terminal.getState());
        assertEquals(0, harness.provider.snapshot().getActiveRequestCount());
        assertEquals(1, harness.provider.metrics().getCancelledCount());
        assertEquals(ModelProvider.CancelState.ALREADY_TERMINAL,
                harness.provider.cancel("cancel-me", "repeat cancel"));
    }

    @Test
    public void slotAndDeadlineAreFailClosed() {
        Harness harness = harness();
        harness.provider.warmup(harness.model);
        harness.provider.infer(
                request("active", false, 10_000),
                new RecordingObserver());
        expect(DeterministicStubModelProvider.ProviderBusyException.class,
                () -> harness.provider.infer(
                        request("second", false, 10_000),
                        new RecordingObserver()));
        harness.clock.set(10_000);
        expect(DeterministicStubModelProvider.DeadlineExceededException.class,
                () -> {
                    Harness expired = harness();
                    expired.clock.set(10_000);
                    expired.provider.warmup(expired.model);
                    expired.provider.infer(
                            request("expired", false, 10_000),
                            new RecordingObserver());
                });
    }

    @Test
    public void retryableAndIsolatedFaultsAreStructured() {
        Harness retryable = harness();
        retryable.provider.warmup(retryable.model);
        retryable.provider.setFaultModeForTest(
                DeterministicStubModelProvider.FaultMode
                        .RETRYABLE_BEFORE_FIRST_CHUNK);
        RecordingObserver retryableObserver = new RecordingObserver();
        retryable.provider.infer(
                request("retryable", true, 10_000),
                retryableObserver);
        retryable.executor.drain();
        assertEquals(ModelProvider.TerminalState.RETRYABLE_FAILURE,
                retryableObserver.terminal.getState());
        assertTrue(retryable.provider.lastFault().isRetryable());
        assertFalse(retryable.provider.lastFault().isIsolated());

        Harness isolated = harness();
        isolated.provider.warmup(isolated.model);
        isolated.provider.setFaultModeForTest(
                DeterministicStubModelProvider.FaultMode
                        .FAULT_ISOLATE_BEFORE_FIRST_CHUNK);
        RecordingObserver isolatedObserver = new RecordingObserver();
        isolated.provider.infer(
                request("isolated", true, 10_000),
                isolatedObserver);
        isolated.executor.drain();
        assertEquals(ModelProvider.TerminalState.TERMINAL_FAILURE,
                isolatedObserver.terminal.getState());
        assertTrue(isolated.provider.lastFault().isIsolated());
        assertEquals(ModelProvider.LifecycleState.FAULT_ISOLATED,
                isolated.provider.snapshot().getLifecycleState());
        expect(DeterministicStubModelProvider.ProviderUnavailableException.class,
                () -> isolated.provider.infer(
                        request("blocked", false, 10_000),
                        new RecordingObserver()));
    }

    @Test
    public void closeTerminatesActiveWorkAndBlocksReuse() {
        Harness harness = harness();
        harness.provider.warmup(harness.model);
        RecordingObserver observer = new RecordingObserver();
        harness.provider.infer(request("close-active", true, 10_000), observer);
        harness.provider.close();
        assertNotNull(observer.terminal);
        assertEquals(ModelProvider.TerminalState.CANCELLED, observer.terminal.getState());
        assertEquals(ModelProvider.LifecycleState.STOPPED,
                harness.provider.snapshot().getLifecycleState());
        expect(DeterministicStubModelProvider.ProviderUnavailableException.class,
                () -> harness.provider.warmup(harness.model));
    }

    private static Harness harness() {
        ManualExecutor executor = new ManualExecutor();
        AtomicLong clock = new AtomicLong(1_000);
        ModelProvider.ModelSpec model = new ModelProvider.ModelSpec(
                "central-intent-v0",
                "1",
                DIGEST_A);
        return new Harness(
                new DeterministicStubModelProvider(model, executor, clock::get),
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

    private static String sha256(byte[] value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder output = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                output.append(String.format("%02x", current & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
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
        final DeterministicStubModelProvider provider;
        final ModelProvider.ModelSpec model;
        final ManualExecutor executor;
        final AtomicLong clock;

        Harness(
                DeterministicStubModelProvider provider,
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
        final Queue<Runnable> pending = new ArrayDeque<>();

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
