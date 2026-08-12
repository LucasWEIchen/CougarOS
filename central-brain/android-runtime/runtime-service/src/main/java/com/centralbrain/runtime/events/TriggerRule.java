package com.centralbrain.runtime.events;

import com.centralbrain.runtime.scenario.ScenarioManifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable build-owned rule metadata for proactive scenario suggestions. */
public final class TriggerRule {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_RULES = 64;
    public static final long MAX_WINDOW_MS = 3_600_000L;
    public static final long MAX_DEBOUNCE_MS = 60_000L;
    public static final long MAX_COOLDOWN_MS = 86_400_000L;
    public static final int MAX_MATCHING_SAMPLES = 32;

    private static final Pattern RULE_ID =
            Pattern.compile("trigger[.][a-z0-9][a-z0-9_.-]{2,95}");
    private static final Pattern MANIFEST_ID =
            Pattern.compile("trigger-manifest[.][a-z0-9][a-z0-9_.-]{2,95}");
    private static final Pattern SCENARIO_ID =
            Pattern.compile("scene[.][a-z0-9][a-z0-9_.-]{2,95}");

    public enum Metric {
        CABIN_TEMPERATURE_C("metric.cabin.temperature_c", -50.0, 80.0),
        DRIVER_FATIGUE_SCORE("metric.driver.fatigue_score", 0.0, 1.0),
        DRIVER_ATTENTION_SCORE("metric.driver.attention_score", 0.0, 1.0),
        CABIN_CO2_PPM("metric.cabin.co2_ppm", 0.0, 20_000.0),
        CABIN_IMAGE_AVAILABLE("metric.cabin.image_available", 0.0, 1.0);

        private final String metricId;
        private final double minimum;
        private final double maximum;

        Metric(String metricId, double minimum, double maximum) {
            this.metricId = metricId;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        public String getMetricId() {
            return metricId;
        }

        void validateValue(double value, String field) {
            if (!Double.isFinite(value) || value < minimum || value > maximum) {
                throw new IllegalArgumentException(field + " is outside metric range");
            }
        }
    }

    public enum ThresholdOperator {
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL;

        boolean matches(double value, double threshold) {
            switch (this) {
                case LESS_THAN:
                    return value < threshold;
                case LESS_THAN_OR_EQUAL:
                    return value <= threshold;
                case GREATER_THAN:
                    return value > threshold;
                case GREATER_THAN_OR_EQUAL:
                    return value >= threshold;
                default:
                    throw new IllegalStateException("unknown threshold operator");
            }
        }
    }

    private final String ruleId;
    private final String scenarioId;
    private final String scenarioManifestDigest;
    private final Metric metric;
    private final ScenarioManifest.Zone zone;
    private final ThresholdOperator thresholdOperator;
    private final double threshold;
    private final long sustainWindowMs;
    private final long maximumSampleGapMs;
    private final int minimumMatchingSamples;
    private final long debounceMs;
    private final long cooldownMs;
    private final long maximumObservationAgeMs;
    private final String ruleDigest;

    public TriggerRule(
            String ruleId,
            String scenarioId,
            String scenarioManifestDigest,
            Metric metric,
            ScenarioManifest.Zone zone,
            ThresholdOperator thresholdOperator,
            double threshold,
            long sustainWindowMs,
            long maximumSampleGapMs,
            int minimumMatchingSamples,
            long debounceMs,
            long cooldownMs,
            long maximumObservationAgeMs) {
        this.ruleId = requireId(ruleId, RULE_ID, "ruleId");
        this.scenarioId = requireId(scenarioId, SCENARIO_ID, "scenarioId");
        this.scenarioManifestDigest = EventBroker.requireDigest(
                scenarioManifestDigest,
                "scenarioManifestDigest");
        this.metric = Objects.requireNonNull(metric, "metric");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.thresholdOperator = Objects.requireNonNull(
                thresholdOperator,
                "thresholdOperator");
        metric.validateValue(threshold, "threshold");
        this.threshold = threshold;
        if (sustainWindowMs < 1 || sustainWindowMs > MAX_WINDOW_MS) {
            throw new IllegalArgumentException("sustainWindowMs is invalid");
        }
        this.sustainWindowMs = sustainWindowMs;
        if (maximumSampleGapMs < 1 || maximumSampleGapMs > sustainWindowMs) {
            throw new IllegalArgumentException("maximumSampleGapMs is invalid");
        }
        this.maximumSampleGapMs = maximumSampleGapMs;
        if (minimumMatchingSamples < 2
                || minimumMatchingSamples > MAX_MATCHING_SAMPLES) {
            throw new IllegalArgumentException("minimumMatchingSamples is invalid");
        }
        this.minimumMatchingSamples = minimumMatchingSamples;
        if (debounceMs < 0 || debounceMs > MAX_DEBOUNCE_MS) {
            throw new IllegalArgumentException("debounceMs is invalid");
        }
        this.debounceMs = debounceMs;
        if (cooldownMs < 1 || cooldownMs > MAX_COOLDOWN_MS) {
            throw new IllegalArgumentException("cooldownMs is invalid");
        }
        this.cooldownMs = cooldownMs;
        if (maximumObservationAgeMs < 1
                || maximumObservationAgeMs > maximumSampleGapMs) {
            throw new IllegalArgumentException("maximumObservationAgeMs is invalid");
        }
        this.maximumObservationAgeMs = maximumObservationAgeMs;
        this.ruleDigest = EventBroker.digest(canonical());
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public String getScenarioManifestDigest() {
        return scenarioManifestDigest;
    }

    public Metric getMetric() {
        return metric;
    }

    public ScenarioManifest.Zone getZone() {
        return zone;
    }

    public ThresholdOperator getThresholdOperator() {
        return thresholdOperator;
    }

    public double getThreshold() {
        return threshold;
    }

    public long getSustainWindowMs() {
        return sustainWindowMs;
    }

    public long getMaximumSampleGapMs() {
        return maximumSampleGapMs;
    }

    public int getMinimumMatchingSamples() {
        return minimumMatchingSamples;
    }

    public long getDebounceMs() {
        return debounceMs;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public long getMaximumObservationAgeMs() {
        return maximumObservationAgeMs;
    }

    public String getRuleDigest() {
        return ruleDigest;
    }

    boolean matches(double value) {
        return thresholdOperator.matches(value, threshold);
    }

    private String canonical() {
        return SCHEMA_VERSION + "|" + ruleId + "|" + scenarioId + "|"
                + scenarioManifestDigest + "|" + metric.name() + "|" + zone.name()
                + "|" + thresholdOperator.name() + "|"
                + Long.toHexString(Double.doubleToLongBits(threshold)) + "|"
                + sustainWindowMs + "|" + maximumSampleGapMs + "|"
                + minimumMatchingSamples + "|" + debounceMs + "|" + cooldownMs
                + "|" + maximumObservationAgeMs;
    }

    private static String requireId(String value, Pattern pattern, String field) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    /** Versioned immutable rule catalog. It is metadata, not an activation authority. */
    public static final class Manifest {
        private final String manifestId;
        private final long manifestEpoch;
        private final List<TriggerRule> rules;
        private final String manifestDigest;

        public Manifest(String manifestId, long manifestEpoch, List<TriggerRule> rules) {
            this.manifestId = requireId(manifestId, MANIFEST_ID, "manifestId");
            if (manifestEpoch < 1) {
                throw new IllegalArgumentException("manifestEpoch must be positive");
            }
            this.manifestEpoch = manifestEpoch;
            if (rules == null || rules.isEmpty() || rules.size() > MAX_RULES) {
                throw new IllegalArgumentException("trigger rule count is invalid");
            }
            List<TriggerRule> ordered = new ArrayList<>(rules.size());
            Set<String> ruleIds = new HashSet<>();
            for (TriggerRule rule : rules) {
                TriggerRule checked = Objects.requireNonNull(rule, "rule");
                if (!ruleIds.add(checked.ruleId)) {
                    throw new IllegalArgumentException("duplicate trigger ruleId");
                }
                ordered.add(checked);
            }
            ordered.sort(Comparator.comparing(TriggerRule::getRuleId));
            this.rules = Collections.unmodifiableList(ordered);
            StringBuilder canonical = new StringBuilder()
                    .append(SCHEMA_VERSION).append('|')
                    .append(manifestId).append('|')
                    .append(manifestEpoch);
            for (TriggerRule rule : ordered) {
                canonical.append('|').append(rule.ruleDigest);
            }
            this.manifestDigest = EventBroker.digest(canonical.toString());
        }

        public String getManifestId() {
            return manifestId;
        }

        public long getManifestEpoch() {
            return manifestEpoch;
        }

        public List<TriggerRule> getRules() {
            return rules;
        }

        public String getManifestDigest() {
            return manifestDigest;
        }

        public boolean isProductionTrusted() {
            return false;
        }

        public boolean isRuntimeWired() {
            return false;
        }
    }
}
