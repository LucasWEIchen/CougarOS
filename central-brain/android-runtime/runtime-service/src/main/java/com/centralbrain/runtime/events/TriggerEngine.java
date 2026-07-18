package com.centralbrain.runtime.events;

import com.centralbrain.runtime.scenario.ScenarioManifest;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Deterministic process-local trigger evaluator that emits suggestion metadata only. */
public final class TriggerEngine {
    public static final int MAX_RULE_STATES = 256;
    private static final int MAX_OBSERVATION_TOMBSTONES = 256;

    public enum BatchCode {
        EVALUATED,
        REPLAYED,
        REQUEST_CONFLICT,
        NO_MATCHING_RULE
    }

    public enum EvaluationCode {
        CONDITION_NOT_MET,
        ACCUMULATING_WINDOW,
        DEBOUNCING,
        COOLDOWN_ACTIVE,
        SUGGESTED,
        SUGGESTION_REPLAYED,
        REJECTED_QUALITY,
        REJECTED_STALE,
        REJECTED_FUTURE,
        REJECTED_OUT_OF_ORDER,
        STATE_CAPACITY_EXCEEDED,
        COOLDOWN_CAPACITY_EXCEEDED
    }

    public static final class Observation {
        private final String observationId;
        private final TriggerRule.Metric metric;
        private final ScenarioManifest.Zone zone;
        private final String scopeDigest;
        private final long observedAtElapsedMs;
        private final SignalQuality quality;
        private final Double value;
        private final String sourceEvidenceDigest;
        private final String observationDigest;

        public Observation(
                String observationId,
                TriggerRule.Metric metric,
                ScenarioManifest.Zone zone,
                String scopeDigest,
                long observedAtElapsedMs,
                SignalQuality quality,
                Double value,
                String sourceEvidenceDigest) {
            this.observationId = EventBroker.requireMetadata(
                    observationId,
                    "observationId",
                    128);
            this.metric = Objects.requireNonNull(metric, "metric");
            this.zone = Objects.requireNonNull(zone, "zone");
            this.scopeDigest = EventBroker.requireDigest(scopeDigest, "scopeDigest");
            if (observedAtElapsedMs < 0) {
                throw new IllegalArgumentException("observedAtElapsedMs is invalid");
            }
            this.observedAtElapsedMs = observedAtElapsedMs;
            this.quality = Objects.requireNonNull(quality, "quality");
            if (quality.hasScalarValue()) {
                if (value == null) {
                    throw new IllegalArgumentException("scalar quality requires value");
                }
                metric.validateValue(value, "value");
                this.value = value;
            } else {
                if (value != null) {
                    throw new IllegalArgumentException("unavailable quality cannot carry value");
                }
                this.value = null;
            }
            this.sourceEvidenceDigest = EventBroker.requireDigest(
                    sourceEvidenceDigest,
                    "sourceEvidenceDigest");
            this.observationDigest = EventBroker.digest(canonical());
        }

        public String getObservationId() {
            return observationId;
        }

        public TriggerRule.Metric getMetric() {
            return metric;
        }

        public ScenarioManifest.Zone getZone() {
            return zone;
        }

        public String getScopeDigest() {
            return scopeDigest;
        }

        public long getObservedAtElapsedMs() {
            return observedAtElapsedMs;
        }

        public SignalQuality getQuality() {
            return quality;
        }

        public Double getValue() {
            return value;
        }

        public String getSourceEvidenceDigest() {
            return sourceEvidenceDigest;
        }

        public String getObservationDigest() {
            return observationDigest;
        }

        private String canonical() {
            return observationId + "|" + metric.name() + "|" + zone.name() + "|"
                    + scopeDigest + "|" + observedAtElapsedMs + "|" + quality.name()
                    + "|" + (value == null
                    ? "none"
                    : Long.toHexString(Double.doubleToLongBits(value)))
                    + "|" + sourceEvidenceDigest;
        }
    }

    /** Suggestion output has no command, user text, model text, or Effect payload. */
    public static final class ScenarioSuggestion {
        private final String suggestionDigest;
        private final String ruleId;
        private final String ruleManifestDigest;
        private final String scenarioId;
        private final String scenarioManifestDigest;
        private final String scopeDigest;
        private final TriggerRule.Metric metric;
        private final ScenarioManifest.Zone zone;
        private final String observationDigest;
        private final long suggestedAtElapsedMs;

        ScenarioSuggestion(
                String suggestionDigest,
                TriggerRule rule,
                String ruleManifestDigest,
                Observation observation,
                long suggestedAtElapsedMs) {
            this.suggestionDigest = EventBroker.requireDigest(
                    suggestionDigest,
                    "suggestionDigest");
            this.ruleId = rule.getRuleId();
            this.ruleManifestDigest = ruleManifestDigest;
            this.scenarioId = rule.getScenarioId();
            this.scenarioManifestDigest = rule.getScenarioManifestDigest();
            this.scopeDigest = observation.scopeDigest;
            this.metric = rule.getMetric();
            this.zone = rule.getZone();
            this.observationDigest = observation.observationDigest;
            this.suggestedAtElapsedMs = suggestedAtElapsedMs;
        }

        public String getSuggestionDigest() {
            return suggestionDigest;
        }

        public String getRuleId() {
            return ruleId;
        }

        public String getRuleManifestDigest() {
            return ruleManifestDigest;
        }

        public String getScenarioId() {
            return scenarioId;
        }

        public String getScenarioManifestDigest() {
            return scenarioManifestDigest;
        }

        public String getScopeDigest() {
            return scopeDigest;
        }

        public TriggerRule.Metric getMetric() {
            return metric;
        }

        public ScenarioManifest.Zone getZone() {
            return zone;
        }

        public String getObservationDigest() {
            return observationDigest;
        }

        public long getSuggestedAtElapsedMs() {
            return suggestedAtElapsedMs;
        }

        public ScenarioManifest.Source getScenarioSource() {
            return ScenarioManifest.Source.TRIGGER;
        }

        public boolean isAutoExecutionRequested() {
            return false;
        }

        public boolean isEffectDispatchRequested() {
            return false;
        }
    }

    public static final class Evaluation {
        private final String ruleId;
        private final EvaluationCode code;
        private final long conditionDurationMs;
        private final int matchingSampleCount;
        private final long cooldownRemainingMs;
        private final ScenarioSuggestion suggestion;

        Evaluation(
                String ruleId,
                EvaluationCode code,
                long conditionDurationMs,
                int matchingSampleCount,
                long cooldownRemainingMs,
                ScenarioSuggestion suggestion) {
            this.ruleId = ruleId;
            this.code = Objects.requireNonNull(code, "code");
            this.conditionDurationMs = conditionDurationMs;
            this.matchingSampleCount = matchingSampleCount;
            this.cooldownRemainingMs = cooldownRemainingMs;
            this.suggestion = suggestion;
        }

        public String getRuleId() {
            return ruleId;
        }

        public EvaluationCode getCode() {
            return code;
        }

        public long getConditionDurationMs() {
            return conditionDurationMs;
        }

        public int getMatchingSampleCount() {
            return matchingSampleCount;
        }

        public long getCooldownRemainingMs() {
            return cooldownRemainingMs;
        }

        public ScenarioSuggestion getSuggestion() {
            return suggestion;
        }
    }

    public static final class EvaluationBatch {
        private final BatchCode code;
        private final List<Evaluation> evaluations;
        private final EngineSnapshot snapshot;

        EvaluationBatch(
                BatchCode code,
                List<Evaluation> evaluations,
                EngineSnapshot snapshot) {
            this.code = Objects.requireNonNull(code, "code");
            this.evaluations = Collections.unmodifiableList(
                    new ArrayList<>(evaluations));
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }

        public BatchCode getCode() {
            return code;
        }

        public List<Evaluation> getEvaluations() {
            return evaluations;
        }

        public EngineSnapshot getSnapshot() {
            return snapshot;
        }
    }

    public static final class EngineSnapshot {
        private final int ruleCount;
        private final int stateCount;
        private final long evaluatedObservationCount;
        private final long suggestionCount;
        private final long cooldownSuppressedCount;
        private final long rejectedObservationCount;
        private final long replayedObservationCount;

        EngineSnapshot(
                int ruleCount,
                int stateCount,
                long evaluatedObservationCount,
                long suggestionCount,
                long cooldownSuppressedCount,
                long rejectedObservationCount,
                long replayedObservationCount) {
            this.ruleCount = ruleCount;
            this.stateCount = stateCount;
            this.evaluatedObservationCount = evaluatedObservationCount;
            this.suggestionCount = suggestionCount;
            this.cooldownSuppressedCount = cooldownSuppressedCount;
            this.rejectedObservationCount = rejectedObservationCount;
            this.replayedObservationCount = replayedObservationCount;
        }

        public int getRuleCount() {
            return ruleCount;
        }

        public int getStateCount() {
            return stateCount;
        }

        public long getEvaluatedObservationCount() {
            return evaluatedObservationCount;
        }

        public long getSuggestionCount() {
            return suggestionCount;
        }

        public long getCooldownSuppressedCount() {
            return cooldownSuppressedCount;
        }

        public long getRejectedObservationCount() {
            return rejectedObservationCount;
        }

        public long getReplayedObservationCount() {
            return replayedObservationCount;
        }

        public boolean isSuggestionOnly() {
            return true;
        }

        public boolean isAutoExecutionEnabled() {
            return false;
        }

        public boolean isEffectDispatchEnabled() {
            return false;
        }

        public boolean isSourceAdapterWired() {
            return false;
        }

        public boolean isRuntimeWired() {
            return false;
        }

        public boolean isModelInvoked() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }
    }

    private final TriggerRule.Manifest manifest;
    private final CooldownStore cooldownStore;
    private final LongSupplier elapsedRealtimeMs;
    private final LinkedHashMap<String, RuleState> states = new LinkedHashMap<>();
    private final LinkedHashMap<String, ObservationReplay> replayByObservation =
            new LinkedHashMap<>();
    private long evaluatedObservationCount;
    private long suggestionCount;
    private long cooldownSuppressedCount;
    private long rejectedObservationCount;
    private long replayedObservationCount;

    private TriggerEngine(
            TriggerRule.Manifest manifest,
            CooldownStore cooldownStore,
            LongSupplier elapsedRealtimeMs) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.cooldownStore = Objects.requireNonNull(cooldownStore, "cooldownStore");
        this.elapsedRealtimeMs = Objects.requireNonNull(
                elapsedRealtimeMs,
                "elapsedRealtimeMs");
    }

    public static TriggerEngine createForContractTest(
            TriggerRule.Manifest manifest,
            CooldownStore cooldownStore,
            LongSupplier elapsedRealtimeMs) {
        return new TriggerEngine(manifest, cooldownStore, elapsedRealtimeMs);
    }

    public synchronized EvaluationBatch evaluate(Observation observation) {
        Objects.requireNonNull(observation, "observation");
        ObservationReplay replay = replayByObservation.get(observation.observationId);
        if (replay != null) {
            if (!replay.observationDigest.equals(observation.observationDigest)) {
                rejectedObservationCount++;
                return new EvaluationBatch(
                        BatchCode.REQUEST_CONFLICT,
                        List.of(),
                        snapshot());
            }
            replayedObservationCount++;
            return new EvaluationBatch(
                    BatchCode.REPLAYED,
                    replay.evaluations,
                    snapshot());
        }

        long now = now();
        List<TriggerRule> matchingRules = matchingRules(observation);
        if (matchingRules.isEmpty()) {
            evaluatedObservationCount++;
            storeReplay(observation, List.of());
            return new EvaluationBatch(
                    BatchCode.NO_MATCHING_RULE,
                    List.of(),
                    snapshot());
        }

        List<Evaluation> evaluations = new ArrayList<>(matchingRules.size());
        for (TriggerRule rule : matchingRules) {
            evaluations.add(evaluateRule(rule, observation, now));
        }
        evaluatedObservationCount++;
        storeReplay(observation, evaluations);
        return new EvaluationBatch(BatchCode.EVALUATED, evaluations, snapshot());
    }

    public synchronized EngineSnapshot snapshot() {
        return new EngineSnapshot(
                manifest.getRules().size(),
                states.size(),
                evaluatedObservationCount,
                suggestionCount,
                cooldownSuppressedCount,
                rejectedObservationCount,
                replayedObservationCount);
    }

    private Evaluation evaluateRule(
            TriggerRule rule,
            Observation observation,
            long now) {
        if (observation.observedAtElapsedMs > now) {
            rejectedObservationCount++;
            return evaluation(rule, EvaluationCode.REJECTED_FUTURE, null, 0, 0);
        }
        if (!observation.quality.isUsableForDecision()) {
            rejectedObservationCount++;
            return evaluation(rule, EvaluationCode.REJECTED_QUALITY, null, 0, 0);
        }
        if (now - observation.observedAtElapsedMs > rule.getMaximumObservationAgeMs()) {
            rejectedObservationCount++;
            return evaluation(rule, EvaluationCode.REJECTED_STALE, null, 0, 0);
        }
        String stateKey = rule.getRuleId() + '|' + observation.scopeDigest;
        RuleState state = states.get(stateKey);
        if (state == null) {
            if (states.size() >= MAX_RULE_STATES) {
                rejectedObservationCount++;
                return evaluation(
                        rule,
                        EvaluationCode.STATE_CAPACITY_EXCEEDED,
                        null,
                        0,
                        0);
            }
            state = new RuleState();
            states.put(stateKey, state);
        }
        long previousObservedAt = state.lastObservedAtElapsedMs;
        if (previousObservedAt >= observation.observedAtElapsedMs) {
            rejectedObservationCount++;
            return evaluation(
                    rule,
                    EvaluationCode.REJECTED_OUT_OF_ORDER,
                    null,
                    state.durationAt(previousObservedAt),
                    state.matchingSampleCount);
        }
        state.lastObservedAtElapsedMs = observation.observedAtElapsedMs;

        if (!rule.matches(observation.value)) {
            state.resetCondition();
            return evaluation(rule, EvaluationCode.CONDITION_NOT_MET, null, 0, 0);
        }

        if (state.conditionSinceElapsedMs < 0
                || previousObservedAt < 0
                || observation.observedAtElapsedMs - previousObservedAt
                > rule.getMaximumSampleGapMs()) {
            state.startCondition(observation.observedAtElapsedMs);
        } else if (state.matchingSampleCount < TriggerRule.MAX_MATCHING_SAMPLES) {
            state.matchingSampleCount++;
        }
        long duration = state.durationAt(observation.observedAtElapsedMs);
        if (duration < rule.getSustainWindowMs()
                || state.matchingSampleCount < rule.getMinimumMatchingSamples()) {
            return evaluation(
                    rule,
                    EvaluationCode.ACCUMULATING_WINDOW,
                    null,
                    duration,
                    state.matchingSampleCount);
        }

        if (state.debounceSinceElapsedMs < 0) {
            state.debounceSinceElapsedMs = observation.observedAtElapsedMs;
        }
        if (observation.observedAtElapsedMs - state.debounceSinceElapsedMs
                < rule.getDebounceMs()) {
            return evaluation(
                    rule,
                    EvaluationCode.DEBOUNCING,
                    null,
                    duration,
                    state.matchingSampleCount);
        }

        String suggestionDigest = EventBroker.digest(
                "trigger-suggestion|" + manifest.getManifestDigest() + "|"
                        + rule.getRuleDigest() + "|" + observation.observationDigest
                        + "|" + observation.scopeDigest);
        CooldownStore.Reservation reservation = cooldownStore.reserve(
                rule.getRuleId(),
                observation.scopeDigest,
                suggestionDigest,
                now,
                rule.getCooldownMs());
        switch (reservation.getCode()) {
            case RESERVED:
                suggestionCount++;
                return evaluation(
                        rule,
                        EvaluationCode.SUGGESTED,
                        suggestion(rule, observation, suggestionDigest, now),
                        duration,
                        state.matchingSampleCount);
            case REPLAYED:
                return evaluation(
                        rule,
                        EvaluationCode.SUGGESTION_REPLAYED,
                        suggestion(rule, observation, suggestionDigest, now),
                        duration,
                        state.matchingSampleCount);
            case COOLDOWN_ACTIVE:
                cooldownSuppressedCount++;
                return new Evaluation(
                        rule.getRuleId(),
                        EvaluationCode.COOLDOWN_ACTIVE,
                        duration,
                        state.matchingSampleCount,
                        reservation.getRemainingMs(),
                        null);
            case CAPACITY_EXCEEDED:
                rejectedObservationCount++;
                return evaluation(
                        rule,
                        EvaluationCode.COOLDOWN_CAPACITY_EXCEEDED,
                        null,
                        duration,
                        state.matchingSampleCount);
            default:
                throw new IllegalStateException("unknown cooldown reservation code");
        }
    }

    private ScenarioSuggestion suggestion(
            TriggerRule rule,
            Observation observation,
            String suggestionDigest,
            long now) {
        return new ScenarioSuggestion(
                suggestionDigest,
                rule,
                manifest.getManifestDigest(),
                observation,
                now);
    }

    private Evaluation evaluation(
            TriggerRule rule,
            EvaluationCode code,
            ScenarioSuggestion suggestion,
            long duration,
            int samples) {
        return new Evaluation(rule.getRuleId(), code, duration, samples, 0, suggestion);
    }

    private List<TriggerRule> matchingRules(Observation observation) {
        List<TriggerRule> result = new ArrayList<>();
        for (TriggerRule rule : manifest.getRules()) {
            if (rule.getMetric() == observation.metric
                    && rule.getZone() == observation.zone) {
                result.add(rule);
            }
        }
        return result;
    }

    private void storeReplay(Observation observation, List<Evaluation> evaluations) {
        replayByObservation.put(
                observation.observationId,
                new ObservationReplay(observation.observationDigest, evaluations));
        while (replayByObservation.size() > MAX_OBSERVATION_TOMBSTONES) {
            Iterator<Map.Entry<String, ObservationReplay>> iterator =
                    replayByObservation.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private long now() {
        long value = elapsedRealtimeMs.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("elapsed realtime must be non-negative");
        }
        return value;
    }

    private static final class RuleState {
        long lastObservedAtElapsedMs = -1;
        long conditionSinceElapsedMs = -1;
        long debounceSinceElapsedMs = -1;
        int matchingSampleCount;

        void startCondition(long observedAtElapsedMs) {
            conditionSinceElapsedMs = observedAtElapsedMs;
            debounceSinceElapsedMs = -1;
            matchingSampleCount = 1;
        }

        void resetCondition() {
            conditionSinceElapsedMs = -1;
            debounceSinceElapsedMs = -1;
            matchingSampleCount = 0;
        }

        long durationAt(long observedAtElapsedMs) {
            return conditionSinceElapsedMs < 0
                    ? 0
                    : Math.max(0, observedAtElapsedMs - conditionSinceElapsedMs);
        }
    }

    private static final class ObservationReplay {
        final String observationDigest;
        final List<Evaluation> evaluations;

        ObservationReplay(String observationDigest, List<Evaluation> evaluations) {
            this.observationDigest = observationDigest;
            this.evaluations = Collections.unmodifiableList(
                    new ArrayList<>(evaluations));
        }
    }
}
