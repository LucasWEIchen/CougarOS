package com.centralbrain.runtime.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.scenario.ScenarioManifest;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class TriggerEngineTest {
    private static final String SCENARIO_DIGEST = "a".repeat(64);
    private static final String SCOPE_A = "b".repeat(64);
    private static final String SCOPE_B = "c".repeat(64);
    private static final String EVIDENCE = "d".repeat(64);

    @Test
    public void manifestIsOrderIndependentAndRejectsInvalidRules() {
        TriggerRule cold = coldRule("trigger.cold.driver.v1");
        TriggerRule fatigue = fatigueRule("trigger.fatigue.driver.v1");
        TriggerRule.Manifest first = manifest(List.of(cold, fatigue));
        TriggerRule.Manifest second = manifest(List.of(fatigue, cold));

        assertEquals(first.getManifestDigest(), second.getManifestDigest());
        assertEquals("trigger.cold.driver.v1", first.getRules().get(0).getRuleId());
        assertFalse(first.isProductionTrusted());
        assertFalse(first.isRuntimeWired());
        assertThrows(IllegalArgumentException.class,
                () -> manifest(List.of(cold, cold)));
        assertThrows(IllegalArgumentException.class, () -> new TriggerRule(
                "invalid",
                "scene.comfort.cold.v1",
                SCENARIO_DIGEST,
                TriggerRule.Metric.CABIN_TEMPERATURE_C,
                ScenarioManifest.Zone.ROW1_DRIVER,
                TriggerRule.ThresholdOperator.LESS_THAN,
                18.0,
                100,
                100,
                3,
                20,
                200,
                100));
    }

    @Test
    public void thresholdWindowAndDebounceEmitSuggestionOnly() {
        Harness harness = harness(coldRule("trigger.cold.driver.v1"));

        assertCode(TriggerEngine.EvaluationCode.ACCUMULATING_WINDOW,
                harness.evaluate("obs.1", 1_000, 17.0, SignalQuality.VALID));
        assertCode(TriggerEngine.EvaluationCode.ACCUMULATING_WINDOW,
                harness.evaluate("obs.2", 1_050, 17.0, SignalQuality.VALID));
        TriggerEngine.Evaluation debouncing = harness.evaluate(
                "obs.3", 1_100, 17.0, SignalQuality.VALID);
        assertEquals(TriggerEngine.EvaluationCode.DEBOUNCING, debouncing.getCode());
        assertEquals(100, debouncing.getConditionDurationMs());
        assertEquals(3, debouncing.getMatchingSampleCount());

        TriggerEngine.Evaluation suggested = harness.evaluate(
                "obs.4", 1_120, 17.0, SignalQuality.VALID);
        assertEquals(TriggerEngine.EvaluationCode.SUGGESTED, suggested.getCode());
        TriggerEngine.ScenarioSuggestion suggestion = suggested.getSuggestion();
        assertNotNull(suggestion);
        assertEquals(ScenarioManifest.Source.TRIGGER, suggestion.getScenarioSource());
        assertEquals("scene.comfort.cold.v1", suggestion.getScenarioId());
        assertEquals(SCOPE_A, suggestion.getScopeDigest());
        assertFalse(suggestion.isAutoExecutionRequested());
        assertFalse(suggestion.isEffectDispatchRequested());
    }

    @Test
    public void falseAndSampleGapResetWindowDeterministically() {
        Harness harness = harness(coldRule("trigger.cold.driver.v1"));
        harness.evaluate("obs.1", 1_000, 17.0, SignalQuality.VALID);
        TriggerEngine.Evaluation reset = harness.evaluate(
                "obs.2", 1_050, 19.0, SignalQuality.VALID);
        assertEquals(TriggerEngine.EvaluationCode.CONDITION_NOT_MET, reset.getCode());
        assertEquals(0, reset.getMatchingSampleCount());
        TriggerEngine.Evaluation restarted = harness.evaluate(
                "obs.3", 1_100, 17.0, SignalQuality.VALID);
        assertEquals(1, restarted.getMatchingSampleCount());

        TriggerEngine.Evaluation gapReset = harness.evaluate(
                "obs.4", 1_201, 17.0, SignalQuality.VALID);
        assertEquals(TriggerEngine.EvaluationCode.ACCUMULATING_WINDOW,
                gapReset.getCode());
        assertEquals(1, gapReset.getMatchingSampleCount());
        assertEquals(0, gapReset.getConditionDurationMs());
    }

    @Test
    public void cooldownIsAtomicScopedBoundedAndExpires() {
        CooldownStore store = CooldownStore.createForContractTest(2);
        String rule = "trigger.cold.driver.v1";
        String suggestionA = "e".repeat(64);
        String suggestionB = "f".repeat(64);

        assertEquals(CooldownStore.ReservationCode.RESERVED,
                store.reserve(rule, SCOPE_A, suggestionA, 100, 100).getCode());
        assertEquals(CooldownStore.ReservationCode.REPLAYED,
                store.reserve(rule, SCOPE_A, suggestionA, 101, 100).getCode());
        CooldownStore.Reservation active = store.reserve(
                rule, SCOPE_A, suggestionB, 110, 100);
        assertEquals(CooldownStore.ReservationCode.COOLDOWN_ACTIVE, active.getCode());
        assertEquals(90, active.getRemainingMs());
        assertEquals(CooldownStore.ReservationCode.RESERVED,
                store.reserve(rule, SCOPE_B, suggestionB, 110, 100).getCode());
        assertEquals(CooldownStore.ReservationCode.RESERVED,
                store.reserve(rule, SCOPE_A, suggestionB, 200, 100).getCode());

        CooldownStore.Snapshot snapshot = store.snapshot(200);
        assertEquals(2, snapshot.getActiveEntryCount());
        assertEquals(3, snapshot.getReservedCount());
        assertEquals(1, snapshot.getReplayedCount());
        assertEquals(1, snapshot.getSuppressedCount());
        assertTrue(snapshot.isProcessLocal());
        assertFalse(snapshot.isDurablePersistenceWired());

        CooldownStore full = CooldownStore.createForContractTest(1);
        full.reserve(rule, SCOPE_A, suggestionA, 100, 100);
        assertEquals(CooldownStore.ReservationCode.CAPACITY_EXCEEDED,
                full.reserve(rule, SCOPE_B, suggestionB, 101, 100).getCode());
    }

    @Test
    public void qualityFreshnessOrderingAndReplayFailClosed() {
        Harness harness = harness(coldRule("trigger.cold.driver.v1"));
        assertCode(TriggerEngine.EvaluationCode.REJECTED_STALE,
                harness.evaluateAt("obs.stale", 899, 1_000, 17.0, SignalQuality.VALID));
        assertCode(TriggerEngine.EvaluationCode.REJECTED_FUTURE,
                harness.evaluateAt("obs.future", 1_001, 1_000, 17.0, SignalQuality.VALID));
        assertCode(TriggerEngine.EvaluationCode.REJECTED_QUALITY,
                harness.evaluateAt("obs.quality", 1_000, 1_000, null,
                        SignalQuality.UNAVAILABLE));
        assertEquals(0, harness.engine.snapshot().getStateCount());

        TriggerEngine.Observation valid = observation(
                "obs.valid", 1_010, 17.0, SignalQuality.VALID, SCOPE_A);
        harness.now.set(1_010);
        TriggerEngine.EvaluationBatch first = harness.engine.evaluate(valid);
        assertEquals(TriggerEngine.BatchCode.EVALUATED, first.getCode());
        assertEquals(TriggerEngine.BatchCode.REPLAYED,
                harness.engine.evaluate(valid).getCode());
        TriggerEngine.Observation conflict = observation(
                "obs.valid", 1_011, 17.0, SignalQuality.VALID, SCOPE_A);
        harness.now.set(1_011);
        assertEquals(TriggerEngine.BatchCode.REQUEST_CONFLICT,
                harness.engine.evaluate(conflict).getCode());
        assertCode(TriggerEngine.EvaluationCode.REJECTED_OUT_OF_ORDER,
                harness.evaluateAt("obs.out-of-order", 1_010, 1_011,
                        17.0, SignalQuality.VALID));
    }

    @Test
    public void scopesAndProductionBoundariesRemainClosed() {
        Harness harness = harness(coldRule("trigger.cold.driver.v1"));
        assertEquals(TriggerEngine.BatchCode.NO_MATCHING_RULE,
                harness.evaluateFatigue("obs.no-rule", 1_000, 0.9).getCode());
        harness.evaluate("obs.a1", 1_001, 17.0, SignalQuality.VALID);
        harness.evaluateScope("obs.b1", 1_001, 17.0, SCOPE_B);
        assertEquals(2, harness.engine.snapshot().getStateCount());

        TriggerEngine.EngineSnapshot snapshot = harness.engine.snapshot();
        assertTrue(snapshot.isSuggestionOnly());
        assertFalse(snapshot.isAutoExecutionEnabled());
        assertFalse(snapshot.isEffectDispatchEnabled());
        assertFalse(snapshot.isSourceAdapterWired());
        assertFalse(snapshot.isRuntimeWired());
        assertFalse(snapshot.isModelInvoked());
        assertFalse(snapshot.isHardwareAccessed());
        assertNull(harness.evaluate("obs.not-met", 1_010, 19.0,
                SignalQuality.VALID).getSuggestion());
    }

    private static void assertCode(
            TriggerEngine.EvaluationCode expected,
            TriggerEngine.Evaluation actual) {
        assertEquals(expected, actual.getCode());
    }

    private static TriggerRule coldRule(String ruleId) {
        return new TriggerRule(
                ruleId,
                "scene.comfort.cold.v1",
                SCENARIO_DIGEST,
                TriggerRule.Metric.CABIN_TEMPERATURE_C,
                ScenarioManifest.Zone.ROW1_DRIVER,
                TriggerRule.ThresholdOperator.LESS_THAN,
                18.0,
                100,
                100,
                3,
                20,
                200,
                100);
    }

    private static TriggerRule fatigueRule(String ruleId) {
        return new TriggerRule(
                ruleId,
                "scene.fatigue.assist.v1",
                "9".repeat(64),
                TriggerRule.Metric.DRIVER_FATIGUE_SCORE,
                ScenarioManifest.Zone.ROW1_DRIVER,
                TriggerRule.ThresholdOperator.GREATER_THAN_OR_EQUAL,
                0.8,
                100,
                100,
                3,
                20,
                200,
                100);
    }

    private static TriggerRule.Manifest manifest(List<TriggerRule> rules) {
        return new TriggerRule.Manifest("trigger-manifest.default.v1", 1, rules);
    }

    private static Harness harness(TriggerRule rule) {
        AtomicLong now = new AtomicLong(1_000);
        TriggerEngine engine = TriggerEngine.createForContractTest(
                manifest(List.of(rule)),
                CooldownStore.createForContractTest(16),
                now::get);
        return new Harness(engine, now);
    }

    private static TriggerEngine.Observation observation(
            String observationId,
            long observedAt,
            Double value,
            SignalQuality quality,
            String scope) {
        return new TriggerEngine.Observation(
                observationId,
                TriggerRule.Metric.CABIN_TEMPERATURE_C,
                ScenarioManifest.Zone.ROW1_DRIVER,
                scope,
                observedAt,
                quality,
                value,
                EVIDENCE);
    }

    private static final class Harness {
        final TriggerEngine engine;
        final AtomicLong now;

        Harness(TriggerEngine engine, AtomicLong now) {
            this.engine = engine;
            this.now = now;
        }

        TriggerEngine.Evaluation evaluate(
                String observationId,
                long timestamp,
                Double value,
                SignalQuality quality) {
            return evaluateAt(observationId, timestamp, timestamp, value, quality);
        }

        TriggerEngine.Evaluation evaluateAt(
                String observationId,
                long observedAt,
                long evaluatedAt,
                Double value,
                SignalQuality quality) {
            now.set(evaluatedAt);
            return engine.evaluate(observation(
                    observationId,
                    observedAt,
                    value,
                    quality,
                    SCOPE_A)).getEvaluations().get(0);
        }

        TriggerEngine.Evaluation evaluateScope(
                String observationId,
                long timestamp,
                Double value,
                String scope) {
            now.set(timestamp);
            return engine.evaluate(observation(
                    observationId,
                    timestamp,
                    value,
                    SignalQuality.VALID,
                    scope)).getEvaluations().get(0);
        }

        TriggerEngine.EvaluationBatch evaluateFatigue(
                String observationId,
                long timestamp,
                double value) {
            now.set(timestamp);
            return engine.evaluate(new TriggerEngine.Observation(
                    observationId,
                    TriggerRule.Metric.DRIVER_FATIGUE_SCORE,
                    ScenarioManifest.Zone.ROW1_DRIVER,
                    SCOPE_A,
                    timestamp,
                    SignalQuality.VALID,
                    value,
                    EVIDENCE));
        }
    }
}
