package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public final class OllamaChatProtocolAdapterTest {
    private static final String MODEL = "vendor/qwen3.6:27b";
    private static final String SYSTEM = "你是车载座舱 AIOS，只能返回受控 JSON。";
    private static final String USER = "我有些疲惫";
    private static final String SCHEMA =
            "{\"type\":\"object\",\"additionalProperties\":false,"
                    + "\"properties\":{\"scenarioId\":{\"type\":\"string\"}},"
                    + "\"required\":[\"scenarioId\"]}";

    @Test
    public void textStreamBuildsBoundedRequestAndReturnsStructuredContent() {
        AtomicReference<OllamaChatProtocolAdapter.HttpRequest> captured =
                new AtomicReference<>();
        FakeCall call = FakeCall.response(
                concat(
                        line("{\"model\":\"" + MODEL
                                + "\",\"message\":{\"role\":\"assistant\","
                                + "\"content\":\"{\\\"scenarioId\\\":\"},\"done\":false}"),
                        line("{\"model\":\"" + MODEL
                                + "\",\"message\":{\"role\":\"assistant\","
                                + "\"content\":\"\\\"scene.fatigue.assist.v1\\\"}\"},"
                                + "\"done\":true,\"done_reason\":\"stop\"}")));
        OllamaChatProtocolAdapter adapter = adapter(request -> {
            captured.set(request);
            return call;
        });
        OllamaChatProtocolAdapter.Payload payload =
                OllamaChatProtocolAdapter.Payload.text(SYSTEM, USER, SCHEMA);
        RecordingObserver observer = new RecordingObserver();

        OllamaChatProtocolAdapter.Result result = adapter.execute(
                endpoint(),
                request(payload, null, "request.direct.text"),
                payload,
                observer,
                neverCancelled());

        String requestJson =
                new String(captured.get().getBody(), StandardCharsets.UTF_8);
        assertEquals(
                "http://169.254.208.110:11434/api/chat",
                captured.get().getUri().toString());
        assertTrue(requestJson.contains("\"model\":\"" + MODEL + "\""));
        assertTrue(requestJson.contains("\"stream\":true"));
        assertTrue(requestJson.contains("\"think\":false"));
        assertTrue(requestJson.contains("\"role\":\"system\""));
        assertTrue(requestJson.contains("\"role\":\"user\""));
        assertTrue(requestJson.contains("\"format\""));
        assertFalse(requestJson.contains("\"tools\""));
        assertEquals(
                "{\"scenarioId\":\"scene.fatigue.assist.v1\"}",
                new String(result.getStructuredContent(), StandardCharsets.UTF_8));
        assertEquals("stop", result.getDoneReason());
        assertFalse(result.grantsToolAuthority());
        assertFalse(result.grantsEffectAuthority());
        assertEquals(
                List.of(
                        DirectModelServiceContract.Stage.INPUT_VALIDATED,
                        DirectModelServiceContract.Stage.NETWORK_CONNECTING,
                        DirectModelServiceContract.Stage.REQUEST_SENT,
                        DirectModelServiceContract.Stage.STREAMING,
                        DirectModelServiceContract.Stage.TERMINAL),
                observer.stages);
        assertEquals(2, observer.deltas.size());
        assertEquals(0, adapter.activeRequestCount());
    }

    @Test
    public void imagePayloadIsBoundToDescriptorAndEncodedInUserMessage() {
        byte[] image = new byte[] {1, 2, 3, 4, 5};
        AtomicReference<OllamaChatProtocolAdapter.HttpRequest> captured =
                new AtomicReference<>();
        OllamaChatProtocolAdapter adapter = adapter(request -> {
            captured.set(request);
            return FakeCall.response(line(
                    "{\"model\":\"" + MODEL + "\","
                            + "\"message\":{\"role\":\"assistant\","
                            + "\"content\":\"{\\\"scenarioId\\\":\\\"scene.cabin\\\"}\"},"
                            + "\"done\":true}"));
        });
        OllamaChatProtocolAdapter.Payload payload =
                OllamaChatProtocolAdapter.Payload.textImage(
                        SYSTEM,
                        "处理一下",
                        SCHEMA,
                        "image/png",
                        image);
        DirectModelServiceContract.ImageDescriptor descriptor =
                new DirectModelServiceContract.ImageDescriptor(
                        "image/png",
                        image.length,
                        sha256(image));

        adapter.execute(
                endpoint(),
                request(payload, descriptor, "request.direct.image"),
                payload,
                new RecordingObserver(),
                neverCancelled());

        String requestJson =
                new String(captured.get().getBody(), StandardCharsets.UTF_8);
        assertTrue(requestJson.contains(
                "\"images\":[\"" + Base64.getEncoder().encodeToString(image) + "\"]"));
        assertEquals(0, adapter.activeRequestCount());
    }

    @Test
    public void digestModalityAndSchemaViolationsFailBeforeNetwork() {
        AtomicInteger opens = new AtomicInteger();
        OllamaChatProtocolAdapter adapter = adapter(request -> {
            opens.incrementAndGet();
            return FakeCall.response(new byte[0]);
        });
        OllamaChatProtocolAdapter.Payload payload =
                OllamaChatProtocolAdapter.Payload.text(SYSTEM, USER, SCHEMA);
        DirectModelServiceContract.Request wrongDigest =
                new DirectModelServiceContract.Request(
                        endpoint(),
                        "request.direct.binding",
                        "session.driver.1",
                        "idempotency.direct.binding",
                        DirectModelServiceContract.Modality.TEXT,
                        "f".repeat(64),
                        sha256(SYSTEM.getBytes(StandardCharsets.UTF_8)),
                        sha256(SCHEMA.getBytes(StandardCharsets.UTF_8)),
                        null,
                        10_000);

        assertFailure(
                OllamaChatProtocolAdapter.FailureCode.REQUEST_BINDING_REJECTED,
                () -> adapter.execute(
                        endpoint(),
                        wrongDigest,
                        payload,
                        new RecordingObserver(),
                        neverCancelled()));
        assertFailure(
                OllamaChatProtocolAdapter.FailureCode.SCHEMA_REJECTED,
                () -> OllamaChatProtocolAdapter.Payload.text(
                        SYSTEM,
                        USER,
                        "[]"));
        assertEquals(0, opens.get());
    }

    @Test
    public void malformedWrongModelAndNonTerminalStreamsFailClosed() {
        OllamaChatProtocolAdapter.Payload payload =
                OllamaChatProtocolAdapter.Payload.text(SYSTEM, USER, SCHEMA);

        assertFailure(
                OllamaChatProtocolAdapter.FailureCode.MODEL_IDENTITY_REJECTED,
                () -> adapter(FakeCall.transport(line(
                        "{\"model\":\"other:model\",\"message\":{\"content\":\"{}\"},"
                                + "\"done\":true}"))).execute(
                        endpoint(),
                        request(payload, null, "request.direct.model"),
                        payload,
                        new RecordingObserver(),
                        neverCancelled()));

        assertFailure(
                OllamaChatProtocolAdapter.FailureCode.NON_TERMINAL_RESPONSE,
                () -> adapter(FakeCall.transport(line(
                        "{\"model\":\"" + MODEL + "\","
                                + "\"message\":{\"content\":\"{}\"},\"done\":false}")))
                        .execute(
                                endpoint(),
                                request(payload, null, "request.direct.terminal"),
                                payload,
                                new RecordingObserver(),
                                neverCancelled()));

        assertFailure(
                OllamaChatProtocolAdapter.FailureCode.STREAM_PARSE_REJECTED,
                () -> adapter(FakeCall.transport(new byte[] {
                        '{', '"', 'x', '"', ':', '"', (byte) 0xc3, '"', '}'
                })).execute(
                        endpoint(),
                        request(payload, null, "request.direct.utf8"),
                        payload,
                        new RecordingObserver(),
                        neverCancelled()));
    }

    @Test
    public void activeRequestCanCancelItsUnderlyingConnection() throws Exception {
        BlockingCall call = new BlockingCall();
        OllamaChatProtocolAdapter adapter = adapter(request -> call);
        OllamaChatProtocolAdapter.Payload payload =
                OllamaChatProtocolAdapter.Payload.text(SYSTEM, USER, SCHEMA);
        DirectModelServiceContract.Request request =
                request(payload, null, "request.direct.cancel");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<OllamaChatProtocolAdapter.Result> future = executor.submit(() ->
                    adapter.execute(
                            endpoint(),
                            request,
                            payload,
                            new RecordingObserver(),
                            neverCancelled()));
            assertTrue(call.started.await(2, TimeUnit.SECONDS));
            assertEquals(1, adapter.activeRequestCount());
            assertTrue(adapter.cancel(request.getRequestId()));

            ExecutionException failure =
                    assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
            assertEquals(
                    OllamaChatProtocolAdapter.FailureCode.CANCELLED,
                    ((OllamaChatProtocolAdapter.AdapterException) failure.getCause())
                            .getFailureCode());
            assertTrue(call.isCancelled());
            assertEquals(0, adapter.activeRequestCount());
        } finally {
            executor.shutdownNow();
        }
    }

    private static OllamaChatProtocolAdapter adapter(
            OllamaChatProtocolAdapter.Transport transport) {
        return new OllamaChatProtocolAdapter(transport, () -> 1_000L);
    }

    private static DirectModelServiceContract.Endpoint endpoint() {
        return DirectModelServiceContract.productionOllama(MODEL);
    }

    private static DirectModelServiceContract.Request request(
            OllamaChatProtocolAdapter.Payload payload,
            DirectModelServiceContract.ImageDescriptor image,
            String requestId) {
        return new DirectModelServiceContract.Request(
                endpoint(),
                requestId,
                "session.driver.1",
                "idempotency." + requestId,
                image == null
                        ? DirectModelServiceContract.Modality.TEXT
                        : DirectModelServiceContract.Modality.TEXT_IMAGE,
                sha256(payload.getUserText().getBytes(StandardCharsets.UTF_8)),
                sha256(payload.getSystemPrompt().getBytes(StandardCharsets.UTF_8)),
                sha256(payload.getResponseSchemaJson().getBytes(StandardCharsets.UTF_8)),
                image,
                10_000);
    }

    private static OllamaChatProtocolAdapter.CancellationSignal neverCancelled() {
        return new OllamaChatProtocolAdapter.CancellationSignal() {
            @Override
            public boolean isCancellationRequested() {
                return false;
            }

            @Override
            public boolean isDeadlineExceeded() {
                return false;
            }
        };
    }

    private static byte[] line(String value) {
        return (value + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
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

    private static void assertFailure(
            OllamaChatProtocolAdapter.FailureCode expected,
            ThrowingRunnable runnable) {
        OllamaChatProtocolAdapter.AdapterException failure =
                assertThrows(
                        OllamaChatProtocolAdapter.AdapterException.class,
                        runnable::run);
        assertEquals(expected, failure.getFailureCode());
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class RecordingObserver
            implements OllamaChatProtocolAdapter.StreamObserver {
        final List<DirectModelServiceContract.Stage> stages = new ArrayList<>();
        final List<String> deltas = new ArrayList<>();

        @Override
        public void onStage(DirectModelServiceContract.Stage stage) {
            stages.add(stage);
        }

        @Override
        public void onTextDelta(String textDelta) {
            deltas.add(textDelta);
        }
    }

    private static final class FakeCall implements OllamaChatProtocolAdapter.Call {
        private final byte[] body;
        private boolean cancelled;

        private FakeCall(byte[] body) {
            this.body = body.clone();
        }

        static FakeCall response(byte[] body) {
            return new FakeCall(body);
        }

        static OllamaChatProtocolAdapter.Transport transport(byte[] body) {
            return request -> response(body);
        }

        @Override
        public OllamaChatProtocolAdapter.HttpResponse execute() {
            return new OllamaChatProtocolAdapter.HttpResponse(
                    200,
                    new ByteArrayInputStream(body),
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
    }

    private static final class BlockingCall implements OllamaChatProtocolAdapter.Call {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch cancelledLatch = new CountDownLatch(1);
        private volatile boolean cancelled;

        @Override
        public OllamaChatProtocolAdapter.HttpResponse execute() throws IOException {
            started.countDown();
            try {
                if (!cancelledLatch.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("cancel timed out");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", failure);
            }
            throw new IOException("cancelled");
        }

        @Override
        public void cancel() {
            cancelled = true;
            cancelledLatch.countDown();
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void close() {
        }
    }
}
