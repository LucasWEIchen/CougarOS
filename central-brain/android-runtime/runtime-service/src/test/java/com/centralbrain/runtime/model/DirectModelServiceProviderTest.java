package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class DirectModelServiceProviderTest {
    private static final String MODEL = "vendor/qwen3.6:27b";
    private static final String ARTIFACT_DIGEST = "a".repeat(64);
    private static final String SOURCE_INPUT_DIGEST = "e".repeat(64);
    private static final String SYSTEM = "你是车载 AIOS，只输出受控 JSON。";
    private static final String USER = "我有些疲惫";
    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";

    @Test
    public void providerOwnsLifecycleStreamingMetricsAndTerminalBinding() {
        OllamaChatProtocolAdapter adapter = adapter(
                "{\"model\":\"" + MODEL + "\",\"message\":{\"content\":\"{\\\"ok\\\":\"},"
                        + "\"done\":false}\n"
                        + "{\"model\":\"" + MODEL + "\",\"message\":{\"content\":\"true}\"},"
                        + "\"done\":true,\"done_reason\":\"stop\"}\n");
        RecordingObserver observer = new RecordingObserver();
        DirectModelServiceProvider provider = provider(
                adapter,
                Runnable::run,
                request -> resolved(request),
                (endpoint, model) -> true);

        ModelProvider.Snapshot ready = provider.warmup(model());
        assertEquals(ModelProvider.LifecycleState.READY, ready.getLifecycleState());
        assertEquals(ModelProvider.HealthState.HEALTHY, ready.getHealthState());
        assertEquals(ModelProvider.BackendKind.DIRECT_MODEL_SERVICE,
                provider.descriptor().getBackendKind());
        assertEquals(ModelProvider.Assurance.TARGET_INTEGRATION,
                provider.descriptor().getAssurance());
        assertFalse(provider.descriptor().isProductionEligible());

        ModelProvider.InferenceHandle handle = provider.infer(
                inference("request.provider.stream", true),
                observer);

        assertEquals(ModelProviderProfiles.DIRECT_MODEL_SERVICE_ID,
                handle.getProviderId());
        assertEquals(2, observer.chunks.size());
        assertEquals(1L, observer.chunks.get(0).getSequence());
        assertEquals(2L, observer.chunks.get(1).getSequence());
        assertEquals("{\"ok\":",
                new String(observer.chunks.get(0).getContent(), StandardCharsets.UTF_8));
        assertEquals("true}",
                new String(observer.chunks.get(1).getContent(), StandardCharsets.UTF_8));
        assertEquals(ModelProvider.TerminalState.COMPLETED,
                observer.terminal.getState());
        assertEquals(
                sha256("{\"ok\":true}".getBytes(StandardCharsets.UTF_8)),
                observer.terminal.getOutputDigest());
        assertEquals(1L, provider.metrics().getAcceptedCount());
        assertEquals(1L, provider.metrics().getCompletedCount());
        assertEquals(0, provider.snapshot().getActiveRequestCount());
        assertEquals("NONE", provider.lastFault().getFaultCode());
    }

    @Test
    public void nonStreamingRequestDeliversOneCanonicalChunk() {
        OllamaChatProtocolAdapter adapter = adapter(
                "{\"model\":\"" + MODEL
                        + "\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"},"
                        + "\"done\":true}\n");
        RecordingObserver observer = new RecordingObserver();
        DirectModelServiceProvider provider = provider(
                adapter,
                Runnable::run,
                request -> resolved(request),
                (endpoint, model) -> true);
        provider.warmup(model());

        provider.infer(inference("request.provider.single", false), observer);

        assertEquals(1, observer.chunks.size());
        assertEquals(
                "{\"ok\":true}",
                new String(observer.chunks.get(0).getContent(), StandardCharsets.UTF_8));
        assertEquals(ModelProvider.TerminalState.COMPLETED,
                observer.terminal.getState());
    }

    @Test
    public void availabilityAndResolvedInputFailuresRemainClosed() {
        DirectModelServiceProvider unavailable = provider(
                adapter("{}"),
                Runnable::run,
                request -> resolved(request),
                (endpoint, model) -> false);
        assertThrows(
                DirectModelServiceProvider.ProviderUnavailableException.class,
                () -> unavailable.warmup(model()));
        assertEquals(ModelProvider.LifecycleState.DEGRADED,
                unavailable.snapshot().getLifecycleState());
        assertEquals("DIRECT_MODEL_UNAVAILABLE",
                unavailable.lastFault().getFaultCode());

        AtomicInteger opens = new AtomicInteger();
        OllamaChatProtocolAdapter adapter = new OllamaChatProtocolAdapter(
                request -> {
                    opens.incrementAndGet();
                    return responseCall("{}");
                },
                () -> 1_000L);
        RecordingObserver observer = new RecordingObserver();
        DirectModelServiceProvider invalidInput = provider(
                adapter,
                Runnable::run,
                request -> new DirectModelServiceProvider.ResolvedInput(
                        "f".repeat(64),
                        protocolRequest(request, payload()),
                        payload()),
                (endpoint, model) -> true);
        invalidInput.warmup(model());

        invalidInput.infer(
                inference("request.provider.binding", true),
                observer);

        assertEquals(0, opens.get());
        assertEquals(ModelProvider.TerminalState.TERMINAL_FAILURE,
                observer.terminal.getState());
        assertEquals("DIRECT_MODEL_INPUT_REJECTED",
                observer.terminal.getDetailCode());
        assertEquals(1L, invalidInput.metrics().getFailureCount());
    }

    @Test
    public void cancellationBeforeExecutionDoesNotOpenNetwork() {
        AtomicInteger opens = new AtomicInteger();
        OllamaChatProtocolAdapter adapter = new OllamaChatProtocolAdapter(
                request -> {
                    opens.incrementAndGet();
                    return responseCall("{}");
                },
                () -> 1_000L);
        QueuedExecutor executor = new QueuedExecutor();
        RecordingObserver observer = new RecordingObserver();
        DirectModelServiceProvider provider = provider(
                adapter,
                executor,
                request -> resolved(request),
                (endpoint, model) -> true);
        provider.warmup(model());
        ModelProvider.InferenceRequest request =
                inference("request.provider.cancel", true);

        provider.infer(request, observer);
        assertEquals(
                ModelProvider.CancelState.PENDING_PROVIDER_ACK,
                provider.cancel(request.getRequestId(), "user cancelled"));
        executor.runPending();

        assertEquals(0, opens.get());
        assertTrue(observer.chunks.isEmpty());
        assertEquals(ModelProvider.TerminalState.CANCELLED,
                observer.terminal.getState());
        assertEquals(1L, provider.metrics().getCancelledCount());
        assertEquals(
                ModelProvider.CancelState.ALREADY_TERMINAL,
                provider.cancel(request.getRequestId(), "repeat"));
    }

    @Test
    public void structuredOutputMustBeAdmittedBeforeCompletion() {
        RecordingObserver observer = new RecordingObserver();
        DirectModelServiceProvider provider = new DirectModelServiceProvider(
                model(),
                endpoint(),
                request -> resolved(request),
                (endpoint, model) -> true,
                (request, resolved, content) -> {
                    throw new IllegalArgumentException("schema rejected");
                },
                adapter(
                        "{\"model\":\"" + MODEL
                                + "\",\"message\":{\"content\":\"{}\"},"
                                + "\"done\":true}\n"),
                Runnable::run,
                () -> 1_000L);
        provider.warmup(model());

        provider.infer(
                inference("request.provider.output", false),
                observer);

        assertTrue(observer.chunks.isEmpty());
        assertEquals(
                ModelProvider.TerminalState.TERMINAL_FAILURE,
                observer.terminal.getState());
        assertEquals(
                "DIRECT_MODEL_OUTPUT_REJECTED",
                observer.terminal.getDetailCode());
        assertEquals(1L, provider.metrics().getFailureCount());
    }

    private static DirectModelServiceProvider provider(
            OllamaChatProtocolAdapter adapter,
            Executor executor,
            DirectModelServiceProvider.InputResolver resolver,
            DirectModelServiceProvider.ModelAvailabilityProbe availabilityProbe) {
        return new DirectModelServiceProvider(
                model(),
                endpoint(),
                resolver,
                availabilityProbe,
                (request, resolved, content) -> {
                    if (content.length < 2
                            || content[0] != '{'
                            || content[content.length - 1] != '}') {
                        throw new IllegalArgumentException(
                                "structured output is not an object");
                    }
                },
                adapter,
                executor,
                () -> 1_000L);
    }

    private static OllamaChatProtocolAdapter adapter(String body) {
        return new OllamaChatProtocolAdapter(
                request -> responseCall(body),
                () -> 1_000L);
    }

    private static OllamaChatProtocolAdapter.Call responseCall(String body) {
        return new OllamaChatProtocolAdapter.Call() {
            private boolean cancelled;

            @Override
            public OllamaChatProtocolAdapter.HttpResponse execute() {
                return new OllamaChatProtocolAdapter.HttpResponse(
                        200,
                        new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                        () -> { });
            }

            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public void close() {
            }
        };
    }

    private static DirectModelServiceProvider.ResolvedInput resolved(
            ModelProvider.InferenceRequest request) {
        OllamaChatProtocolAdapter.Payload payload = payload();
        return new DirectModelServiceProvider.ResolvedInput(
                SOURCE_INPUT_DIGEST,
                protocolRequest(request, payload),
                payload);
    }

    private static DirectModelServiceContract.Request protocolRequest(
            ModelProvider.InferenceRequest request,
            OllamaChatProtocolAdapter.Payload payload) {
        return new DirectModelServiceContract.Request(
                endpoint(),
                request.getRequestId(),
                "session.driver.1",
                "idempotency." + request.getRequestId(),
                DirectModelServiceContract.Modality.TEXT,
                sha256(payload.getUserText().getBytes(StandardCharsets.UTF_8)),
                sha256(payload.getSystemPrompt().getBytes(StandardCharsets.UTF_8)),
                sha256(payload.getResponseSchemaJson().getBytes(StandardCharsets.UTF_8)),
                null,
                request.getDeadlineElapsedRealtimeMs());
    }

    private static OllamaChatProtocolAdapter.Payload payload() {
        return OllamaChatProtocolAdapter.Payload.text(SYSTEM, USER, SCHEMA);
    }

    private static ModelProvider.ModelSpec model() {
        return new ModelProvider.ModelSpec(MODEL, "direct-v1", ARTIFACT_DIGEST);
    }

    private static ModelProvider.InferenceRequest inference(
            String requestId,
            boolean streaming) {
        return new ModelProvider.InferenceRequest(
                requestId,
                MODEL,
                SOURCE_INPUT_DIGEST,
                10_000L,
                streaming);
    }

    private static DirectModelServiceContract.Endpoint endpoint() {
        return DirectModelServiceContract.productionOllama(MODEL);
    }

    private static String sha256(byte[] value) {
        try {
            byte[] encoded = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte item : encoded) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
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
    }

    private static final class QueuedExecutor implements Executor {
        private Runnable pending;

        @Override
        public void execute(Runnable command) {
            if (pending != null) {
                throw new IllegalStateException("executor already has pending work");
            }
            pending = command;
        }

        void runPending() {
            Runnable current = pending;
            pending = null;
            current.run();
        }
    }
}
