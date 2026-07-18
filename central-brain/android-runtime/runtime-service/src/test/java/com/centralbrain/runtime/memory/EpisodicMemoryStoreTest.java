package com.centralbrain.runtime.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class EpisodicMemoryStoreTest {
    private static final String OWNER_A = "a".repeat(64);
    private static final String OWNER_B = "b".repeat(64);
    private static final String OWNER_C = "c".repeat(64);
    private static final String CATALOG_DIGEST = "d".repeat(64);

    @Test
    public void storesOnlyTypedScenarioSummaryAndIsolatesOwners() {
        Harness harness = harness(limits(4, 3, 3, 100, 100), true, true, true);
        EpisodicMemoryStore.StoreResult stored = harness.store.store(
                request(OWNER_A, "episode-a", "fatigue-care", 80, 2, 2));

        assertEquals(EpisodicMemoryStore.StoreOutcome.STORED, stored.getOutcome());
        assertEquals("episode-a", stored.getRecord().getEpisodeId());
        assertEquals("fatigue-care", stored.getRecord().getScenarioId());
        assertEquals(EpisodicMemoryStore.TriggerKind.EXPLICIT_USER_INTENT,
                stored.getRecord().getTriggerKind());
        assertEquals(EpisodicMemoryStore.ResultKind.SUCCEEDED,
                stored.getRecord().getResultKind());
        assertEquals(EpisodicMemoryStore.OutcomeCode.COMPLETED,
                stored.getRecord().getOutcomeCode());
        assertEquals(2, stored.getRecord().getPlannedActionCount());
        assertEquals(2, stored.getRecord().getCompletedActionCount());
        assertTrue(harness.store.readOwner(OWNER_B, 3, readEvidence(OWNER_B))
                .getRecords().isEmpty());

        List<EpisodicMemoryStore.RecordSnapshot> records =
                harness.store.readOwner(OWNER_A, 3, readEvidence(OWNER_A)).getRecords();
        assertEquals(1, records.size());
        try {
            records.clear();
            fail("Episodic Memory reads must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
        assertFalse(harness.store.isRawContinuousSignalAccepted());
        assertFalse(harness.store.isRawContinuousSignalRetained());
        assertFalse(harness.store.isArbitraryPayloadAccepted());
        assertFalse(harness.store.isContentLoggingEnabled());
        assertFalse(harness.store.isPersistentStorageWired());
        assertFalse(harness.store.isRuntimeWired());
        assertFalse(harness.store.isModelContextPublicationEnabled());
        assertFalse(harness.store.isHardwareAccessed());
    }

    @Test
    public void catalogAndStoragePolicyFailClosed() {
        Harness catalogDenied = harness(limits(2, 2, 2, 100, 100), false, true, true);
        assertEquals(EpisodicMemoryStore.StoreOutcome.SCENARIO_NOT_ALLOWED,
                catalogDenied.store.store(
                        request(OWNER_A, "episode-a", "unknown", 80, 1, 1)).getOutcome());

        Harness policyDenied = harness(limits(2, 2, 2, 100, 100), true, false, true);
        assertEquals(EpisodicMemoryStore.StoreOutcome.POLICY_DENIED,
                policyDenied.store.store(
                        request(OWNER_A, "episode-a", "fatigue-care", 80, 1, 1))
                        .getOutcome());
        Harness expiredPolicy = harness(limits(2, 2, 2, 100, 100), true, true, true);
        EpisodicMemoryStore.RecordRequest expired = request(
                OWNER_A,
                "episode-expired",
                "fatigue-care",
                80,
                1,
                1,
                new EpisodicMemoryStore.StoragePolicyEvidence(
                        OWNER_A, "episode-expired", "policy-expired", 800, 1_000));
        assertEquals(EpisodicMemoryStore.StoreOutcome.POLICY_DENIED,
                expiredPolicy.store.store(expired).getOutcome());
        assertEquals(0, expiredPolicy.store.snapshot().getActiveRecordCount());

        EpisodicMemoryStore throwing = EpisodicMemoryStore.createForContractTest(
                limits(2, 2, 2, 100, 100),
                () -> 1_000L,
                reference -> {
                    throw new IllegalStateException("catalog unavailable");
                },
                (evidence, reference, now) -> true,
                (evidence, now) -> true,
                (evidence, now) -> true);
        assertEquals(EpisodicMemoryStore.StoreOutcome.SCENARIO_NOT_ALLOWED,
                throwing.store(request(
                        OWNER_A, "episode-a", "fatigue-care", 80, 1, 1)).getOutcome());
    }

    @Test
    public void replayIsIdempotentAndConflictDoesNotOverwrite() {
        Harness harness = harness(limits(2, 2, 2, 100, 100), true, true, true);
        EpisodicMemoryStore.RecordRequest first =
                request(OWNER_A, "episode-a", "fatigue-care", 80, 2, 2);
        assertEquals(EpisodicMemoryStore.StoreOutcome.STORED,
                harness.store.store(first).getOutcome());
        assertEquals(EpisodicMemoryStore.StoreOutcome.REPLAYED,
                harness.store.store(first).getOutcome());
        assertEquals(EpisodicMemoryStore.StoreOutcome.EPISODE_CONFLICT,
                harness.store.store(
                        request(OWNER_A, "episode-a", "fatigue-care", 80, 2, 1))
                        .getOutcome());

        EpisodicMemoryStore.Snapshot snapshot = harness.store.snapshot();
        assertEquals(1, snapshot.getActiveRecordCount());
        assertEquals(1, snapshot.getStoredCount());
        assertEquals(1, snapshot.getReplayedCount());
        assertEquals(2,
                harness.store.readOwner(OWNER_A, 2, readEvidence(OWNER_A))
                        .getRecords().get(0).getCompletedActionCount());
    }

    @Test
    public void retentionAndDurationBoundsAreDeterministic() {
        Harness harness = harness(limits(2, 2, 2, 20, 60), true, true, true);
        assertEquals(EpisodicMemoryStore.StoreOutcome.RETENTION_LIMIT,
                harness.store.store(
                        request(OWNER_A, "episode-long-retention", "fatigue-care", 21, 1, 1))
                        .getOutcome());
        assertEquals(EpisodicMemoryStore.StoreOutcome.EPISODE_DURATION_LIMIT,
                harness.store.store(requestWithTimes(
                        OWNER_A, "episode-long", 900, 961, 10)).getOutcome());
        assertEquals(EpisodicMemoryStore.StoreOutcome.STORED,
                harness.store.store(
                        request(OWNER_A, "episode-short", "fatigue-care", 10, 1, 1))
                        .getOutcome());
        harness.clock.set(1_009);
        assertEquals(1, harness.store.snapshot().getActiveRecordCount());
        harness.clock.set(1_010);
        assertEquals(0, harness.store.snapshot().getActiveRecordCount());
        assertEquals(1, harness.store.snapshot().getExpiredCount());
        assertFalse(harness.store.snapshot().isRawContinuousSignalRetained());
    }

    @Test
    public void globalAndOwnerCapacityFailClosedWithoutEviction() {
        Harness harness = harness(limits(2, 1, 1, 100, 100), true, true, true);
        assertEquals(EpisodicMemoryStore.StoreOutcome.STORED,
                harness.store.store(
                        request(OWNER_A, "episode-a", "fatigue-care", 80, 1, 1))
                        .getOutcome());
        assertEquals(EpisodicMemoryStore.StoreOutcome.OWNER_CAPACITY,
                harness.store.store(
                        request(OWNER_A, "episode-b", "thermal-care", 80, 1, 1))
                        .getOutcome());
        assertEquals(EpisodicMemoryStore.StoreOutcome.STORED,
                harness.store.store(
                        request(OWNER_B, "episode-b", "thermal-care", 80, 1, 1))
                        .getOutcome());
        assertEquals(EpisodicMemoryStore.StoreOutcome.GLOBAL_CAPACITY,
                harness.store.store(
                        request(OWNER_C, "episode-c", "rest-care", 80, 1, 1))
                        .getOutcome());
        assertEquals(2, harness.store.snapshot().getActiveRecordCount());
        assertEquals(2, harness.store.snapshot().getActiveOwnerCount());
    }

    @Test
    public void eraseRequiresExactAuthorityAndMalformedInputsAreRejected() {
        Harness harness = harness(limits(4, 3, 3, 100, 100), true, true, false);
        harness.store.store(request(OWNER_A, "episode-a", "fatigue-care", 80, 1, 1));
        harness.store.store(request(OWNER_A, "episode-b", "thermal-care", 80, 1, 1));
        harness.store.store(request(OWNER_B, "episode-c", "rest-care", 80, 1, 1));

        assertEquals(EpisodicMemoryStore.EraseOutcome.AUTHORIZATION_DENIED,
                harness.store.eraseEpisode(
                        OWNER_A,
                        "episode-a",
                        eraseEpisode(OWNER_A, "episode-a")).getOutcome());
        harness.eraseAllowed.set(true);
        EpisodicMemoryStore.EraseResult one = harness.store.eraseEpisode(
                OWNER_A,
                "episode-a",
                eraseEpisode(OWNER_A, "episode-a"));
        assertEquals(EpisodicMemoryStore.EraseOutcome.ERASED, one.getOutcome());
        assertEquals(1, one.getErasedRecordCount());
        EpisodicMemoryStore.EraseResult owner = harness.store.eraseOwner(
                OWNER_A,
                eraseOwner(OWNER_A));
        assertEquals(EpisodicMemoryStore.EraseOutcome.ERASED, owner.getOutcome());
        assertEquals(1, owner.getErasedRecordCount());
        assertEquals(1, harness.store.readOwner(OWNER_B, 3, readEvidence(OWNER_B))
                .getRecords().size());
        assertEquals(2, harness.store.snapshot().getErasedCount());
        assertEquals(1, harness.store.snapshot().getErasedOwnerCount());
        assertFalse(harness.store.isProductionStoragePolicyAuthorityWired());
        assertFalse(harness.store.isProductionReadAuthorityWired());
        assertFalse(harness.store.isProductionEraseAuthorityWired());

        EpisodicMemoryStore.ReadResult deniedRead = harness.store.readOwner(
                OWNER_B,
                3,
                new EpisodicMemoryStore.ReadEvidence(
                        OWNER_A, "read-wrong-owner", 900, 2_000));
        assertEquals(EpisodicMemoryStore.ReadOutcome.AUTHORIZATION_DENIED,
                deniedRead.getOutcome());
        assertTrue(deniedRead.getRecords().isEmpty());

        assertEquals(EpisodicMemoryStore.EraseOutcome.AUTHORIZATION_DENIED,
                harness.store.eraseEpisode(
                        OWNER_B,
                        "episode-c",
                        eraseEpisode(OWNER_A, "episode-c")).getOutcome());

        try {
            request(OWNER_A, "episode-invalid", "fatigue-care", 80, 1, 2);
            fail("completed action count above planned must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            limits(0, 1, 1, 1, 1);
            fail("zero record capacity must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        EpisodicMemoryStore.StoragePolicyEvidence mismatch =
                new EpisodicMemoryStore.StoragePolicyEvidence(
                        OWNER_B, "episode-mismatch", "policy-mismatch", 900, 2_000);
        assertEquals(OWNER_B, mismatch.getOwnerFingerprint());
        assertEquals("episode-mismatch", mismatch.getEpisodeId());
        assertEquals(EpisodicMemoryStore.StoreOutcome.POLICY_DENIED,
                harness.store.store(request(
                        OWNER_A,
                        "episode-mismatch",
                        "fatigue-care",
                        80,
                        1,
                        1,
                        mismatch)).getOutcome());
        try {
            new EpisodicMemoryStore.ScenarioReference("fatigue-care", "not-a-digest");
            fail("non-digest catalog identity must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static Harness harness(
            EpisodicMemoryStore.Limits limits,
            boolean catalogAllowed,
            boolean policyAllowed,
            boolean eraseAllowed) {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicBoolean eraseFlag = new AtomicBoolean(eraseAllowed);
        EpisodicMemoryStore store = EpisodicMemoryStore.createForContractTest(
                limits,
                clock::get,
                reference -> catalogAllowed
                        && reference.getCatalogDigest().equals(CATALOG_DIGEST),
                (evidence, reference, now) -> policyAllowed,
                (evidence, now) -> true,
                (evidence, now) -> eraseFlag.get());
        return new Harness(store, clock, eraseFlag);
    }

    private static EpisodicMemoryStore.Limits limits(
            int records,
            int ownerRecords,
            int readRecords,
            long retentionMs,
            long durationMs) {
        return new EpisodicMemoryStore.Limits(
                records,
                ownerRecords,
                readRecords,
                retentionMs,
                durationMs);
    }

    private static EpisodicMemoryStore.RecordRequest request(
            String owner,
            String episode,
            String scenario,
            long retentionMs,
            int plannedActions,
            int completedActions) {
        return request(
                owner,
                episode,
                scenario,
                retentionMs,
                plannedActions,
                completedActions,
                new EpisodicMemoryStore.StoragePolicyEvidence(
                        owner, episode, "policy-" + episode, 900, 2_000));
    }

    private static EpisodicMemoryStore.RecordRequest request(
            String owner,
            String episode,
            String scenario,
            long retentionMs,
            int plannedActions,
            int completedActions,
            EpisodicMemoryStore.StoragePolicyEvidence evidence) {
        return EpisodicMemoryStore.RecordRequest.fromScenarioResult(
                owner,
                episode,
                new EpisodicMemoryStore.ScenarioReference(scenario, CATALOG_DIGEST),
                EpisodicMemoryStore.TriggerKind.EXPLICIT_USER_INTENT,
                EpisodicMemoryStore.ResultKind.SUCCEEDED,
                EpisodicMemoryStore.OutcomeCode.COMPLETED,
                plannedActions,
                completedActions,
                900,
                950,
                retentionMs,
                evidence);
    }

    private static EpisodicMemoryStore.RecordRequest requestWithTimes(
            String owner,
            String episode,
            long startedAt,
            long finishedAt,
            long retentionMs) {
        return EpisodicMemoryStore.RecordRequest.fromScenarioResult(
                owner,
                episode,
                new EpisodicMemoryStore.ScenarioReference("fatigue-care", CATALOG_DIGEST),
                EpisodicMemoryStore.TriggerKind.CONTEXT_THRESHOLD,
                EpisodicMemoryStore.ResultKind.PARTIAL,
                EpisodicMemoryStore.OutcomeCode.READBACK_MISMATCH,
                2,
                1,
                startedAt,
                finishedAt,
                retentionMs,
                new EpisodicMemoryStore.StoragePolicyEvidence(
                        owner, episode, "policy-" + episode, 900, 2_000));
    }

    private static EpisodicMemoryStore.EraseEvidence eraseEpisode(
            String owner,
            String episode) {
        return new EpisodicMemoryStore.EraseEvidence(
                EpisodicMemoryStore.EraseOperation.EPISODE,
                owner,
                episode,
                "erase-" + episode,
                900,
                2_000);
    }

    private static EpisodicMemoryStore.EraseEvidence eraseOwner(String owner) {
        return new EpisodicMemoryStore.EraseEvidence(
                EpisodicMemoryStore.EraseOperation.OWNER,
                owner,
                null,
                "erase-owner",
                900,
                2_000);
    }

    private static EpisodicMemoryStore.ReadEvidence readEvidence(String owner) {
        return new EpisodicMemoryStore.ReadEvidence(
                owner,
                "read-owner",
                900,
                2_000);
    }

    private static final class Harness {
        private final EpisodicMemoryStore store;
        private final AtomicLong clock;
        private final AtomicBoolean eraseAllowed;

        private Harness(
                EpisodicMemoryStore store,
                AtomicLong clock,
                AtomicBoolean eraseAllowed) {
            this.store = store;
            this.clock = clock;
            this.eraseAllowed = eraseAllowed;
        }
    }
}
