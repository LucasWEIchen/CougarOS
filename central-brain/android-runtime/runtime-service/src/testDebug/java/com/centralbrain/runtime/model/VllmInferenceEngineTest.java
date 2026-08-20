package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public final class VllmInferenceEngineTest {
    private static final String MODEL = "Qwen3.5-9B-AWQ";
    private static final String INPUT_DIGEST = "b".repeat(64);

    @Test
    public void structuredScenarioResponseIsValidatedAndBounded() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<VllmInferenceEngine.Request> captured = new AtomicReference<>();
        VllmInferenceEngine engine = engine(clock, request -> {
            captured.set(request);
            clock.set(1_250L);
            return response(
                    "scene.comfort.cold.v1",
                    "正在为你调节座舱温度。",
                    "hvac.warm_cabin");
        });
        ModelProvider.ModelSpec model = model();
        engine.warmup(model);
        engine.registerScenarioPrompt(INPUT_DIGEST, "scene.comfort.cold.v1");

        LocalModelProvider.EngineOutput output = engine.infer(
                model,
                request(INPUT_DIGEST),
                neverCancelled());

        String requestJson = new String(captured.get().body, StandardCharsets.UTF_8);
        assertEquals("http://127.0.0.1:10030/v1/chat/completions",
                captured.get().uri.toString());
        assertTrue(requestJson.contains("\"stream\":false"));
        assertFalse(requestJson.contains("\"think\""));
        assertTrue(requestJson.contains("\"response_format\""));
        assertTrue(requestJson.contains("\"json_schema\""));
        assertTrue(requestJson.contains("\"max_tokens\":192"));
        assertTrue(requestJson.contains("scene.comfort.cold.v1"));
        assertTrue(requestJson.contains("键名scenario_id、reply、actions必须完全一致"));
        assertTrue(requestJson.contains("actions的每一项必须是可用动作中的字符串"));
        assertEquals(1, output.getChunks().size());
        String canonical = new String(output.getChunks().get(0), StandardCharsets.UTF_8);
        assertTrue(canonical.contains("\"scenario_id\":\"scene.comfort.cold.v1\""));
        assertTrue(canonical.contains("\"actions\":[\"hvac.warm_cabin\"]"));
        assertEquals(1, engine.snapshot().getInvocationCount());
        assertEquals(1, engine.snapshot().getCompletedCount());
        assertEquals(250L, engine.snapshot().getLastLatencyMs());
        assertEquals(0, engine.snapshot().getPendingPromptCount());
    }

    @Test
    public void unknownActionsAndMissingPromptMaterialFailClosed() {
        AtomicLong clock = new AtomicLong(1_000L);
        VllmInferenceEngine unknownAction = engine(clock, request -> response(
                "scene.fatigue.assist.v1",
                "正在处理。",
                "vehicle.unknown"));
        unknownAction.warmup(model());
        unknownAction.registerScenarioPrompt(INPUT_DIGEST, "scene.fatigue.assist.v1");
        assertThrows(IllegalStateException.class, () -> unknownAction.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));
        assertEquals(1, unknownAction.snapshot().getFailureCount());

        VllmInferenceEngine missing = engine(clock, request -> response(
                "scene.comfort.cold.v1",
                "unused",
                "hvac.warm_cabin"));
        missing.warmup(model());
        assertThrows(IllegalArgumentException.class, () -> missing.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));
    }

    @Test
    public void endpointAndModelCannotBeSelectedByCaller() {
        VllmEndpointConfig config = VllmEndpointConfig.ty1100EthernetViaAdbReverse();
        assertEquals("http://127.0.0.1:10030", config.getBaseUri().toString());
        assertEquals(MODEL, config.getModelName());
    }

    @Test
    public void fatigueRequestContainsCockpitContextAndRequiredActions() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<VllmInferenceEngine.Request> captured = new AtomicReference<>();
        VllmInferenceEngine engine = engine(clock, request -> {
            captured.set(request);
            return response(
                    "scene.fatigue.assist.v1",
                    "正在执行疲劳关怀仿真。",
                    "hvac.ventilate",
                    "seat.recline");
        });
        engine.warmup(model());
        engine.registerScenarioPrompt(INPUT_DIGEST, "scene.fatigue.assist.v1");

        engine.infer(model(), request(INPUT_DIGEST), neverCancelled());

        String requestJson = new String(captured.get().body, StandardCharsets.UTF_8);
        assertTrue(requestJson.contains("environment=AUTOMOTIVE_COCKPIT"));
        assertTrue(requestJson.contains("occupant_role=DRIVER"));
        assertTrue(requestJson.contains("UI_SIMULATION_ONLY"));

        VllmInferenceEngine missingSeat = engine(clock, request -> response(
                "scene.fatigue.assist.v1", "只调整通风。", "hvac.ventilate"));
        missingSeat.warmup(model());
        missingSeat.registerScenarioPrompt(INPUT_DIGEST, "scene.fatigue.assist.v1");
        assertThrows(IllegalStateException.class, () -> missingSeat.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));
    }

    @Test
    public void smokingSpecialistBindsImageAndValidatesFiveFieldPayload() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<VllmInferenceEngine.Request> captured = new AtomicReference<>();
        VllmInferenceEngine engine = engine(clock, request -> {
            captured.set(request);
            return envelope(("{\"smoking_detected\":1,\"person_count\":1,"
                    + "\"location\":\"IMAGE_ROW_1_RIGHT\",\"confidence\":0.88,"
                    + "\"description\":\"可见烟支靠近嘴部。\"}")
                    .getBytes(StandardCharsets.UTF_8));
        });
        engine.warmup(model());
        engine.registerPrompt(CockpitModelPrompt.forSmokingDetection(
                INPUT_DIGEST,
                "检测吸烟",
                "agent.cabin.smoking-detection.v1",
                "只识别客观吸烟事实并输出五字段JSON。"));
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        engine.registerScenarioImageAttachment(
                INPUT_DIGEST, "image/png", "fixture.png", png);

        LocalModelProvider.EngineOutput output = engine.infer(
                model(), request(INPUT_DIGEST), neverCancelled());

        String requestJson = new String(captured.get().body, StandardCharsets.UTF_8);
        assertTrue(requestJson.contains("\"type\":\"image_url\""));
        assertTrue(requestJson.contains(
                "\"max_tokens\":" + VllmInferenceEngine.SMOKING_MAX_OUTPUT_TOKENS));
        assertTrue(requestJson.contains(
                "data:image/png;base64,iVBORw0KGgo="));
        assertFalse(requestJson.contains("uniqueItems"));
        assertTrue(requestJson.contains("检测到吸烟行为。"));
        assertTrue(requestJson.contains("\"smoking_detected\""));
        assertTrue(requestJson.contains("agent.cabin.smoking-detection.v1"));
        String canonical = new String(output.getChunks().get(0), StandardCharsets.UTF_8);
        assertTrue(canonical.contains(
                "\"scenario_id\":\"scene.cabin.compliance.smoking.v1\""));
        assertTrue(canonical.contains("\\\"smoking_detected\\\":1"));
        assertTrue(canonical.contains("\"actions\":[\"assistant.respond\"]"));
    }

    @Test
    public void cancelledSmokingRequestConsumesImageWithoutCallingTransport() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicInteger transportCalls = new AtomicInteger();
        VllmInferenceEngine engine = engine(clock, request -> {
            transportCalls.incrementAndGet();
            return envelope("{}".getBytes(StandardCharsets.UTF_8));
        });
        engine.warmup(model());
        engine.registerPrompt(CockpitModelPrompt.forSmokingDetection(
                INPUT_DIGEST,
                "检测吸烟",
                "agent.cabin.smoking-detection.v1",
                "只识别客观吸烟事实并输出五字段JSON。"));
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        engine.registerScenarioImageAttachment(
                INPUT_DIGEST, "image/png", "fixture.png", png);

        assertThrows(IllegalStateException.class, () -> engine.infer(
                model(), request(INPUT_DIGEST), alwaysCancelled()));

        assertEquals(0, transportCalls.get());
        assertEquals(1, engine.snapshot().getFailureCount());
        byte[] replacement = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01
        };
        engine.registerScenarioImageAttachment(
                INPUT_DIGEST, "image/png", "fixture.png", replacement);
        engine.close();
    }

    @Test
    public void unknownFieldsAndMalformedUtf8FailClosed() {
        AtomicLong clock = new AtomicLong(1_000L);
        VllmInferenceEngine unknownField = engine(clock, request -> {
            String content = "{\"scenario_id\":\"scene.comfort.cold.v1\","
                    + "\"reply\":\"bounded\","
                    + "\"actions\":[\"hvac.warm_cabin\"],\"debug\":true}";
            return envelope(content.getBytes(StandardCharsets.UTF_8));
        });
        unknownField.warmup(model());
        unknownField.registerScenarioPrompt(INPUT_DIGEST, "scene.comfort.cold.v1");
        assertThrows(IllegalStateException.class, () -> unknownField.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));

        VllmInferenceEngine malformed = engine(clock, request ->
                new VllmInferenceEngine.Response(
                        200,
                        new byte[] {'{', '"', 'x', '"', ':', '"', (byte) 0xc3, '"', '}'}));
        malformed.warmup(model());
        malformed.registerScenarioPrompt(INPUT_DIGEST, "scene.comfort.cold.v1");
        assertThrows(IllegalStateException.class, () -> malformed.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));
    }

    private static VllmInferenceEngine engine(
            AtomicLong clock,
            VllmInferenceEngine.Transport transport) {
        return new VllmInferenceEngine(
                VllmEndpointConfig.ty1100EthernetViaAdbReverse(),
                transport,
                clock::get);
    }

    private static ModelProvider.ModelSpec model() {
        return new ModelProvider.ModelSpec("central-intent-v0", "vllm-debug-v1", "a".repeat(64));
    }

    private static ModelProvider.InferenceRequest request(String inputDigest) {
        return new ModelProvider.InferenceRequest(
                "vllm-test-request",
                "central-intent-v0",
                inputDigest,
                10_000L,
                true);
    }

    private static LocalModelProvider.CancellationSignal neverCancelled() {
        return new LocalModelProvider.CancellationSignal() {
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

    private static LocalModelProvider.CancellationSignal alwaysCancelled() {
        return new LocalModelProvider.CancellationSignal() {
            @Override
            public boolean isCancellationRequested() {
                return true;
            }

            @Override
            public boolean isDeadlineExceeded() {
                return false;
            }
        };
    }

    private static VllmInferenceEngine.Response response(
            String scenarioId,
            String reply,
            String... actions) {
        String content = "{\"scenario_id\":\"" + scenarioId
                + "\",\"reply\":\"" + reply
                + "\",\"actions\":[\"" + String.join("\",\"", actions) + "\"]}";
        String envelope = openAiEnvelope(content);
        return new VllmInferenceEngine.Response(
                200, envelope.getBytes(StandardCharsets.UTF_8));
    }

    private static VllmInferenceEngine.Response envelope(byte[] content) {
        String body = new String(content, StandardCharsets.UTF_8);
        String envelope = openAiEnvelope(body);
        return new VllmInferenceEngine.Response(
                200, envelope.getBytes(StandardCharsets.UTF_8));
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }

    private static String openAiEnvelope(String content) {
        return "{\"model\":\"" + MODEL
                + "\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                + "\"content\":" + quote(content)
                + "},\"finish_reason\":\"stop\"}]}";
    }
}
