package com.centralbrain.runtime.reliability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fixed P9-W02 stability/fault matrix and strict summary validation.
 * It consumes caller-owned observations and never injects faults or reads platform state.
 * Req IDs: S2-REL-001, S2-OBS-001, XSC-001/004/005/006, KH-003/006, DEL-001/004/005.
 */
public final class StabilityFaultMatrixContract {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE_ID = "android13-stability-v1";
    public static final int WORKLOAD_COUNT = 3;
    public static final int FAULT_COUNT = 6;
    public static final int MATRIX_CASE_COUNT = WORKLOAD_COUNT * FAULT_COUNT;
    public static final int APPLICATION_MINIMUM_ITERATIONS = 30;
    public static final long APPLICATION_MINIMUM_DURATION_MILLIS = 60_000L;
    public static final long TARGET_DURATION_MILLIS = 259_200_000L;

    private static final Pattern RELEASE_TAG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern SOURCE_COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern DEVICE_ALIAS = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final List<MatrixCase> MATRIX = buildMatrix();
    private static final String MATRIX_DIGEST = sha256(canonicalMatrix());

    private StabilityFaultMatrixContract() {
    }

    public enum Workload {
        COLD_SCENARIO_LOOP("scene.comfort.cold.v1"),
        FATIGUE_SCENARIO_LOOP("scene.fatigue.assist.v1"),
        REST_SCENARIO_LOOP("scene.rest.nap.v1");

        private final String id;

        Workload(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public enum ExpectedOutcome {
        COMPLETED,
        RECOVERED,
        FAIL_CLOSED,
        DEGRADED
    }

    public enum Fault {
        BASELINE("none", ExpectedOutcome.COMPLETED, 0L),
        ADAPTER_DEATH("adapter_death", ExpectedOutcome.RECOVERED, 5_000L),
        RUNTIME_RESTART("runtime_restart", ExpectedOutcome.RECOVERED, 30_000L),
        STORAGE_PRESSURE("storage_pressure", ExpectedOutcome.FAIL_CLOSED, 5_000L),
        CALLBACK_CHURN("callback_churn", ExpectedOutcome.RECOVERED, 5_000L),
        NETWORK_LOSS("network_loss", ExpectedOutcome.DEGRADED, 5_000L);

        private final String id;
        private final ExpectedOutcome expectedOutcome;
        private final long recoveryBudgetMillis;

        Fault(String id, ExpectedOutcome expectedOutcome, long recoveryBudgetMillis) {
            this.id = id;
            this.expectedOutcome = expectedOutcome;
            this.recoveryBudgetMillis = recoveryBudgetMillis;
        }

        public String getId() {
            return id;
        }

        public ExpectedOutcome getExpectedOutcome() {
            return expectedOutcome;
        }

        public long getRecoveryBudgetMillis() {
            return recoveryBudgetMillis;
        }
    }

    public enum EvidenceMode {
        CONTRACT_TEST,
        ANDROID_APPLICATION,
        TARGET_ANDROID13_72H
    }

    public enum CaseResultCode {
        WITHIN_CONTRACT,
        MISSING,
        INSUFFICIENT_ITERATIONS,
        UNEXPECTED_CRASH,
        ANR_DETECTED,
        INVARIANT_VIOLATION,
        ITERATION_INCOMPLETE,
        OUTCOME_MISMATCH,
        RECOVERY_TIMEOUT
    }

    public enum ReportCode {
        PASSED,
        FAILED,
        INCOMPLETE
    }

    public static final class MatrixCase {
        private final Workload workload;
        private final Fault fault;
        private final String caseId;

        private MatrixCase(Workload workload, Fault fault) {
            this.workload = workload;
            this.fault = fault;
            this.caseId = workload.getId() + "::" + fault.getId();
        }

        public Workload getWorkload() {
            return workload;
        }

        public Fault getFault() {
            return fault;
        }

        public String getCaseId() {
            return caseId;
        }

        public ExpectedOutcome getExpectedOutcome() {
            return fault.getExpectedOutcome();
        }

        public long getRecoveryBudgetMillis() {
            return fault.getRecoveryBudgetMillis();
        }

        private String canonicalForm() {
            return caseId
                    + "|" + getExpectedOutcome().name()
                    + "|" + getRecoveryBudgetMillis();
        }
    }

    public static final class Observation {
        private final Workload workload;
        private final Fault fault;
        private final ExpectedOutcome observedOutcome;
        private final int attemptedIterations;
        private final int completedIterations;
        private final int unexpectedCrashCount;
        private final int anrCount;
        private final int invariantViolationCount;
        private final long maxRecoveryMillis;
        private final String evidenceDigest;

        public Observation(
                Workload workload,
                Fault fault,
                ExpectedOutcome observedOutcome,
                int attemptedIterations,
                int completedIterations,
                int unexpectedCrashCount,
                int anrCount,
                int invariantViolationCount,
                long maxRecoveryMillis,
                String evidenceDigest) {
            this.workload = Objects.requireNonNull(workload, "workload");
            this.fault = Objects.requireNonNull(fault, "fault");
            this.observedOutcome = Objects.requireNonNull(observedOutcome, "observedOutcome");
            if (attemptedIterations < 1 || attemptedIterations > 1_000_000) {
                throw violation("attemptedIterations is out of range");
            }
            if (completedIterations < 0 || completedIterations > attemptedIterations) {
                throw violation("completedIterations is out of range");
            }
            this.attemptedIterations = attemptedIterations;
            this.completedIterations = completedIterations;
            this.unexpectedCrashCount = requireCount(unexpectedCrashCount, "unexpectedCrashCount");
            this.anrCount = requireCount(anrCount, "anrCount");
            this.invariantViolationCount = requireCount(
                    invariantViolationCount, "invariantViolationCount");
            if (maxRecoveryMillis < 0 || maxRecoveryMillis > TARGET_DURATION_MILLIS) {
                throw violation("maxRecoveryMillis is out of range");
            }
            this.maxRecoveryMillis = maxRecoveryMillis;
            this.evidenceDigest = requireDigest(evidenceDigest, "evidenceDigest");
        }

        public String getCaseId() {
            return workload.getId() + "::" + fault.getId();
        }

        public Workload getWorkload() {
            return workload;
        }

        public Fault getFault() {
            return fault;
        }

        public ExpectedOutcome getObservedOutcome() {
            return observedOutcome;
        }

        public int getAttemptedIterations() {
            return attemptedIterations;
        }

        public int getCompletedIterations() {
            return completedIterations;
        }

        public int getUnexpectedCrashCount() {
            return unexpectedCrashCount;
        }

        public int getAnrCount() {
            return anrCount;
        }

        public int getInvariantViolationCount() {
            return invariantViolationCount;
        }

        public long getMaxRecoveryMillis() {
            return maxRecoveryMillis;
        }

        public String getEvidenceDigest() {
            return evidenceDigest;
        }
    }

    public static final class RunContext {
        private final EvidenceMode evidenceMode;
        private final long observedDurationMillis;
        private final boolean targetOwnerApproved;
        private final String contextDigest;

        public RunContext(
                EvidenceMode evidenceMode,
                String releaseTag,
                String sourceCommit,
                String deviceAlias,
                String evidenceDigest,
                long observedDurationMillis,
                boolean targetOwnerApproved) {
            this.evidenceMode = Objects.requireNonNull(evidenceMode, "evidenceMode");
            require(RELEASE_TAG, releaseTag, "releaseTag");
            require(SOURCE_COMMIT, sourceCommit, "sourceCommit");
            require(DEVICE_ALIAS, deviceAlias, "deviceAlias");
            requireDigest(evidenceDigest, "evidenceDigest");
            if (observedDurationMillis < 0 || observedDurationMillis > 31L * 24 * 60 * 60 * 1_000) {
                throw violation("observedDurationMillis is out of range");
            }
            if (targetOwnerApproved && evidenceMode != EvidenceMode.TARGET_ANDROID13_72H) {
                throw violation("owner approval is only valid for target 72h evidence");
            }
            this.observedDurationMillis = observedDurationMillis;
            this.targetOwnerApproved = targetOwnerApproved;
            this.contextDigest = sha256(
                    SCHEMA_VERSION
                            + "|" + PROFILE_ID
                            + "|" + evidenceMode.name()
                            + "|" + releaseTag
                            + "|" + sourceCommit
                            + "|" + deviceAlias
                            + "|" + evidenceDigest
                            + "|" + observedDurationMillis
                            + "|" + targetOwnerApproved);
        }

        public EvidenceMode getEvidenceMode() {
            return evidenceMode;
        }

        public long getObservedDurationMillis() {
            return observedDurationMillis;
        }

        public boolean isTargetOwnerApproved() {
            return targetOwnerApproved;
        }

        public String getContextDigest() {
            return contextDigest;
        }

        private int minimumIterations() {
            return evidenceMode == EvidenceMode.CONTRACT_TEST
                    ? 1
                    : APPLICATION_MINIMUM_ITERATIONS;
        }

        private long minimumDurationMillis() {
            if (evidenceMode == EvidenceMode.CONTRACT_TEST) {
                return 0L;
            }
            if (evidenceMode == EvidenceMode.ANDROID_APPLICATION) {
                return APPLICATION_MINIMUM_DURATION_MILLIS;
            }
            return TARGET_DURATION_MILLIS;
        }
    }

    public static final class CaseResult {
        private final MatrixCase matrixCase;
        private final CaseResultCode code;
        private final int attemptedIterations;
        private final String observationDigest;

        private CaseResult(
                MatrixCase matrixCase,
                CaseResultCode code,
                int attemptedIterations,
                String observationDigest) {
            this.matrixCase = matrixCase;
            this.code = code;
            this.attemptedIterations = attemptedIterations;
            this.observationDigest = observationDigest;
        }

        public MatrixCase getMatrixCase() {
            return matrixCase;
        }

        public CaseResultCode getCode() {
            return code;
        }

        private String canonicalForm() {
            return matrixCase.getCaseId()
                    + "|" + code.name()
                    + "|" + attemptedIterations
                    + "|" + observationDigest;
        }
    }

    public static final class Report {
        private final ReportCode code;
        private final List<CaseResult> results;
        private final int passCount;
        private final int failedCount;
        private final int incompleteCount;
        private final boolean durationComplete;
        private final boolean targetEvidenceStructurallyComplete;
        private final String reportDigest;

        private Report(RunContext context, List<CaseResult> results) {
            this.results = Collections.unmodifiableList(new ArrayList<>(results));
            int passed = 0;
            int failed = 0;
            for (CaseResult result : results) {
                if (result.getCode() == CaseResultCode.WITHIN_CONTRACT) {
                    passed++;
                } else if (result.getCode() != CaseResultCode.MISSING
                        && result.getCode() != CaseResultCode.INSUFFICIENT_ITERATIONS) {
                    failed++;
                }
            }
            this.passCount = passed;
            this.failedCount = failed;
            this.incompleteCount = results.size() - passed - failed;
            this.durationComplete = context.getObservedDurationMillis()
                    >= context.minimumDurationMillis();
            if (failed > 0) {
                this.code = ReportCode.FAILED;
            } else if (passed == MATRIX_CASE_COUNT && durationComplete) {
                this.code = ReportCode.PASSED;
            } else {
                this.code = ReportCode.INCOMPLETE;
            }
            this.targetEvidenceStructurallyComplete = code == ReportCode.PASSED
                    && context.getEvidenceMode() == EvidenceMode.TARGET_ANDROID13_72H
                    && context.isTargetOwnerApproved();
            StringBuilder canonical = new StringBuilder(context.getContextDigest())
                    .append('|').append(code.name())
                    .append('|').append(durationComplete);
            for (CaseResult result : results) {
                canonical.append('|').append(result.canonicalForm());
            }
            this.reportDigest = sha256(canonical.toString());
        }

        public ReportCode getCode() {
            return code;
        }

        public List<CaseResult> getResults() {
            return results;
        }

        public int getPassCount() {
            return passCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public int getIncompleteCount() {
            return incompleteCount;
        }

        public boolean isDurationComplete() {
            return durationComplete;
        }

        public boolean isTargetEvidenceStructurallyComplete() {
            return targetEvidenceStructurallyComplete;
        }

        public String getReportDigest() {
            return reportDigest;
        }

        public boolean isFaultInjectionRuntimeWired() {
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

    public static List<MatrixCase> matrix() {
        return MATRIX;
    }

    public static String matrixDigest() {
        return MATRIX_DIGEST;
    }

    public static Report evaluate(RunContext context, List<Observation> observations) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(observations, "observations");
        if (observations.size() > MATRIX_CASE_COUNT) {
            throw violation("observation count exceeds fixed matrix");
        }
        Map<String, Observation> indexed = new HashMap<>();
        for (Observation observation : observations) {
            Objects.requireNonNull(observation, "observation");
            if (indexed.put(observation.getCaseId(), observation) != null) {
                throw violation("duplicate matrix observation");
            }
        }

        List<CaseResult> results = new ArrayList<>(MATRIX_CASE_COUNT);
        for (MatrixCase matrixCase : MATRIX) {
            Observation observation = indexed.get(matrixCase.getCaseId());
            if (observation == null) {
                results.add(new CaseResult(
                        matrixCase, CaseResultCode.MISSING, 0, "0".repeat(64)));
                continue;
            }
            CaseResultCode code;
            if (observation.getAttemptedIterations() < context.minimumIterations()) {
                code = CaseResultCode.INSUFFICIENT_ITERATIONS;
            } else if (observation.getUnexpectedCrashCount() > 0) {
                code = CaseResultCode.UNEXPECTED_CRASH;
            } else if (observation.getAnrCount() > 0) {
                code = CaseResultCode.ANR_DETECTED;
            } else if (observation.getInvariantViolationCount() > 0) {
                code = CaseResultCode.INVARIANT_VIOLATION;
            } else if (observation.getCompletedIterations()
                    != observation.getAttemptedIterations()) {
                code = CaseResultCode.ITERATION_INCOMPLETE;
            } else if (observation.getObservedOutcome() != matrixCase.getExpectedOutcome()) {
                code = CaseResultCode.OUTCOME_MISMATCH;
            } else if (observation.getMaxRecoveryMillis()
                    > matrixCase.getRecoveryBudgetMillis()) {
                code = CaseResultCode.RECOVERY_TIMEOUT;
            } else {
                code = CaseResultCode.WITHIN_CONTRACT;
            }
            results.add(new CaseResult(
                    matrixCase,
                    code,
                    observation.getAttemptedIterations(),
                    observationDigest(observation)));
        }
        return new Report(context, results);
    }

    private static List<MatrixCase> buildMatrix() {
        List<MatrixCase> matrix = new ArrayList<>(MATRIX_CASE_COUNT);
        Set<String> ids = new HashSet<>();
        for (Workload workload : Workload.values()) {
            for (Fault fault : Fault.values()) {
                MatrixCase matrixCase = new MatrixCase(workload, fault);
                if (!ids.add(matrixCase.getCaseId())) {
                    throw new IllegalStateException("duplicate built-in matrix case");
                }
                matrix.add(matrixCase);
            }
        }
        if (matrix.size() != MATRIX_CASE_COUNT) {
            throw new IllegalStateException("fixed matrix size changed");
        }
        return Collections.unmodifiableList(matrix);
    }

    private static String canonicalMatrix() {
        StringBuilder canonical = new StringBuilder(PROFILE_ID);
        for (MatrixCase matrixCase : MATRIX) {
            canonical.append('|').append(matrixCase.canonicalForm());
        }
        return canonical.toString();
    }

    private static String observationDigest(Observation observation) {
        return sha256(
                observation.getCaseId()
                        + "|" + observation.getObservedOutcome().name()
                        + "|" + observation.getAttemptedIterations()
                        + "|" + observation.getCompletedIterations()
                        + "|" + observation.getUnexpectedCrashCount()
                        + "|" + observation.getAnrCount()
                        + "|" + observation.getInvariantViolationCount()
                        + "|" + observation.getMaxRecoveryMillis()
                        + "|" + observation.getEvidenceDigest());
    }

    private static int requireCount(int count, String field) {
        if (count < 0 || count > 1_000_000) {
            throw violation(field + " is out of range");
        }
        return count;
    }

    private static String require(Pattern pattern, String value, String field) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw violation(field + " is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String field) {
        return require(DIGEST, value, field);
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException(message);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                hex.append(String.format("%02x", item & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
