package com.centralbrain.runtime.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class BoundedMemoryLifecycleTest {
    private static final String OWNER_A = repeat("a", 64);
    private static final String OWNER_B = repeat("b", 64);
    private static final String DIGEST_A = repeat("c", 64);
    private static final String DIGEST_B = repeat("d", 64);

    @Test
    public void scopeTtlAndProfileConsentFailClosed() {
        Harness harness = harness(4, 2, 4, 10);
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.CREATED,
                harness.lifecycle.write(write(
                        OWNER_A,
                        "ephemeral",
                        BoundedMemoryLifecycle.Scope.EPHEMERAL,
                        BoundedMemoryLifecycle.Purpose.CONVERSATION_CONTEXT,
                        "session-a",
                        DIGEST_A,
                        100,
                        null)).getOutcome());
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.SCOPE_POLICY_DENIED,
                harness.lifecycle.write(write(
                        OWNER_A,
                        "too-long",
                        BoundedMemoryLifecycle.Scope.EPHEMERAL,
                        BoundedMemoryLifecycle.Purpose.CONVERSATION_CONTEXT,
                        "session-a",
                        DIGEST_A,
                        BoundedMemoryLifecycle.MAX_EPHEMERAL_TTL_MS + 1,
                        null)).getOutcome());
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.CONSENT_REQUIRED,
                harness.lifecycle.write(write(
                        OWNER_A,
                        "profile-no-consent",
                        BoundedMemoryLifecycle.Scope.PROFILE,
                        BoundedMemoryLifecycle.Purpose.COMFORT_PREFERENCE,
                        "",
                        DIGEST_A,
                        100,
                        null)).getOutcome());
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.SCOPE_POLICY_DENIED,
                harness.lifecycle.write(write(
                        OWNER_A,
                        "profile-ineligible",
                        BoundedMemoryLifecycle.Scope.PROFILE,
                        BoundedMemoryLifecycle.Purpose.SAFETY_CONTEXT,
                        "",
                        DIGEST_A,
                        100,
                        consent(OWNER_A,
                                BoundedMemoryLifecycle.Purpose.SAFETY_CONTEXT,
                                10_000))).getOutcome());
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.CONSENT_INVALID,
                harness.lifecycle.write(write(
                        OWNER_A,
                        "profile-wrong-owner",
                        BoundedMemoryLifecycle.Scope.PROFILE,
                        BoundedMemoryLifecycle.Purpose.COMFORT_PREFERENCE,
                        "",
                        DIGEST_A,
                        100,
                        consent(OWNER_B,
                                BoundedMemoryLifecycle.Purpose.COMFORT_PREFERENCE,
                                10_000))).getOutcome());
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.CREATED,
                harness.lifecycle.write(write(
                        OWNER_A,
                        "profile-valid",
                        BoundedMemoryLifecycle.Scope.PROFILE,
                        BoundedMemoryLifecycle.Purpose.COMFORT_PREFERENCE,
                        "",
                        DIGEST_A,
                        100,
                        consent(OWNER_A,
                                BoundedMemoryLifecycle.Purpose.COMFORT_PREFERENCE,
                                10_000))).getOutcome());
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.CONFLICT,
                harness.lifecycle.write(write(
                        OWNER_A,
                        "profile-valid",
                        BoundedMemoryLifecycle.Scope.PROFILE,
                        BoundedMemoryLifecycle.Purpose.COMFORT_PREFERENCE,
                        "",
                        DIGEST_A,
                        100,
                        consent(OWNER_A,
                                BoundedMemoryLifecycle.Purpose.COMFORT_PREFERENCE,
                                20_000))).getOutcome());
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.SCOPE_POLICY_DENIED,
                harness.lifecycle.write(write(
                        OWNER_B,
                        "session-with-consent",
                        BoundedMemoryLifecycle.Scope.SESSION,
                        BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                        "session-b",
                        DIGEST_B,
                        100,
                        consent(OWNER_B,
                                BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                                10_000))).getOutcome());
    }

    @Test
    public void ownerClientReplayConflictAndCapacityAreBounded() {
        Harness harness = harness(2, 1, 2, 10);
        BoundedMemoryLifecycle.TrustedWrite first = write(
                OWNER_A,
                "request-a",
                BoundedMemoryLifecycle.Scope.SESSION,
                BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                "session-a",
                DIGEST_A,
                100,
                null);
        BoundedMemoryLifecycle.WriteResult created = harness.lifecycle.write(first);
        BoundedMemoryLifecycle.WriteResult replayed = harness.lifecycle.write(first);
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.CREATED, created.getOutcome());
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.REPLAYED, replayed.getOutcome());
        assertEquals(created.getSnapshot().getMemoryId(), replayed.getSnapshot().getMemoryId());
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.CONFLICT,
                harness.lifecycle.write(write(
                        OWNER_A,
                        "request-a",
                        BoundedMemoryLifecycle.Scope.SESSION,
                        BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                        "session-a",
                        DIGEST_B,
                        100,
                        null)).getOutcome());
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.OWNER_LIMIT,
                harness.lifecycle.write(write(
                        OWNER_A,
                        "request-a-2",
                        BoundedMemoryLifecycle.Scope.SESSION,
                        BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                        "session-a",
                        DIGEST_B,
                        100,
                        null)).getOutcome());
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.CREATED,
                harness.lifecycle.write(write(
                        OWNER_B,
                        "request-b",
                        BoundedMemoryLifecycle.Scope.SESSION,
                        BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                        "session-b",
                        DIGEST_B,
                        100,
                        null)).getOutcome());
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.GLOBAL_LIMIT,
                harness.lifecycle.write(write(
                        repeat("e", 64),
                        "request-c",
                        BoundedMemoryLifecycle.Scope.SESSION,
                        BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                        "session-c",
                        DIGEST_B,
                        100,
                        null)).getOutcome());
    }

    @Test
    public void queryIsOwnerScopedAndRedacted() {
        Harness harness = harness(4, 3, 3, 10);
        BoundedMemoryLifecycle.WriteResult created = harness.lifecycle.write(write(
                OWNER_A,
                "query-a",
                BoundedMemoryLifecycle.Scope.SESSION,
                BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                "session-a",
                DIGEST_A,
                100,
                null));
        List<BoundedMemoryLifecycle.RedactedRecord> records = harness.lifecycle.queryOwned(
                OWNER_A,
                BoundedMemoryLifecycle.Scope.SESSION,
                BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                10);
        assertEquals(1, records.size());
        assertEquals(created.getSnapshot().getMemoryId(), records.get(0).getMemoryId());
        assertTrue(records.get(0).isSessionScoped());
        assertFalse(records.get(0).isContentDigestExposed());
        assertTrue(harness.lifecycle.queryOwned(OWNER_B, null, null, 10).isEmpty());
        assertNull(harness.lifecycle.findOwned(created.getSnapshot().getMemoryId(), OWNER_B));
        try {
            records.clear();
            fail("redacted query result must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void expiryDeleteAndTerminalRetentionClearContentReference() {
        Harness harness = harness(4, 4, 1, 10);
        BoundedMemoryLifecycle.TrustedWrite request = write(
                OWNER_A,
                "expiry-a",
                BoundedMemoryLifecycle.Scope.EPHEMERAL,
                BoundedMemoryLifecycle.Purpose.CONVERSATION_CONTEXT,
                "session-a",
                DIGEST_A,
                10,
                null);
        String memoryId = harness.lifecycle.write(request).getSnapshot().getMemoryId();
        harness.clock.set(1_011);
        BoundedMemoryLifecycle.LifecycleSnapshot expired = harness.lifecycle.findOwned(
                memoryId,
                OWNER_A);
        assertEquals(BoundedMemoryLifecycle.State.EXPIRED, expired.getState());
        assertFalse(expired.isContentReferenceRetained());
        assertEquals(BoundedMemoryLifecycle.DeleteOutcome.APPLIED,
                harness.lifecycle.deleteOwned(memoryId, OWNER_A));
        assertEquals(BoundedMemoryLifecycle.DeleteOutcome.REPLAYED,
                harness.lifecycle.deleteOwned(memoryId, OWNER_A));
        assertEquals(BoundedMemoryLifecycle.WriteOutcome.REPLAYED,
                harness.lifecycle.write(request).getOutcome());

        String second = harness.lifecycle.write(write(
                OWNER_B,
                "delete-b",
                BoundedMemoryLifecycle.Scope.SESSION,
                BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                "session-b",
                DIGEST_B,
                100,
                null)).getSnapshot().getMemoryId();
        assertEquals(BoundedMemoryLifecycle.DeleteOutcome.APPLIED,
                harness.lifecycle.deleteOwned(second, OWNER_B));
        assertNull(harness.lifecycle.findOwned(memoryId, OWNER_A));
        BoundedMemoryLifecycle.Snapshot snapshot = harness.lifecycle.snapshot();
        assertEquals(1, snapshot.getTerminalRecordCount());
        assertEquals(1, snapshot.getTerminalEvictionCount());
        assertFalse(snapshot.isPersistentStorageWired());
        assertFalse(snapshot.isProductionServiceWired());
        assertFalse(snapshot.isRawContentStored());
    }

    @Test
    public void exportRequiresScopeAndTrustedAuthorization() {
        Harness harness = harness(4, 4, 4, 10);
        String ephemeral = harness.lifecycle.write(write(
                OWNER_A,
                "export-ephemeral",
                BoundedMemoryLifecycle.Scope.EPHEMERAL,
                BoundedMemoryLifecycle.Purpose.CONVERSATION_CONTEXT,
                "session-a",
                DIGEST_A,
                100,
                null)).getSnapshot().getMemoryId();
        assertEquals(BoundedMemoryLifecycle.ExportOutcome.SCOPE_NOT_EXPORTABLE,
                harness.lifecycle.exportOwned(
                        ephemeral,
                        OWNER_A,
                        exportAuthorization(
                                OWNER_A,
                                BoundedMemoryLifecycle.Purpose.CONVERSATION_CONTEXT,
                                ephemeral,
                                10_000)).getOutcome());

        String session = harness.lifecycle.write(write(
                OWNER_A,
                "export-session",
                BoundedMemoryLifecycle.Scope.SESSION,
                BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                "session-a",
                DIGEST_B,
                100,
                null)).getSnapshot().getMemoryId();
        assertEquals(BoundedMemoryLifecycle.ExportOutcome.AUTHORIZATION_REQUIRED,
                harness.lifecycle.exportOwned(session, OWNER_A, null).getOutcome());
        assertEquals(BoundedMemoryLifecycle.ExportOutcome.AUTHORIZATION_INVALID,
                harness.lifecycle.exportOwned(
                        session,
                        OWNER_A,
                        exportAuthorization(
                                OWNER_B,
                                BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                                session,
                                10_000)).getOutcome());
        BoundedMemoryLifecycle.ExportResult exported = harness.lifecycle.exportOwned(
                session,
                OWNER_A,
                exportAuthorization(
                        OWNER_A,
                        BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                        session,
                        10_000));
        assertEquals(BoundedMemoryLifecycle.ExportOutcome.EXPORTED, exported.getOutcome());
        assertEquals(DIGEST_B, exported.getRecord().getContentDigest());
        assertEquals(BoundedMemoryLifecycle.DeleteOutcome.APPLIED,
                harness.lifecycle.deleteOwned(session, OWNER_A));
        assertEquals(BoundedMemoryLifecycle.ExportOutcome.NOT_ACTIVE,
                harness.lifecycle.exportOwned(
                        session,
                        OWNER_A,
                        exportAuthorization(
                                OWNER_A,
                                BoundedMemoryLifecycle.Purpose.ROUTE_CONTEXT,
                                session,
                                10_000)).getOutcome());
    }

    private static Harness harness(
            int maxActive,
            int maxPerOwner,
            int maxTerminal,
            int maxQuery) {
        AtomicLong clock = new AtomicLong(1_000);
        AtomicInteger ids = new AtomicInteger();
        BoundedMemoryLifecycle lifecycle = BoundedMemoryLifecycle.createForContractTest(
                new BoundedMemoryLifecycle.Limits(
                        maxActive,
                        maxPerOwner,
                        maxTerminal,
                        maxQuery),
                clock::get,
                () -> "test-" + ids.incrementAndGet());
        return new Harness(lifecycle, clock);
    }

    private static BoundedMemoryLifecycle.TrustedWrite write(
            String owner,
            String clientId,
            BoundedMemoryLifecycle.Scope scope,
            BoundedMemoryLifecycle.Purpose purpose,
            String sessionId,
            String digest,
            long ttlMs,
            BoundedMemoryLifecycle.TrustedConsentEvidence consent) {
        return BoundedMemoryLifecycle.TrustedWrite.fromRuntimePolicy(
                owner,
                clientId,
                scope,
                purpose,
                sessionId,
                "central.memory.v1",
                digest,
                ttlMs,
                consent);
    }

    private static BoundedMemoryLifecycle.TrustedConsentEvidence consent(
            String owner,
            BoundedMemoryLifecycle.Purpose purpose,
            long expiresAt) {
        return BoundedMemoryLifecycle.TrustedConsentEvidence.grantedByGovernance(
                owner,
                purpose,
                "consent-1",
                expiresAt);
    }

    private static BoundedMemoryLifecycle.TrustedExportAuthorization exportAuthorization(
            String owner,
            BoundedMemoryLifecycle.Purpose purpose,
            String memoryId,
            long expiresAt) {
        return BoundedMemoryLifecycle.TrustedExportAuthorization.grantedByGovernance(
                owner,
                purpose,
                memoryId,
                "export-1",
                expiresAt);
    }

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }

    private static final class Harness {
        final BoundedMemoryLifecycle lifecycle;
        final AtomicLong clock;

        Harness(BoundedMemoryLifecycle lifecycle, AtomicLong clock) {
            this.lifecycle = lifecycle;
            this.clock = clock;
        }
    }
}
