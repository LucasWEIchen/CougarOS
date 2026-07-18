package com.centralbrain.runtime.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class MemoryConsentControllerTest {
    private static final String OWNER = repeat('a', 64);
    private static final String OTHER_OWNER = repeat('b', 64);

    @Test
    public void fixedSourcesAreVisibleWithoutContent() {
        Fixture fixture = fixture(MemoryConsentController.AuthorizationDecision.ALLOWED);
        MemoryConsentController.HmiSnapshot parked = fixture.controller.snapshot(
                OWNER, MemoryConsentController.DrivingState.PARKED);
        MemoryConsentController.HmiSnapshot moving = fixture.controller.snapshot(
                OWNER, MemoryConsentController.DrivingState.MOVING);

        assertEquals(3, parked.getSources().size());
        assertEquals(MemoryConsentController.MemorySource.WORKING_SESSION,
                parked.getSources().get(0).getSource());
        assertEquals(MemoryConsentController.MemorySource.PROFILE_PREFERENCE,
                parked.getSources().get(1).getSource());
        assertEquals(MemoryConsentController.MemorySource.EPISODIC_SCENARIO,
                parked.getSources().get(2).getSource());
        assertTrue(parked.isManagementAllowed());
        assertFalse(moving.isManagementAllowed());
        assertEquals(MemoryConsentController.StoragePresence.NOT_DISCLOSED,
                moving.getSources().get(1).getStoragePresence());
        expectUnsupported(() -> parked.getSources().clear());
    }

    @Test
    public void parkedUserCanDisableRetainedMemoryAndClearPreferences() {
        Fixture fixture = fixture(MemoryConsentController.AuthorizationDecision.ALLOWED);
        MemoryConsentController.MutationResult disabled = fixture.controller
                .setRetainedMemoryEnabled(
                        OWNER,
                        false,
                        MemoryConsentController.DrivingState.PARKED,
                        retainedEvidence("memory.disable", OWNER, false));
        MemoryConsentController.MutationResult cleared = fixture.controller
                .clearProfilePreferences(
                        OWNER,
                        MemoryConsentController.DrivingState.PARKED,
                        clearEvidence("memory.clear", OWNER));
        MemoryConsentController.HmiSnapshot snapshot = fixture.controller.snapshot(
                OWNER, MemoryConsentController.DrivingState.PARKED);

        assertEquals(MemoryConsentController.ResultCode.APPLIED, disabled.getCode());
        assertEquals(MemoryConsentController.ResultCode.APPLIED, cleared.getCode());
        assertEquals(2, snapshot.getRevision());
        assertFalse(snapshot.isRetainedMemoryEnabled());
        assertFalse(snapshot.getSources().get(1).isEnabled());
        assertEquals(MemoryConsentController.StoragePresence.EMPTY,
                snapshot.getSources().get(1).getStoragePresence());
        assertFalse(disabled.isRepositoryMutationApplied());
        assertFalse(cleared.isRepositoryMutationApplied());
        assertEquals(2, fixture.authorityCalls.get());
    }

    @Test
    public void movingAndUnknownBlockComplexManagementBeforeAuthority() {
        Fixture fixture = fixture(MemoryConsentController.AuthorizationDecision.ALLOWED);
        MemoryConsentController.MutationResult moving = fixture.controller
                .setRetainedMemoryEnabled(
                        OWNER,
                        false,
                        MemoryConsentController.DrivingState.MOVING,
                        retainedEvidence("memory.moving", OWNER, false));
        MemoryConsentController.MutationResult unknown = fixture.controller
                .clearProfilePreferences(
                        OWNER,
                        MemoryConsentController.DrivingState.UNKNOWN,
                        clearEvidence("memory.unknown", OWNER));

        assertEquals(MemoryConsentController.ResultCode.DRIVING_RESTRICTED,
                moving.getCode());
        assertEquals(MemoryConsentController.ResultCode.DRIVING_RESTRICTED,
                unknown.getCode());
        assertEquals(0, fixture.authorityCalls.get());
        assertEquals(0, fixture.controller.snapshot(
                OWNER, MemoryConsentController.DrivingState.PARKED).getRevision());
    }

    @Test
    public void malformedExpiredDeniedAndUnavailableEvidenceFailClosed() {
        Fixture denied = fixture(MemoryConsentController.AuthorizationDecision.DENIED);
        assertEquals(MemoryConsentController.ResultCode.AUTHORITY_DENIED,
                denied.controller.clearProfilePreferences(
                        OWNER,
                        MemoryConsentController.DrivingState.PARKED,
                        clearEvidence("memory.denied", OWNER)).getCode());

        Fixture unavailable = fixture(null);
        assertEquals(MemoryConsentController.ResultCode.AUTHORITY_UNAVAILABLE,
                unavailable.controller.clearProfilePreferences(
                        OWNER,
                        MemoryConsentController.DrivingState.PARKED,
                        clearEvidence("memory.unavailable", OWNER)).getCode());

        Fixture fixture = fixture(MemoryConsentController.AuthorizationDecision.ALLOWED);
        fixture.clock.now = 301;
        assertEquals(MemoryConsentController.ResultCode.INVALID_EVIDENCE,
                fixture.controller.setRetainedMemoryEnabled(
                        OWNER,
                        false,
                        MemoryConsentController.DrivingState.PARKED,
                        retainedEvidence("memory.expired", OWNER, false)).getCode());
        assertEquals(MemoryConsentController.ResultCode.INVALID_EVIDENCE,
                fixture.controller.setRetainedMemoryEnabled(
                        OWNER,
                        false,
                        MemoryConsentController.DrivingState.PARKED,
                        retainedEvidence("memory.owner", OTHER_OWNER, false)).getCode());
        expectIllegal(() -> retainedEvidence("Not Canonical", OWNER, false));
    }

    @Test
    public void exactReplayIsIdempotentAndRequestConflictIsRejected() {
        Fixture fixture = fixture(MemoryConsentController.AuthorizationDecision.ALLOWED);
        MemoryConsentController.MutationEvidence evidence = retainedEvidence(
                "memory.replay", OWNER, false);
        MemoryConsentController.MutationResult first = fixture.controller
                .setRetainedMemoryEnabled(
                        OWNER, false, MemoryConsentController.DrivingState.PARKED, evidence);
        MemoryConsentController.MutationResult replay = fixture.controller
                .setRetainedMemoryEnabled(
                        OWNER, false, MemoryConsentController.DrivingState.PARKED, evidence);
        MemoryConsentController.MutationResult conflict = fixture.controller
                .setRetainedMemoryEnabled(
                        OWNER,
                        true,
                        MemoryConsentController.DrivingState.PARKED,
                        MemoryConsentController.MutationEvidence.forRetainedMemory(
                                "memory.replay", OWNER, true, 90, 300));

        assertEquals(MemoryConsentController.ResultCode.APPLIED, first.getCode());
        assertTrue(replay.isReplayed());
        assertEquals(MemoryConsentController.ResultCode.REQUEST_CONFLICT,
                conflict.getCode());
        assertEquals(1, fixture.authorityCalls.get());
        assertEquals(1, fixture.controller.snapshot(
                OWNER, MemoryConsentController.DrivingState.PARKED).getRevision());
    }

    @Test
    public void productionMemoryAndHardwareBoundariesRemainClosed() {
        Fixture fixture = fixture(MemoryConsentController.AuthorizationDecision.ALLOWED);
        assertTrue(fixture.controller.isHmiProjectionOnly());
        assertFalse(fixture.controller.isProductionAuthorityWired());
        assertFalse(fixture.controller.isRepositoryMutationWired());
        assertFalse(fixture.controller.isRuntimeWired());
        assertFalse(fixture.controller.isModelContextPublished());
        assertFalse(fixture.controller.isContentLoggingEnabled());
        assertFalse(fixture.controller.isHardwareAccessed());
    }

    private static Fixture fixture(
            MemoryConsentController.AuthorizationDecision decision) {
        TestClock clock = new TestClock();
        AtomicInteger calls = new AtomicInteger();
        MemoryConsentController controller = MemoryConsentController.createForContractTest(
                clock,
                (request, evidence) -> {
                    calls.incrementAndGet();
                    if (decision == null) {
                        throw new IllegalStateException("unavailable");
                    }
                    return decision;
                });
        return new Fixture(clock, calls, controller);
    }

    private static MemoryConsentController.MutationEvidence retainedEvidence(
            String requestId, String owner, boolean enabled) {
        return MemoryConsentController.MutationEvidence.forRetainedMemory(
                requestId, owner, enabled, 90, 300);
    }

    private static MemoryConsentController.MutationEvidence clearEvidence(
            String requestId, String owner) {
        return MemoryConsentController.MutationEvidence.forPreferenceClear(
                requestId, owner, 90, 300);
    }

    private static String repeat(char value, int count) {
        StringBuilder output = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }

    private static void expectIllegal(Runnable runnable) {
        try {
            runnable.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectUnsupported(Runnable runnable) {
        try {
            runnable.run();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static final class TestClock
            implements MemoryConsentController.ElapsedRealtimeClock {
        long now = 100;

        @Override
        public long nowMs() {
            return now;
        }
    }

    private static final class Fixture {
        final TestClock clock;
        final AtomicInteger authorityCalls;
        final MemoryConsentController controller;

        Fixture(
                TestClock clock,
                AtomicInteger authorityCalls,
                MemoryConsentController controller) {
            this.clock = clock;
            this.authorityCalls = authorityCalls;
            this.controller = controller;
        }
    }
}
