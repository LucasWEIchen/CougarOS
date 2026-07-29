package com.centralbrain.runtime.orchestration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.events.ProactiveConsentPolicy;
import com.centralbrain.runtime.model.DevelopmentModelInputStore;
import com.centralbrain.runtime.scenario.ScenarioCatalog;
import com.centralbrain.sdk.model.DevelopmentModelInput;
import com.centralbrain.sdk.model.DevelopmentModelInputContract;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class DebugDecisionCompositionBoundaryTest {
    @Test
    public void coldDecisionComposesDigestOnlyEvidenceAndClosesAuthorities() {
        DebugDecisionCompositionBoundary boundary = boundary();
        OrchestrationBackend.SessionDescriptor session = session(
                "cold-session", "scene.comfort.cold.v1");

        DebugDecisionCompositionBoundary.Evidence evidence = boundary.prepare(
                session, "scene.comfort.cold.v1", "b".repeat(64));
        DebugDecisionCompositionBoundary.Evidence replay = boundary.prepare(
                session, "scene.comfort.cold.v1", "b".repeat(64));

        assertEquals(evidence.getDigest(), replay.getDigest());
        assertEquals(3, evidence.getContextObservationCount());
        assertEquals(ProactiveConsentPolicy.AdmissionCode.NO_ACTIVE_GRANT,
                evidence.getConsentCode());
        assertEquals(2, evidence.getDeliveredEventCount());
        assertEquals(2, evidence.getLastEventSequence());
        assertFalse(evidence.isFatigueSourceStubbed());
        assertFalse(evidence.isAutoExecutionAuthorized());
        assertFalse(evidence.isProductionAuthority());
        assertFalse(evidence.isNetworkAccessed());
        assertFalse(evidence.isNpuAccessed());
        assertFalse(evidence.isHardwareAccessed());
        assertEquals(1, boundary.snapshot().getSuggestionCount());
        assertEquals(1, boundary.snapshot().getCompletedModelCount());
        assertEquals(0, boundary.snapshot().getActiveEventSubscriptionCount());

        assertFalse(boundary.complete(session, evidence).isReplayed());
        assertTrue(boundary.complete(session, evidence).isReplayed());
        assertEquals(1, boundary.snapshot().getCompletedCount());
    }

    @Test
    public void fatigueUsesExplicitStubSourceAndSessionConflictFailsClosed() {
        DebugDecisionCompositionBoundary boundary = boundary();
        OrchestrationBackend.SessionDescriptor session = session(
                "fatigue-session", "scene.fatigue.assist.v1");

        DebugDecisionCompositionBoundary.Evidence evidence = boundary.prepare(
                session, "scene.fatigue.assist.v1", "c".repeat(64));

        assertEquals(2, evidence.getContextObservationCount());
        assertTrue(evidence.isFatigueSourceStubbed());
        assertEquals(2, evidence.getDeliveredEventCount());
        assertEquals(1, boundary.snapshot().getSessionCount());
        assertThrows(IllegalArgumentException.class, () -> boundary.prepare(
                session, "scene.fatigue.assist.v1", "d".repeat(64)));
    }

    @Test
    public void freeformInputUsesAnIndependentTriggerAndConsumeOnceEnvelope() {
        DebugDecisionCompositionBoundary boundary = boundary();
        String sessionId = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
        OrchestrationBackend.SessionDescriptor session = session(
                sessionId, "scene.aios.freeform.v1");
        DevelopmentModelInput input = new DevelopmentModelInput();
        input.schemaVersion = DevelopmentModelInputContract.SCHEMA_VERSION;
        input.inputMode = DevelopmentModelInputContract.INPUT_TEXT_ONLY;
        input.sessionId = sessionId;
        input.scenarioId = "scene.aios.freeform.v1";
        input.inputText = "请把座舱调暖一点";
        input.imageMimeType = "";
        input.imageFileName = "";
        input.imageByteCount = 0L;
        input.imageSha256 = "";
        input.imageFd = null;
        DevelopmentModelInputStore.getInstance().stage(
                "a".repeat(64), input, new byte[0], 1_750_000_000_000L);

        DebugDecisionCompositionBoundary.Evidence evidence = boundary.prepare(
                session, "scene.aios.freeform.v1", "e".repeat(64));

        assertEquals(1, boundary.snapshot().getSuggestionCount());
        assertFalse(evidence.isNetworkAccessed());
        assertFalse(evidence.isImageConsumed());
    }

    private static DebugDecisionCompositionBoundary boundary() {
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicInteger leases = new AtomicInteger();
        return new DebugDecisionCompositionBoundary(
                catalog(),
                () -> 10_000L,
                () -> "test-subscription-" + subscriptions.incrementAndGet(),
                () -> "test-lease-" + leases.incrementAndGet());
    }

    private static OrchestrationBackend.SessionDescriptor session(
            String sessionId, String scenarioId) {
        return new OrchestrationBackend.SessionDescriptor(
                "a".repeat(64), sessionId, scenarioId, 1, 0, 0L, 1L);
    }

    private static ScenarioCatalog catalog() {
        Path assetsRoot = Path.of("src/main/assets/scenarios");
        Map<String, byte[]> assets = new LinkedHashMap<>();
        try {
            for (String name : new String[] {
                    "scene.aios.freeform.v1.json",
                    "scene.comfort.cold.v1.json",
                    "scene.fatigue.assist.v1.json"
            }) {
                assets.put(name, Files.readAllBytes(assetsRoot.resolve(name)));
            }
        } catch (IOException failure) {
            throw new IllegalStateException("built-in scenario fixture unavailable", failure);
        }
        return ScenarioCatalog.load(assets);
    }
}
