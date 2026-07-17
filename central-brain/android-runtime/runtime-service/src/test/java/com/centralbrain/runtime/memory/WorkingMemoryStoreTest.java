package com.centralbrain.runtime.memory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class WorkingMemoryStoreTest {
    private static final String OWNER_A = "a".repeat(64);
    private static final String OWNER_B = "b".repeat(64);

    @Test
    public void sessionIsolationDefensiveCopiesAndImmutableRead() {
        Harness harness = harness(limits(2, 3, 16, 16, 8, 8, 2, 3, 100));
        byte[] callerPayload = bytes(1, 2, 3);
        WorkingMemoryStore.PutRequest request = request(
                OWNER_A, "session-a", "context", callerPayload, 2, 20);
        callerPayload[0] = 9;

        WorkingMemoryStore.PutResult created = harness.store.put(request);
        assertEquals(WorkingMemoryStore.PutOutcome.CREATED, created.getOutcome());
        assertArrayEquals(bytes(1, 2, 3), created.getItem().getPayloadCopy());
        assertTrue(harness.store.readSessionOwned(OWNER_B, "session-a", 3).isEmpty());

        List<WorkingMemoryStore.ItemSnapshot> firstRead =
                harness.store.readSessionOwned(OWNER_A, "session-a", 3);
        byte[] leakedCopy = firstRead.get(0).getPayloadCopy();
        leakedCopy[1] = 8;
        assertArrayEquals(
                bytes(1, 2, 3),
                harness.store.readSessionOwned(OWNER_A, "session-a", 3)
                        .get(0).getPayloadCopy());
        try {
            firstRead.clear();
            fail("Working Memory reads must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
        assertFalse(harness.store.isPersistentStorageWired());
        assertFalse(harness.store.isRuntimeWired());
        assertFalse(harness.store.isModelContextPublicationEnabled());
        assertFalse(harness.store.isTokenCountVerifiedByModelTokenizer());
        assertFalse(harness.store.isContentLoggingEnabled());
        assertFalse(harness.store.isHardwareAccessed());
    }

    @Test
    public void monotonicTtlExpiresAndWipesRetainedPayload() {
        Harness harness = harness(limits(2, 2, 16, 16, 8, 8, 2, 2, 20));
        harness.store.put(request(
                OWNER_A, "session-a", "short", bytes(1, 2, 3, 4), 2, 10));
        harness.clock.set(1_009);
        assertEquals(1, harness.store.snapshot().getActiveItemCount());
        harness.clock.set(1_010);

        WorkingMemoryStore.Snapshot expired = harness.store.snapshot();
        assertEquals(0, expired.getActiveSessionCount());
        assertEquals(0, expired.getActiveItemCount());
        assertEquals(1, expired.getExpiredItemCount());
        assertEquals(4, expired.getWipedByteCount());
        assertFalse(expired.isRawPayloadRetained());
        assertTrue(harness.store.readSessionOwned(OWNER_A, "session-a", 2).isEmpty());
    }

    @Test
    public void itemByteTokenSessionAndTtlLimitsFailClosed() {
        Harness harness = harness(limits(1, 2, 6, 5, 4, 4, 2, 2, 20));
        assertEquals(WorkingMemoryStore.PutOutcome.ITEM_BYTE_LIMIT,
                harness.store.put(request(
                        OWNER_A, "session-a", "oversize", bytes(1, 2, 3, 4, 5), 2, 10))
                        .getOutcome());
        assertEquals(WorkingMemoryStore.PutOutcome.ITEM_TOKEN_LIMIT,
                harness.store.put(request(
                        OWNER_A, "session-a", "tokens", bytes(1), 5, 10))
                        .getOutcome());
        assertEquals(WorkingMemoryStore.PutOutcome.TTL_LIMIT,
                harness.store.put(request(
                        OWNER_A, "session-a", "ttl", bytes(1), 1, 21))
                        .getOutcome());

        assertEquals(WorkingMemoryStore.PutOutcome.CREATED,
                harness.store.put(request(
                        OWNER_A, "session-a", "one", bytes(1, 2, 3, 4), 2, 10))
                        .getOutcome());
        assertEquals(WorkingMemoryStore.PutOutcome.CREATED,
                harness.store.put(request(
                        OWNER_A, "session-a", "two", bytes(5, 6), 3, 10))
                        .getOutcome());
        assertEquals(WorkingMemoryStore.PutOutcome.ITEM_LIMIT,
                harness.store.put(request(
                        OWNER_A, "session-a", "three", bytes(7), 1, 10))
                        .getOutcome());
        assertEquals(WorkingMemoryStore.PutOutcome.SESSION_BYTE_LIMIT,
                harness.store.put(request(
                        OWNER_A, "session-a", "two", bytes(5, 6, 7), 3, 10))
                        .getOutcome());
        assertEquals(WorkingMemoryStore.PutOutcome.SESSION_TOKEN_LIMIT,
                harness.store.put(request(
                        OWNER_A, "session-a", "two", bytes(5, 6), 4, 10))
                        .getOutcome());
        assertEquals(WorkingMemoryStore.PutOutcome.SESSION_LIMIT,
                harness.store.put(request(
                        OWNER_B, "session-b", "one", bytes(1), 1, 10))
                        .getOutcome());
        assertEquals(2, harness.store.snapshot().getActiveItemCount());
        assertEquals(6, harness.store.snapshot().getActiveByteCount());
        assertEquals(5, harness.store.snapshot().getActiveTokenCount());
    }

    @Test
    public void replayReplacementAndRemovalKeepBudgetsExact() {
        Harness harness = harness(limits(2, 2, 12, 12, 8, 8, 2, 2, 100));
        WorkingMemoryStore.PutRequest first = request(
                OWNER_A, "session-a", "item", bytes(1, 2, 3), 2, 20);
        assertEquals(WorkingMemoryStore.PutOutcome.CREATED,
                harness.store.put(first).getOutcome());
        assertEquals(WorkingMemoryStore.PutOutcome.REPLAYED,
                harness.store.put(first).getOutcome());
        assertEquals(WorkingMemoryStore.PutOutcome.REPLACED,
                harness.store.put(request(
                        OWNER_A, "session-a", "item", bytes(4, 5, 6, 7), 3, 30))
                        .getOutcome());

        WorkingMemoryStore.Snapshot replaced = harness.store.snapshot();
        assertEquals(1, replaced.getCreatedItemCount());
        assertEquals(1, replaced.getReplacedItemCount());
        assertEquals(4, replaced.getActiveByteCount());
        assertEquals(3, replaced.getActiveTokenCount());
        assertEquals(3, replaced.getWipedByteCount());
        assertEquals(WorkingMemoryStore.RemoveOutcome.APPLIED,
                harness.store.removeOwned(OWNER_A, "session-a", "item"));
        assertEquals(WorkingMemoryStore.RemoveOutcome.NOT_FOUND,
                harness.store.removeOwned(OWNER_A, "session-a", "item"));
        assertEquals(0, harness.store.snapshot().getActiveSessionCount());
        assertEquals(1, harness.store.snapshot().getRemovedItemCount());
        assertEquals(7, harness.store.snapshot().getWipedByteCount());
    }

    @Test
    public void terminalCleanupWipesBlocksReplayAndBoundsTombstones() {
        Harness harness = harness(limits(2, 3, 16, 16, 8, 8, 1, 3, 100));
        harness.store.put(request(
                OWNER_A, "session-a", "one", bytes(1, 2), 2, 20));
        harness.store.put(request(
                OWNER_A, "session-a", "two", bytes(3, 4, 5), 3, 20));
        WorkingMemoryStore.TerminalResult terminal =
                harness.store.terminateSessionOwned(OWNER_A, "session-a");
        assertEquals(WorkingMemoryStore.TerminalOutcome.APPLIED, terminal.getOutcome());
        assertEquals(2, terminal.getCleanedItemCount());
        assertEquals(5, terminal.getCleanedByteCount());
        assertEquals(5, terminal.getCleanedTokenCount());
        assertEquals(WorkingMemoryStore.TerminalOutcome.REPLAYED,
                harness.store.terminateSessionOwned(OWNER_A, "session-a").getOutcome());
        assertEquals(WorkingMemoryStore.PutOutcome.SESSION_TERMINAL,
                harness.store.put(request(
                        OWNER_A, "session-a", "three", bytes(6), 1, 20))
                        .getOutcome());
        assertEquals(WorkingMemoryStore.RemoveOutcome.SESSION_TERMINAL,
                harness.store.removeOwned(OWNER_A, "session-a", "one"));

        WorkingMemoryStore.Snapshot cleaned = harness.store.snapshot();
        assertEquals(0, cleaned.getActiveItemCount());
        assertEquals(1, cleaned.getTerminalCleanupCount());
        assertEquals(2, cleaned.getTerminalCleanedItemCount());
        assertEquals(5, cleaned.getWipedByteCount());
        assertFalse(cleaned.isRawPayloadRetained());

        harness.store.terminateSessionOwned(OWNER_B, "session-b");
        assertEquals(1, harness.store.snapshot().getTerminalEvictionCount());
        assertEquals(WorkingMemoryStore.PutOutcome.CREATED,
                harness.store.put(request(
                        OWNER_A, "session-a", "reused-after-bound", bytes(7), 1, 20))
                        .getOutcome());
    }

    @Test
    public void malformedLimitsAndRequestsAreRejected() {
        try {
            limits(0, 1, 1, 1, 1, 1, 1, 1, 1);
            fail("zero session capacity must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            request(OWNER_A, "session-a", "empty", new byte[0], 1, 1);
            fail("empty payload must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            request(OWNER_A, "session-a", "token", bytes(1), 0, 1);
            fail("zero token count must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            request(OWNER_A, "session-a", "ttl", bytes(1), 1, 0);
            fail("zero TTL must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            request(
                    OWNER_A,
                    "session-a",
                    "absolute-bytes",
                    new byte[64 * 1024 + 1],
                    1,
                    1);
            fail("payload above the absolute contract bound must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            request(OWNER_A, "session-a", "absolute-tokens", bytes(1), 16_385, 1);
            fail("token count above the absolute contract bound must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            request(
                    OWNER_A,
                    "session-a",
                    "absolute-ttl",
                    bytes(1),
                    1,
                    WorkingMemoryStore.MAX_TTL_MS + 1);
            fail("TTL above the absolute contract bound must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static Harness harness(WorkingMemoryStore.Limits limits) {
        AtomicLong clock = new AtomicLong(1_000);
        return new Harness(new WorkingMemoryStore(limits, clock::get), clock);
    }

    private static WorkingMemoryStore.Limits limits(
            int sessions,
            int items,
            int sessionBytes,
            int sessionTokens,
            int itemBytes,
            int itemTokens,
            int terminalSessions,
            int readItems,
            long ttlMs) {
        return new WorkingMemoryStore.Limits(
                sessions,
                items,
                sessionBytes,
                sessionTokens,
                itemBytes,
                itemTokens,
                terminalSessions,
                readItems,
                ttlMs);
    }

    private static WorkingMemoryStore.PutRequest request(
            String owner,
            String session,
            String item,
            byte[] payload,
            int tokens,
            long ttlMs) {
        return WorkingMemoryStore.PutRequest.fromRuntimePolicy(
                owner,
                session,
                item,
                "central.working-memory.v1",
                payload,
                tokens,
                ttlMs);
    }

    private static byte[] bytes(int... values) {
        byte[] output = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            output[index] = (byte) values[index];
        }
        return output;
    }

    private static final class Harness {
        final WorkingMemoryStore store;
        final AtomicLong clock;

        Harness(WorkingMemoryStore store, AtomicLong clock) {
            this.store = store;
            this.clock = clock;
        }
    }
}
