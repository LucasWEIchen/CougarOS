package com.centralbrain.runtime.performance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fixed P9-W01 application performance budgets and strict aggregate report validation.
 * It consumes measurements but never reads Android, vehicle, model, NPU, or hardware state.
 * Req IDs: S2-OBS-001, S2-REL-001, XSC-001/004/005/006, KH-003/006, DEL-001/004/005.
 */
public final class PerformanceBudgetContract {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-app-baseline-v1";
    public static final int METRIC_CATEGORY_COUNT = 7;
    public static final int METRIC_COUNT = 10;
    public static final int TARGET_MINIMUM_SAMPLES = 30;

    private static final Pattern RELEASE_TAG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern SOURCE_COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern DEVICE_ALIAS = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final List<Budget> CATALOG = buildCatalog();
    private static final String CATALOG_DIGEST = sha256(canonicalCatalog());

    private PerformanceBudgetContract() {
    }

    public enum Category {
        BINDER,
        PLAN,
        EFFECT_DISPATCH,
        DATABASE,
        MEMORY,
        CPU,
        STARTUP
    }

    public enum Aggregation {
        P95,
        MAX
    }

    public enum Unit {
        MICROSECONDS,
        BYTES,
        PERMILLE_SINGLE_CORE
    }

    public enum EvidenceMode {
        CONTRACT_TEST,
        ANDROID_APPLICATION,
        TARGET_ANDROID13
    }

    public enum MetricId {
        BINDER_PROTOCOL(
                "binder.protocol_negotiation.latency_us",
                Category.BINDER,
                Aggregation.P95,
                Unit.MICROSECONDS,
                10_000L),
        BINDER_SESSION_OPEN(
                "binder.session_open.latency_us",
                Category.BINDER,
                Aggregation.P95,
                Unit.MICROSECONDS,
                50_000L),
        BINDER_READ_CANCEL(
                "binder.read_cancel.latency_us",
                Category.BINDER,
                Aggregation.P95,
                Unit.MICROSECONDS,
                30_000L),
        BINDER_CALLBACK_REGISTRATION(
                "binder.callback_registration.latency_us",
                Category.BINDER,
                Aggregation.P95,
                Unit.MICROSECONDS,
                50_000L),
        PLAN_COMPILE(
                "plan.compile.latency_us",
                Category.PLAN,
                Aggregation.P95,
                Unit.MICROSECONDS,
                300_000L),
        EFFECT_DISPATCH_ADMISSION(
                "effect.dispatch_admission.latency_us",
                Category.EFFECT_DISPATCH,
                Aggregation.P95,
                Unit.MICROSECONDS,
                200_000L),
        DATABASE_COMBINED_SIZE(
                "database.combined_size.bytes",
                Category.DATABASE,
                Aggregation.MAX,
                Unit.BYTES,
                67_108_864L),
        RUNTIME_PSS(
                "runtime.pss.bytes",
                Category.MEMORY,
                Aggregation.MAX,
                Unit.BYTES,
                268_435_456L),
        RUNTIME_CPU(
                "runtime.cpu.single_core_permille",
                Category.CPU,
                Aggregation.P95,
                Unit.PERMILLE_SINGLE_CORE,
                300L),
        RUNTIME_COLD_START(
                "runtime.cold_start_to_binder_ready.latency_us",
                Category.STARTUP,
                Aggregation.P95,
                Unit.MICROSECONDS,
                3_000_000L);

        private final String id;
        private final Category category;
        private final Aggregation aggregation;
        private final Unit unit;
        private final long limit;

        MetricId(String id, Category category, Aggregation aggregation, Unit unit, long limit) {
            this.id = id;
            this.category = category;
            this.aggregation = aggregation;
            this.unit = unit;
            this.limit = limit;
        }

        public String getId() {
            return id;
        }

        public Category getCategory() {
            return category;
        }

        public Aggregation getAggregation() {
            return aggregation;
        }

        public Unit getUnit() {
            return unit;
        }

        public long getLimit() {
            return limit;
        }
    }

    public enum ResultCode {
        WITHIN_BUDGET,
        EXCEEDED,
        MISSING,
        UNIT_MISMATCH,
        INSUFFICIENT_SAMPLES
    }

    public enum ReportCode {
        PASSED,
        EXCEEDED,
        INCOMPLETE
    }

    public static final class Budget {
        private final MetricId metricId;

        private Budget(MetricId metricId) {
            this.metricId = metricId;
        }

        public MetricId getMetricId() {
            return metricId;
        }

        public Category getCategory() {
            return metricId.getCategory();
        }

        public Aggregation getAggregation() {
            return metricId.getAggregation();
        }

        public Unit getUnit() {
            return metricId.getUnit();
        }

        public long getLimit() {
            return metricId.getLimit();
        }
    }

    public static final class Measurement {
        private final MetricId metricId;
        private final Unit unit;
        private final long observedValue;
        private final int sampleCount;
        private final String evidenceDigest;

        public Measurement(
                MetricId metricId,
                Unit unit,
                long observedValue,
                int sampleCount,
                String evidenceDigest) {
            this.metricId = Objects.requireNonNull(metricId, "metricId");
            this.unit = Objects.requireNonNull(unit, "unit");
            if (observedValue < 0) {
                throw violation("observedValue must not be negative");
            }
            if (sampleCount < 1 || sampleCount > 1_000_000) {
                throw violation("sampleCount is out of range");
            }
            this.observedValue = observedValue;
            this.sampleCount = sampleCount;
            this.evidenceDigest = requireDigest(evidenceDigest, "evidenceDigest");
        }

        public MetricId getMetricId() {
            return metricId;
        }

        public Unit getUnit() {
            return unit;
        }

        public long getObservedValue() {
            return observedValue;
        }

        public int getSampleCount() {
            return sampleCount;
        }

        public String getEvidenceDigest() {
            return evidenceDigest;
        }
    }

    public static final class ReportContext {
        private final EvidenceMode evidenceMode;
        private final String releaseTag;
        private final String sourceCommit;
        private final String deviceAlias;
        private final String evidenceDigest;
        private final boolean targetOwnerApproved;
        private final String contextDigest;

        public ReportContext(
                EvidenceMode evidenceMode,
                String releaseTag,
                String sourceCommit,
                String deviceAlias,
                String evidenceDigest,
                boolean targetOwnerApproved) {
            this.evidenceMode = Objects.requireNonNull(evidenceMode, "evidenceMode");
            this.releaseTag = require(RELEASE_TAG, releaseTag, "releaseTag");
            this.sourceCommit = require(SOURCE_COMMIT, sourceCommit, "sourceCommit");
            this.deviceAlias = require(DEVICE_ALIAS, deviceAlias, "deviceAlias");
            this.evidenceDigest = requireDigest(evidenceDigest, "evidenceDigest");
            if (targetOwnerApproved && evidenceMode != EvidenceMode.TARGET_ANDROID13) {
                throw violation("owner approval is only valid for TARGET_ANDROID13 evidence");
            }
            this.targetOwnerApproved = targetOwnerApproved;
            this.contextDigest = sha256(
                    SCHEMA_VERSION
                            + "|" + PROFILE_ID
                            + "|" + evidenceMode.name()
                            + "|" + releaseTag
                            + "|" + sourceCommit
                            + "|" + deviceAlias
                            + "|" + evidenceDigest
                            + "|" + targetOwnerApproved);
        }

        public EvidenceMode getEvidenceMode() {
            return evidenceMode;
        }

        public String getContextDigest() {
            return contextDigest;
        }

        public boolean isTargetOwnerApproved() {
            return targetOwnerApproved;
        }

        private int minimumSamples() {
            return evidenceMode == EvidenceMode.CONTRACT_TEST ? 1 : TARGET_MINIMUM_SAMPLES;
        }
    }

    public static final class MetricResult {
        private final Budget budget;
        private final ResultCode code;
        private final long observedValue;
        private final int sampleCount;
        private final String evidenceDigest;

        private MetricResult(
                Budget budget,
                ResultCode code,
                long observedValue,
                int sampleCount,
                String evidenceDigest) {
            this.budget = budget;
            this.code = code;
            this.observedValue = observedValue;
            this.sampleCount = sampleCount;
            this.evidenceDigest = evidenceDigest;
        }

        public Budget getBudget() {
            return budget;
        }

        public ResultCode getCode() {
            return code;
        }

        public long getObservedValue() {
            return observedValue;
        }

        public int getSampleCount() {
            return sampleCount;
        }

        private String canonicalForm() {
            return budget.getMetricId().getId()
                    + "|" + code.name()
                    + "|" + observedValue
                    + "|" + sampleCount
                    + "|" + evidenceDigest;
        }
    }

    public static final class Report {
        private final ReportContext context;
        private final ReportCode code;
        private final List<MetricResult> results;
        private final int passCount;
        private final int exceededCount;
        private final int incompleteCount;
        private final boolean targetEvidenceStructurallyComplete;
        private final String reportDigest;

        private Report(ReportContext context, List<MetricResult> results) {
            this.context = context;
            this.results = Collections.unmodifiableList(new ArrayList<>(results));
            int passed = 0;
            int exceeded = 0;
            for (MetricResult result : results) {
                if (result.getCode() == ResultCode.WITHIN_BUDGET) {
                    passed++;
                } else if (result.getCode() == ResultCode.EXCEEDED) {
                    exceeded++;
                }
            }
            this.passCount = passed;
            this.exceededCount = exceeded;
            this.incompleteCount = results.size() - passed - exceeded;
            if (exceeded > 0) {
                this.code = ReportCode.EXCEEDED;
            } else if (passed == METRIC_COUNT) {
                this.code = ReportCode.PASSED;
            } else {
                this.code = ReportCode.INCOMPLETE;
            }
            this.targetEvidenceStructurallyComplete = code == ReportCode.PASSED
                    && context.getEvidenceMode() == EvidenceMode.TARGET_ANDROID13
                    && context.isTargetOwnerApproved();
            StringBuilder canonical = new StringBuilder(context.getContextDigest())
                    .append('|').append(code.name());
            for (MetricResult result : results) {
                canonical.append('|').append(result.canonicalForm());
            }
            this.reportDigest = sha256(canonical.toString());
        }

        public ReportCode getCode() {
            return code;
        }

        public List<MetricResult> getResults() {
            return results;
        }

        public int getPassCount() {
            return passCount;
        }

        public int getExceededCount() {
            return exceededCount;
        }

        public int getIncompleteCount() {
            return incompleteCount;
        }

        public boolean isTargetEvidenceStructurallyComplete() {
            return targetEvidenceStructurallyComplete;
        }

        public String getReportDigest() {
            return reportDigest;
        }

        public boolean isRuntimeWired() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }

        public boolean isProductionReady() {
            return false;
        }

        public boolean isTargetHardwareQualified() {
            return false;
        }
    }

    public static List<Budget> catalog() {
        return CATALOG;
    }

    public static String catalogDigest() {
        return CATALOG_DIGEST;
    }

    public static Report evaluate(ReportContext context, List<Measurement> measurements) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(measurements, "measurements");
        if (measurements.size() > METRIC_COUNT) {
            throw violation("measurement count exceeds fixed catalog");
        }
        Map<MetricId, Measurement> indexed = new EnumMap<>(MetricId.class);
        for (Measurement measurement : measurements) {
            Objects.requireNonNull(measurement, "measurement");
            if (indexed.put(measurement.getMetricId(), measurement) != null) {
                throw violation("duplicate metric measurement");
            }
        }

        List<MetricResult> results = new ArrayList<>(METRIC_COUNT);
        for (Budget budget : CATALOG) {
            Measurement measurement = indexed.get(budget.getMetricId());
            if (measurement == null) {
                results.add(new MetricResult(budget, ResultCode.MISSING, 0, 0, "0".repeat(64)));
                continue;
            }
            ResultCode code;
            if (measurement.getUnit() != budget.getUnit()) {
                code = ResultCode.UNIT_MISMATCH;
            } else if (measurement.getSampleCount() < context.minimumSamples()) {
                code = ResultCode.INSUFFICIENT_SAMPLES;
            } else if (measurement.getObservedValue() > budget.getLimit()) {
                code = ResultCode.EXCEEDED;
            } else {
                code = ResultCode.WITHIN_BUDGET;
            }
            results.add(new MetricResult(
                    budget,
                    code,
                    measurement.getObservedValue(),
                    measurement.getSampleCount(),
                    measurement.getEvidenceDigest()));
        }
        return new Report(context, results);
    }

    private static List<Budget> buildCatalog() {
        List<Budget> budgets = new ArrayList<>();
        Set<Category> categories = new HashSet<>();
        for (MetricId metricId : MetricId.values()) {
            budgets.add(new Budget(metricId));
            categories.add(metricId.getCategory());
        }
        if (budgets.size() != METRIC_COUNT || categories.size() != METRIC_CATEGORY_COUNT) {
            throw new ExceptionInInitializerError("performance budget catalog shape changed");
        }
        return Collections.unmodifiableList(budgets);
    }

    private static String canonicalCatalog() {
        StringBuilder canonical = new StringBuilder()
                .append(SCHEMA_VERSION).append('|').append(PROFILE_ID);
        for (Budget budget : CATALOG) {
            MetricId metric = budget.getMetricId();
            canonical.append('|').append(metric.getId())
                    .append('|').append(metric.getCategory().name())
                    .append('|').append(metric.getAggregation().name())
                    .append('|').append(metric.getUnit().name())
                    .append('|').append(metric.getLimit());
        }
        return canonical.toString();
    }

    private static String require(Pattern pattern, String value, String name) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw violation(name + " is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String name) {
        return require(DIGEST, value, name);
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException(message);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                encoded.append(String.format("%02x", item & 0xff));
            }
            return encoded.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
