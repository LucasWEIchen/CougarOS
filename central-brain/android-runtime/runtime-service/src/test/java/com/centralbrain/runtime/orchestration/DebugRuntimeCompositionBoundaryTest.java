package com.centralbrain.runtime.orchestration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public final class DebugRuntimeCompositionBoundaryTest {
    @Test
    public void coldScenarioComposesAndCleansDigestOnlyState() {
        AtomicInteger ids = new AtomicInteger();
        DebugRuntimeCompositionBoundary boundary =
                new DebugRuntimeCompositionBoundary(
                        () -> 10_000L,
                        () -> "test-" + ids.incrementAndGet());
        OrchestrationBackend.SessionDescriptor session = session(
                "cold-session", "scene.comfort.cold.v1");

        DebugRuntimeCompositionBoundary.Evidence evidence = boundary.prepare(
                session, "scene.comfort.cold.v1", "b".repeat(64));
        DebugRuntimeCompositionBoundary.Evidence replay = boundary.prepare(
                session, "scene.comfort.cold.v1", "b".repeat(64));

        assertEquals("cabin.precondition", evidence.getSkillId());
        assertEquals(evidence.getDigest(), replay.getDigest());
        assertEquals(12, evidence.getAllocatedTokens());
        assertEquals(192, evidence.getAllocatedBytes());
        assertEquals(64, evidence.getWorkingMemoryBytes());
        assertFalse(evidence.isSkillDispatchEnabled());
        assertFalse(evidence.isProfileMemoryWritten());
        assertFalse(evidence.isEpisodicMemoryWritten());
        assertFalse(evidence.isProductionAuthority());
        assertEquals(1, boundary.snapshot().getActiveSkillCount());
        assertEquals(1, boundary.snapshot().getActiveWorkingMemoryItemCount());

        DebugRuntimeCompositionBoundary.Completion completion =
                boundary.complete(session, evidence);
        assertFalse(completion.isReplayed());
        assertEquals(1, completion.getCleanedItems());
        assertEquals(64, completion.getCleanedBytes());
        assertEquals(1, completion.getCleanedTokens());
        assertTrue(boundary.complete(session, evidence).isReplayed());
        assertEquals(0, boundary.snapshot().getActiveSkillCount());
        assertEquals(0, boundary.snapshot().getActiveWorkingMemoryItemCount());
    }

    @Test
    public void fatigueScenarioUsesNapSkillAndConflictsFailClosed() {
        AtomicInteger ids = new AtomicInteger();
        DebugRuntimeCompositionBoundary boundary =
                new DebugRuntimeCompositionBoundary(
                        () -> 20_000L,
                        () -> "test-" + ids.incrementAndGet());
        OrchestrationBackend.SessionDescriptor session = session(
                "fatigue-session", "scene.fatigue.assist.v1");

        DebugRuntimeCompositionBoundary.Evidence evidence = boundary.prepare(
                session, "scene.fatigue.assist.v1", "c".repeat(64));

        assertEquals("cabin.scene.nap", evidence.getSkillId());
        assertThrows(
                IllegalArgumentException.class,
                () -> boundary.prepare(
                        session, "scene.fatigue.assist.v1", "d".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> boundary.prepare(
                        session("unknown-session", "scene.unknown.v1"),
                        "scene.unknown.v1",
                        "e".repeat(64)));
    }

    private static OrchestrationBackend.SessionDescriptor session(
            String sessionId, String scenarioId) {
        return new OrchestrationBackend.SessionDescriptor(
                "a".repeat(64), sessionId, scenarioId, 1, 0, 0L, 1L);
    }
}
