package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public final class OpenClawInferenceEngineTest {
    private static final String INPUT_DIGEST = "b".repeat(64);
    private static final String TEST_TOKEN = "unit-test-token";

    @Test
    public void structuredScenarioResponseUsesFixedV3TargetAndIsCanonicalized() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<OpenClawInferenceEngine.Request> captured = new AtomicReference<>();
        OpenClawInferenceEngine engine = engine(clock, request -> {
            captured.set(request);
            clock.set(1_275L);
            return result(
                    "scene.comfort.cold.v1",
                    "正在为你调节座舱温度。",
                    "hvac.warm_cabin",
                    true);
        });
        ModelProvider.ModelSpec model = model();
        engine.warmup(model);
        engine.registerScenarioPrompt(INPUT_DIGEST, "scene.comfort.cold.v1");

        LocalModelProvider.EngineOutput output = engine.infer(
                model,
                request(INPUT_DIGEST),
                neverCancelled());

        OpenClawInferenceEngine.Request request = captured.get();
        assertEquals("ws://169.254.208.110:18789/",
                request.endpoint.getWebSocketUri().toString());
        assertEquals(TEST_TOKEN, request.token);
        assertTrue(request.sessionKey.startsWith("agent:main:cougaros-"));
        assertEquals(32 + "agent:main:cougaros-".length(),
                request.sessionKey.length());
        assertTrue(request.idempotencyKey.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertTrue(request.message.contains("只输出一个JSON对象"));
        assertTrue(request.message.contains("不得声明动作已在真实车辆上执行"));
        assertTrue(request.message.contains("hvac.warm_cabin"));
        assertFalse(request.message.contains(TEST_TOKEN));
        String canonical = new String(
                output.getChunks().get(0), StandardCharsets.UTF_8);
        assertEquals("{\"scenario_id\":\"scene.comfort.cold.v1\","
                        + "\"reply\":\"正在为你调节座舱温度。\","
                        + "\"actions\":[\"hvac.warm_cabin\"]}",
                canonical);
        assertEquals(1, engine.snapshot().getInvocationCount());
        assertEquals(1, engine.snapshot().getCompletedCount());
        assertEquals(1, engine.snapshot().getHistoryFallbackCount());
        assertEquals(275L, engine.snapshot().getLastLatencyMs());
        assertEquals(0, engine.snapshot().getPendingPromptCount());
    }

    @Test
    public void untrustedActionAndNonExactShapeFailClosed() {
        AtomicLong clock = new AtomicLong(1_000L);
        OpenClawInferenceEngine untrusted = engine(clock, request -> result(
                "scene.fatigue.assist.v1",
                "正在处理。",
                "vehicle.unlock",
                false));
        untrusted.warmup(model());
        untrusted.registerScenarioPrompt(INPUT_DIGEST, "scene.fatigue.assist.v1");
        assertThrows(IllegalStateException.class, () -> untrusted.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));
        assertEquals(1, untrusted.snapshot().getFailureCount());

        OpenClawInferenceEngine unknownField = engine(clock, request ->
                new OpenClawInferenceEngine.Result(
                        "{\"scenario_id\":\"scene.comfort.cold.v1\","
                                + "\"reply\":\"正在处理。\","
                                + "\"actions\":[\"hvac.warm_cabin\"],"
                                + "\"debug\":true}",
                        3,
                        false));
        unknownField.warmup(model());
        unknownField.registerScenarioPrompt(INPUT_DIGEST, "scene.comfort.cold.v1");
        assertThrows(IllegalStateException.class, () -> unknownField.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));
    }

    @Test
    public void missingCredentialAndProtocolMismatchAreAuditedAsFailures() {
        AtomicLong clock = new AtomicLong(1_000L);
        OpenClawInferenceEngine missing = new OpenClawInferenceEngine(
                OpenClawEndpointConfig.targetProductionTransitional(),
                () -> {
                    throw new IllegalStateException("OpenClaw credential is not provisioned");
                },
                request -> result(
                        "scene.comfort.cold.v1", "unused", "hvac.warm_cabin", false),
                clock::get);
        missing.warmup(model());
        missing.registerScenarioPrompt(INPUT_DIGEST, "scene.comfort.cold.v1");
        assertThrows(IllegalStateException.class, () -> missing.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));
        assertEquals(1, missing.snapshot().getInvocationCount());
        assertEquals(1, missing.snapshot().getFailureCount());

        OpenClawInferenceEngine mismatch = engine(clock, request ->
                new OpenClawInferenceEngine.Result(
                        "{\"scenario_id\":\"scene.comfort.cold.v1\","
                                + "\"reply\":\"unused\","
                                + "\"actions\":[\"hvac.warm_cabin\"]}",
                        2,
                        false));
        mismatch.warmup(model());
        mismatch.registerScenarioPrompt(INPUT_DIGEST, "scene.comfort.cold.v1");
        assertThrows(IllegalStateException.class, () -> mismatch.infer(
                model(), request(INPUT_DIGEST), neverCancelled()));
        assertEquals(1, mismatch.snapshot().getFailureCount());
    }

    private static OpenClawInferenceEngine engine(
            AtomicLong clock,
            OpenClawInferenceEngine.Transport transport) {
        return new OpenClawInferenceEngine(
                OpenClawEndpointConfig.targetProductionTransitional(),
                () -> TEST_TOKEN,
                transport,
                clock::get);
    }

    private static ModelProvider.ModelSpec model() {
        return new ModelProvider.ModelSpec(
                "central-intent-v0", "openclaw-ws-v3", "a".repeat(64));
    }

    private static ModelProvider.InferenceRequest request(String inputDigest) {
        return new ModelProvider.InferenceRequest(
                "openclaw-test-request",
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

    private static OpenClawInferenceEngine.Result result(
            String scenarioId,
            String reply,
            String action,
            boolean historyFallbackUsed) {
        return new OpenClawInferenceEngine.Result(
                "{\"scenario_id\":\"" + scenarioId
                        + "\",\"reply\":\"" + reply
                        + "\",\"actions\":[\"" + action + "\"]}",
                3,
                historyFallbackUsed);
    }
}
