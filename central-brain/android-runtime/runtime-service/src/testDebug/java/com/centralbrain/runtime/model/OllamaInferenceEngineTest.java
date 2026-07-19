package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public final class OllamaInferenceEngineTest {
    private static final String MODEL = "qwen3.5:27b-optimized";
    private static final String INPUT_DIGEST = "b".repeat(64);

    @Test
    public void structuredScenarioResponseIsValidatedAndBounded() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<OllamaInferenceEngine.Request> captured = new AtomicReference<>();
        OllamaInferenceEngine engine = engine(clock, request -> {
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
        assertEquals("http://127.0.0.1:11434/api/chat",
                captured.get().uri.toString());
        assertTrue(requestJson.contains("\"stream\":false"));
        assertTrue(requestJson.contains("\"think\":false"));
        assertTrue(requestJson.contains("\"format\""));
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
        OllamaInferenceEngine unknownAction = engine(clock, request -> response(
                "scene.fatigue.assist.v1",
                "正在处理。",
                "vehicle.unknown"));
        unknownAction.warmup(model());
        unknownAction.registerScenarioPrompt(INPUT_DIGEST, "scene.fatigue.assist.v1");
        assertThrows(IllegalStateException.class, () -> unknownAction.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));
        assertEquals(1, unknownAction.snapshot().getFailureCount());

        OllamaInferenceEngine missing = engine(clock, request -> response(
                "scene.comfort.cold.v1",
                "unused",
                "hvac.warm_cabin"));
        missing.warmup(model());
        assertThrows(IllegalArgumentException.class, () -> missing.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));
    }

    @Test
    public void productionProfileCannotBeInjectedIntoDebugEngine() {
        assertThrows(IllegalArgumentException.class, () -> new OllamaInferenceEngine(
                OllamaEndpointConfig.productionLinkLocal("central-brain-model:v1"),
                request -> response(
                        "scene.comfort.cold.v1", "unused", "hvac.warm_cabin"),
                () -> 1_000L));
    }

    @Test
    public void fatigueRequestContainsCockpitContextAndRequiredActions() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<OllamaInferenceEngine.Request> captured = new AtomicReference<>();
        OllamaInferenceEngine engine = engine(clock, request -> {
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

        OllamaInferenceEngine missingSeat = engine(clock, request -> response(
                "scene.fatigue.assist.v1", "只调整通风。", "hvac.ventilate"));
        missingSeat.warmup(model());
        missingSeat.registerScenarioPrompt(INPUT_DIGEST, "scene.fatigue.assist.v1");
        assertThrows(IllegalStateException.class, () -> missingSeat.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));
    }

    @Test
    public void unknownFieldsAndMalformedUtf8FailClosed() {
        AtomicLong clock = new AtomicLong(1_000L);
        OllamaInferenceEngine unknownField = engine(clock, request -> {
            String content = "{\"scenario_id\":\"scene.comfort.cold.v1\","
                    + "\"reply\":\"bounded\","
                    + "\"actions\":[\"hvac.warm_cabin\"],\"debug\":true}";
            return envelope(content.getBytes(StandardCharsets.UTF_8));
        });
        unknownField.warmup(model());
        unknownField.registerScenarioPrompt(INPUT_DIGEST, "scene.comfort.cold.v1");
        assertThrows(IllegalStateException.class, () -> unknownField.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));

        OllamaInferenceEngine malformed = engine(clock, request ->
                new OllamaInferenceEngine.Response(
                        200,
                        new byte[] {'{', '"', 'x', '"', ':', '"', (byte) 0xc3, '"', '}'}));
        malformed.warmup(model());
        malformed.registerScenarioPrompt(INPUT_DIGEST, "scene.comfort.cold.v1");
        assertThrows(IllegalStateException.class, () -> malformed.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));
    }

    private static OllamaInferenceEngine engine(
            AtomicLong clock,
            OllamaInferenceEngine.Transport transport) {
        return new OllamaInferenceEngine(
                OllamaEndpointConfig.developmentWslAdbReverse(MODEL),
                transport,
                clock::get);
    }

    private static ModelProvider.ModelSpec model() {
        return new ModelProvider.ModelSpec("central-intent-v0", "ollama-debug-v1", "a".repeat(64));
    }

    private static ModelProvider.InferenceRequest request(String inputDigest) {
        return new ModelProvider.InferenceRequest(
                "ollama-test-request",
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

    private static OllamaInferenceEngine.Response response(
            String scenarioId,
            String reply,
            String... actions) {
        String content = "{\"scenario_id\":\"" + scenarioId
                + "\",\"reply\":\"" + reply
                + "\",\"actions\":[\"" + String.join("\",\"", actions) + "\"]}";
        String envelope = "{\"model\":\"" + MODEL
                + "\",\"done\":true,\"message\":{\"content\":"
                + quote(content) + "}}";
        return new OllamaInferenceEngine.Response(
                200, envelope.getBytes(StandardCharsets.UTF_8));
    }

    private static OllamaInferenceEngine.Response envelope(byte[] content) {
        String body = new String(content, StandardCharsets.UTF_8);
        String envelope = "{\"model\":\"" + MODEL
                + "\",\"done\":true,\"message\":{\"content\":"
                + quote(body) + "}}";
        return new OllamaInferenceEngine.Response(
                200, envelope.getBytes(StandardCharsets.UTF_8));
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }
}
