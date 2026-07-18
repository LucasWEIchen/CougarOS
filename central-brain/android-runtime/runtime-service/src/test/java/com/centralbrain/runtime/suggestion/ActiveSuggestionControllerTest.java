package com.centralbrain.runtime.suggestion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class ActiveSuggestionControllerTest {
    private static final String OWNER = repeat('a');
    private static final String OTHER_OWNER = repeat('b');

    @Test
    public void parkedProjectionShowsWhyCooldownAndUserActions() {
        Fixture fixture = fixture();
        assertEquals(ActiveSuggestionController.IngestCode.ADDED,
                fixture.controller.ingest(candidate(
                        "suggestion.fatigue", OWNER, "fatigue-care",
                        ActiveSuggestionController.ReasonCode.DRIVER_FATIGUE, 60_000L))
                        .getCode());

        ActiveSuggestionController.HmiSnapshot snapshot = fixture.controller.snapshot(
                OWNER, ActiveSuggestionController.DrivingState.PARKED);

        assertEquals(ActiveSuggestionController.PresentationMode.FULL_CARD,
                snapshot.getPresentationMode());
        assertEquals(1, snapshot.getCards().size());
        assertEquals("suggestion.why.driver_fatigue",
                snapshot.getCards().get(0).getWhyKey());
        assertEquals("suggestion.plan.fatigue_care",
                snapshot.getCards().get(0).getPlanKey());
        assertEquals(60_000L, snapshot.getCards().get(0).getCooldownMs());
        assertEquals(List.of(
                        ActiveSuggestionController.SuggestedAction.REVIEW,
                        ActiveSuggestionController.SuggestedAction.DISMISS,
                        ActiveSuggestionController.SuggestedAction.NEVER_ASK),
                snapshot.getCards().get(0).getActions());
        assertNull(snapshot.getMinimalVoiceKey());
    }

    @Test
    public void duplicateScopeMergesDeterministicallyByReasonPriority() {
        Fixture fixture = fixture();
        fixture.controller.ingest(candidate(
                "suggestion.cold", OWNER, "comfort-care",
                ActiveSuggestionController.ReasonCode.CABIN_TOO_COLD, 30_000L));
        ActiveSuggestionController.Candidate fatigue = candidate(
                "suggestion.fatigue", OWNER, "comfort-care",
                ActiveSuggestionController.ReasonCode.DRIVER_FATIGUE, 90_000L);
        ActiveSuggestionController.IngestResult merged = fixture.controller.ingest(fatigue);
        ActiveSuggestionController.IngestResult replay = fixture.controller.ingest(fatigue);

        ActiveSuggestionController.SuggestionCard card = fixture.controller.snapshot(
                OWNER, ActiveSuggestionController.DrivingState.PARKED).getCards().get(0);
        assertEquals(ActiveSuggestionController.IngestCode.MERGED, merged.getCode());
        assertEquals(2, merged.getMergedCount());
        assertEquals(ActiveSuggestionController.IngestCode.REPLAYED, replay.getCode());
        assertEquals(ActiveSuggestionController.ReasonCode.DRIVER_FATIGUE, card.getReason());
        assertEquals(2, card.getMergedCount());
        assertEquals(90_000L, card.getCooldownMs());
    }

    @Test
    public void neverAskIsParkedOnlyAndScopedWithoutPersistenceClaim() {
        Fixture fixture = fixture();
        fixture.controller.ingest(candidate(
                "suggestion.cold", OWNER, "comfort-care",
                ActiveSuggestionController.ReasonCode.CABIN_TOO_COLD, 60_000L));
        ActiveSuggestionController.ActionResult moving = fixture.controller.neverAsk(
                OWNER, "suggestion.cold", ActiveSuggestionController.DrivingState.MOVING);
        ActiveSuggestionController.ActionResult parked = fixture.controller.neverAsk(
                OWNER, "suggestion.cold", ActiveSuggestionController.DrivingState.PARKED);
        ActiveSuggestionController.IngestResult suppressed = fixture.controller.ingest(
                candidate("suggestion.cold.again", OWNER, "comfort-care",
                        ActiveSuggestionController.ReasonCode.CABIN_TOO_COLD, 60_000L));
        ActiveSuggestionController.IngestResult otherOwner = fixture.controller.ingest(
                candidate("suggestion.cold.other", OTHER_OWNER, "comfort-care",
                        ActiveSuggestionController.ReasonCode.CABIN_TOO_COLD, 60_000L));

        assertEquals(ActiveSuggestionController.ActionCode.DRIVING_RESTRICTED,
                moving.getCode());
        assertEquals(ActiveSuggestionController.ActionCode.APPLIED, parked.getCode());
        assertFalse(parked.isPreferencePersisted());
        assertEquals(ActiveSuggestionController.IngestCode.SUPPRESSED_NEVER_ASK,
                suppressed.getCode());
        assertEquals(ActiveSuggestionController.IngestCode.ADDED, otherOwner.getCode());
    }

    @Test
    public void movingAndUnknownUseSingleMinimalBannerAndVoiceProjectionKey() {
        Fixture fixture = fixture();
        fixture.controller.ingest(candidate(
                "suggestion.cold", OWNER, "comfort-care",
                ActiveSuggestionController.ReasonCode.CABIN_TOO_COLD, 60_000L));
        fixture.controller.ingest(candidate(
                "suggestion.fatigue", OWNER, "fatigue-care",
                ActiveSuggestionController.ReasonCode.DRIVER_FATIGUE, 60_000L));

        ActiveSuggestionController.HmiSnapshot moving = fixture.controller.snapshot(
                OWNER, ActiveSuggestionController.DrivingState.MOVING);
        ActiveSuggestionController.HmiSnapshot unknown = fixture.controller.snapshot(
                OWNER, ActiveSuggestionController.DrivingState.UNKNOWN);

        assertEquals(ActiveSuggestionController.PresentationMode.MINIMAL_BANNER,
                moving.getPresentationMode());
        assertEquals(1, moving.getCards().size());
        assertEquals(ActiveSuggestionController.ReasonCode.DRIVER_FATIGUE,
                moving.getCards().get(0).getReason());
        assertEquals(List.of(ActiveSuggestionController.SuggestedAction.DISMISS),
                moving.getCards().get(0).getActions());
        assertEquals("suggestion.voice.fatigue_care", moving.getMinimalVoiceKey());
        assertFalse(moving.isVoiceSynthesisRequested());
        assertEquals(ActiveSuggestionController.PresentationMode.MINIMAL_BANNER,
                unknown.getPresentationMode());
    }

    @Test
    public void expiryCooldownReplayConflictAndCapacityFailClosed() {
        Fixture fixture = fixture();
        ActiveSuggestionController.Candidate cold = candidate(
                "suggestion.cold", OWNER, "comfort-care",
                ActiveSuggestionController.ReasonCode.CABIN_TOO_COLD, 60_000L);
        fixture.controller.ingest(cold);
        assertEquals(ActiveSuggestionController.IngestCode.REPLAYED,
                fixture.controller.ingest(cold).getCode());
        assertEquals(ActiveSuggestionController.IngestCode.REQUEST_CONFLICT,
                fixture.controller.ingest(candidateWithFingerprint(
                        "suggestion.cold", OWNER, "comfort-care", repeat('e'))).getCode());
        fixture.controller.dismiss(
                OWNER, "suggestion.cold", ActiveSuggestionController.DrivingState.MOVING);
        assertEquals(ActiveSuggestionController.IngestCode.SUPPRESSED_COOLDOWN,
                fixture.controller.ingest(candidate(
                        "suggestion.cold.cooldown", OWNER, "comfort-care",
                        ActiveSuggestionController.ReasonCode.CABIN_TOO_COLD, 60_000L))
                        .getCode());

        Fixture capacity = fixture();
        for (int index = 0; index < ActiveSuggestionController.MAX_ACTIVE_SUGGESTIONS; index++) {
            assertEquals(ActiveSuggestionController.IngestCode.ADDED,
                    capacity.controller.ingest(candidate(
                            "suggestion.item." + index,
                            OWNER,
                            "scenario." + index,
                            ActiveSuggestionController.ReasonCode.CABIN_AIR_QUALITY,
                            0L)).getCode());
        }
        assertEquals(ActiveSuggestionController.IngestCode.CAPACITY_EXCEEDED,
                capacity.controller.ingest(candidate(
                        "suggestion.overflow", OWNER, "scenario.overflow",
                        ActiveSuggestionController.ReasonCode.CABIN_AIR_QUALITY, 0L)).getCode());

        Fixture invalid = fixture();
        invalid.clock.now = 50;
        assertEquals(ActiveSuggestionController.IngestCode.REJECTED_FUTURE,
                invalid.controller.ingest(candidate(
                        "suggestion.future", OWNER, "future",
                        ActiveSuggestionController.ReasonCode.RUNTIME_DEGRADED, 0L)).getCode());
        invalid.clock.now = 401;
        assertEquals(ActiveSuggestionController.IngestCode.EXPIRED,
                invalid.controller.ingest(candidate(
                        "suggestion.expired", OWNER, "expired",
                        ActiveSuggestionController.ReasonCode.RUNTIME_DEGRADED, 0L)).getCode());
    }

    @Test
    public void productionRuntimeEffectVoiceAndHardwareBoundariesRemainClosed() {
        Fixture fixture = fixture();
        assertTrue(fixture.controller.isHmiProjectionOnly());
        assertFalse(fixture.controller.isProductionSuggestionSourceWired());
        assertFalse(fixture.controller.isTriggerEngineWired());
        assertFalse(fixture.controller.isGraphRuntimeWired());
        assertFalse(fixture.controller.isEffectDispatchWired());
        assertFalse(fixture.controller.isVoiceEngineWired());
        assertFalse(fixture.controller.isPreferenceRepositoryWired());
        assertFalse(fixture.controller.isHardwareAccessed());
    }

    private static Fixture fixture() {
        TestClock clock = new TestClock();
        return new Fixture(clock, ActiveSuggestionController.createForContractTest(clock));
    }

    private static ActiveSuggestionController.Candidate candidate(
            String suggestionId,
            String owner,
            String scenarioId,
            ActiveSuggestionController.ReasonCode reason,
            long cooldownMs) {
        return ActiveSuggestionController.Candidate.create(
                suggestionId,
                repeat('c'),
                owner,
                scenarioId,
                "driver",
                reason,
                90,
                300,
                cooldownMs,
                repeat('d'));
    }

    private static ActiveSuggestionController.Candidate candidateWithFingerprint(
            String suggestionId, String owner, String scenarioId, String fingerprint) {
        return ActiveSuggestionController.Candidate.create(
                suggestionId,
                fingerprint,
                owner,
                scenarioId,
                "driver",
                ActiveSuggestionController.ReasonCode.CABIN_TOO_COLD,
                90,
                300,
                60_000L,
                repeat('d'));
    }

    private static String repeat(char value) {
        StringBuilder output = new StringBuilder(64);
        for (int index = 0; index < 64; index++) {
            output.append(value);
        }
        return output.toString();
    }

    private static final class TestClock
            implements ActiveSuggestionController.ElapsedRealtimeClock {
        long now = 100;

        @Override
        public long nowMs() {
            return now;
        }
    }

    private static final class Fixture {
        final TestClock clock;
        final ActiveSuggestionController controller;

        Fixture(TestClock clock, ActiveSuggestionController controller) {
            this.clock = clock;
            this.controller = controller;
        }
    }
}
