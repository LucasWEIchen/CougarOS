package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
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
        assertTrue(request.message.contains("不能声称真实车辆已经执行"));
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
    public void developmentProfileUsesTheFixedAdbReverseEndpoint() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<OpenClawInferenceEngine.Request> captured = new AtomicReference<>();
        OpenClawInferenceEngine engine = new OpenClawInferenceEngine(
                OpenClawEndpointConfig.developmentWslAdbReverse(),
                () -> TEST_TOKEN,
                request -> {
                    captured.set(request);
                    return new OpenClawInferenceEngine.Result(
                            "{\"scenario_id\":\"scene.comfort.cold.v1\","
                                    + "\"reply\":\"正在为你调节座舱温度。\","
                                    + "\"actions\":[\"hvac.warm_cabin\"]}",
                            4,
                            false);
                },
                clock::get);
        engine.warmup(model());
        engine.registerScenarioPrompt(INPUT_DIGEST, "scene.comfort.cold.v1");

        engine.infer(model(), request(INPUT_DIGEST), neverCancelled());

        assertEquals("development_wsl_openclaw",
                captured.get().endpoint.getProfile());
        assertEquals("ws://127.0.0.1:18789/",
                captured.get().endpoint.getWebSocketUri().toString());
        assertEquals(4, captured.get().endpoint.getProtocolVersion());
        assertEquals(null, captured.get().imageAttachment);
    }

    @Test
    public void boundedImageAttachmentIsDigestBoundAndForwardedWithText() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<OpenClawInferenceEngine.Request> captured = new AtomicReference<>();
        OpenClawInferenceEngine engine = engine(clock, request -> {
            captured.set(request);
            return result(
                    "scene.comfort.cold.v1",
                    "正在结合座舱图像调节温度。",
                    "hvac.warm_cabin",
                    false);
        });
        byte[] png = minimalPng();
        engine.warmup(model());
        engine.registerScenarioPrompt(INPUT_DIGEST, "scene.comfort.cold.v1");
        engine.registerScenarioImageAttachment(
                INPUT_DIGEST, "image/png", "cabin.png", png);

        engine.infer(model(), request(INPUT_DIGEST), neverCancelled());

        OpenClawInferenceEngine.ImageAttachment image = captured.get().imageAttachment;
        assertEquals("image/png", image.mimeType);
        assertEquals("cabin.png", image.fileName);
        assertEquals(png.length, image.content.length);
        assertEquals(
                "1b56b50ac4e976f488f128cabdcdffb2fc9331d6974bb9968131a415d14ade24",
                image.sha256);
        assertTrue(captured.get().message.contains("汽车座舱"));
    }

    @Test
    public void smokingSpecialistUsesSameImageAndStrictResultContract() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<OpenClawInferenceEngine.Request> captured = new AtomicReference<>();
        OpenClawInferenceEngine engine = engine(clock, request -> {
            captured.set(request);
            return new OpenClawInferenceEngine.Result(
                    "{\"smoking_detected\":1,\"person_count\":1,"
                            + "\"location\":\"IMAGE_ROW_1_RIGHT\","
                            + "\"confidence\":0.88,"
                            + "\"description\":\"可见烟支靠近嘴部。\"}",
                    3,
                    false);
        });
        engine.warmup(model());
        engine.registerPrompt(CockpitModelPrompt.forSmokingDetection(
                INPUT_DIGEST,
                "检测吸烟",
                "agent.cabin.smoking-detection.v1",
                "只识别客观吸烟事实并输出五字段JSON。"));
        engine.registerScenarioImageAttachment(
                INPUT_DIGEST, "image/png", "smoking.png", minimalPng());

        LocalModelProvider.EngineOutput output = engine.infer(
                model(), request(INPUT_DIGEST), neverCancelled());

        assertTrue(captured.get().message.contains("专用Agent"));
        assertTrue(captured.get().message.contains("smoking-detection.v1"));
        assertEquals("image/png", captured.get().imageAttachment.mimeType);
        String canonical = new String(output.getChunks().get(0), StandardCharsets.UTF_8);
        assertTrue(canonical.contains("\\\"smoking_detected\\\":1"));
        assertTrue(canonical.contains("\"actions\":[\"assistant.respond\"]"));
    }

    @Test
    public void cancelledSmokingRequestConsumesImageWithoutCallingTransport() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicInteger transportCalls = new AtomicInteger();
        OpenClawInferenceEngine engine = engine(clock, request -> {
            transportCalls.incrementAndGet();
            return new OpenClawInferenceEngine.Result("{}", 3, false);
        });
        engine.warmup(model());
        engine.registerPrompt(CockpitModelPrompt.forSmokingDetection(
                INPUT_DIGEST,
                "检测吸烟",
                "agent.cabin.smoking-detection.v1",
                "只识别客观吸烟事实并输出五字段JSON。"));
        engine.registerScenarioImageAttachment(
                INPUT_DIGEST, "image/png", "smoking.png", minimalPng());

        assertThrows(IllegalStateException.class, () -> engine.infer(
                model(), request(INPUT_DIGEST), alwaysCancelled()));

        assertEquals(0, transportCalls.get());
        assertEquals(1, engine.snapshot().getFailureCount());
        byte[] replacement = minimalPng();
        replacement[replacement.length - 1] = 0x01;
        engine.registerScenarioImageAttachment(
                INPUT_DIGEST, "image/png", "smoking.png", replacement);
        engine.close();
    }

    @Test
    public void invalidImageMimeSignatureSizeAndDigestConflictFailClosed() {
        OpenClawInferenceEngine engine = engine(new AtomicLong(1_000L), request -> result(
                "scene.comfort.cold.v1", "unused", "hvac.warm_cabin", false));
        byte[] png = minimalPng();
        assertThrows(IllegalArgumentException.class, () ->
                engine.registerScenarioImageAttachment(
                        INPUT_DIGEST, "image/gif", "cabin.gif", png));
        assertThrows(IllegalArgumentException.class, () ->
                engine.registerScenarioImageAttachment(
                        INPUT_DIGEST, "image/jpeg", "cabin.jpg", png));
        assertThrows(IllegalArgumentException.class, () ->
                engine.registerScenarioImageAttachment(
                        INPUT_DIGEST, "image/png", "../cabin.png", png));
        assertThrows(IllegalArgumentException.class, () ->
                engine.registerScenarioImageAttachment(
                        INPUT_DIGEST,
                        "image/png",
                        "oversize.png",
                        new byte[OpenClawInferenceEngine.MAX_IMAGE_BYTES + 1]));

        engine.registerScenarioImageAttachment(
                INPUT_DIGEST, "image/png", "cabin.png", png);
        assertThrows(IllegalArgumentException.class, () ->
                engine.registerScenarioImageAttachment(
                        INPUT_DIGEST, "image/png", "different.png", png));
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
        assertEquals("ACTION_ALLOWLIST_REJECTED",
                untrusted.snapshot().getLastFailureCode());

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
    public void fatiguePromptCarriesCockpitContextAndRequiresHvacAndSeat() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<OpenClawInferenceEngine.Request> captured = new AtomicReference<>();
        OpenClawInferenceEngine engine = engine(clock, request -> {
            captured.set(request);
            return resultWithActions(
                    "scene.fatigue.assist.v1",
                    "我会调节通风并模拟座椅舒展。",
                    "hvac.ventilate",
                    "seat.recline");
        });
        engine.warmup(model());
        engine.registerScenarioPrompt(INPUT_DIGEST, "scene.fatigue.assist.v1");

        engine.infer(model(), request(INPUT_DIGEST), neverCancelled());

        assertTrue(captured.get().message.contains("environment=AUTOMOTIVE_COCKPIT"));
        assertTrue(captured.get().message.contains("occupant_role=DRIVER"));
        assertTrue(captured.get().message.contains("UI_SIMULATION_ONLY"));
        assertTrue(captured.get().message.contains("seat.recline,hvac.ventilate")
                || captured.get().message.contains("hvac.ventilate,seat.recline"));

        OpenClawInferenceEngine missingSeat = engine(clock, request -> result(
                "scene.fatigue.assist.v1",
                "只调节通风。",
                "hvac.ventilate",
                false));
        missingSeat.warmup(model());
        missingSeat.registerScenarioPrompt(INPUT_DIGEST, "scene.fatigue.assist.v1");
        assertThrows(IllegalStateException.class, () -> missingSeat.infer(
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

    @Test
    public void defaultEngineUsesTheFixedTargetCredential() {
        OpenClawEndpointConfig endpoint = OpenClawEndpointConfig
                .targetProductionTransitional();
        assertEquals("Iluvatar1!", endpoint.getEmbeddedToken());
        assertEquals("http://169.254.208.110:18789/chat?token=Iluvatar1!",
                endpoint.getControlUiUri().toString());
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

    private static byte[] minimalPng() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x00
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

    private static OpenClawInferenceEngine.Result resultWithActions(
            String scenarioId,
            String reply,
            String... actions) {
        return new OpenClawInferenceEngine.Result(
                "{\"scenario_id\":\"" + scenarioId
                        + "\",\"reply\":\"" + reply
                        + "\",\"actions\":[\""
                        + String.join("\",\"", actions) + "\"]}",
                3,
                false);
    }
}
