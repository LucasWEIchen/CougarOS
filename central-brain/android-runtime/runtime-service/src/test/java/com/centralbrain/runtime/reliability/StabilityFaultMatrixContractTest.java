package com.centralbrain.runtime.reliability;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class StabilityFaultMatrixContractTest {
    private static final String SOURCE_COMMIT = "a".repeat(40);
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    @Test
    public void fixedMatrixContainsThreeWorkloadsSixFaultsAndEighteenCases() {
        assertEquals(3, StabilityFaultMatrixContract.WORKLOAD_COUNT);
        assertEquals(6, StabilityFaultMatrixContract.FAULT_COUNT);
        assertEquals(18, StabilityFaultMatrixContract.MATRIX_CASE_COUNT);
        assertEquals(18, StabilityFaultMatrixContract.matrix().size());
        assertTrue(StabilityFaultMatrixContract.matrixDigest().matches("[0-9a-f]{64}"));
    }

    @Test
    public void matrixIsExactWorkloadFaultCrossProduct() {
        for (StabilityFaultMatrixContract.Workload workload
                : StabilityFaultMatrixContract.Workload.values()) {
            int count = 0;
            for (StabilityFaultMatrixContract.MatrixCase matrixCase
                    : StabilityFaultMatrixContract.matrix()) {
                if (matrixCase.getWorkload() == workload) {
                    count++;
                }
            }
            assertEquals(6, count);
        }
    }

    @Test
    public void completeContractReportPassesWithoutTargetQualification() {
        StabilityFaultMatrixContract.Report report = StabilityFaultMatrixContract.evaluate(
                context(StabilityFaultMatrixContract.EvidenceMode.CONTRACT_TEST, 0L, false),
                completeObservations(1));

        assertEquals(StabilityFaultMatrixContract.ReportCode.PASSED, report.getCode());
        assertEquals(18, report.getPassCount());
        assertEquals(0, report.getFailedCount());
        assertEquals(0, report.getIncompleteCount());
        assertTrue(report.isDurationComplete());
        assertFalse(report.isTargetEvidenceStructurallyComplete());
        assertFalse(report.isTargetHardwareQualified());
        assertFalse(report.isFaultInjectionRuntimeWired());
        assertFalse(report.isHardwareAccessed());
    }

    @Test
    public void duplicateAndMissingCasesFailClosed() {
        List<StabilityFaultMatrixContract.Observation> duplicate = completeObservations(1);
        duplicate.set(duplicate.size() - 1, duplicate.get(0));
        try {
            StabilityFaultMatrixContract.evaluate(
                    context(StabilityFaultMatrixContract.EvidenceMode.CONTRACT_TEST, 0L, false),
                    duplicate);
            fail("duplicate matrix observation must fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("duplicate"));
        }

        List<StabilityFaultMatrixContract.Observation> missing = completeObservations(1);
        missing.remove(missing.size() - 1);
        StabilityFaultMatrixContract.Report report = StabilityFaultMatrixContract.evaluate(
                context(StabilityFaultMatrixContract.EvidenceMode.CONTRACT_TEST, 0L, false),
                missing);
        assertEquals(StabilityFaultMatrixContract.ReportCode.INCOMPLETE, report.getCode());
        assertEquals(1, report.getIncompleteCount());
    }

    @Test
    public void applicationIterationAndDurationRequirementsFailClosed() {
        StabilityFaultMatrixContract.Report insufficientIterations =
                StabilityFaultMatrixContract.evaluate(
                        context(
                                StabilityFaultMatrixContract.EvidenceMode.ANDROID_APPLICATION,
                                60_000L,
                                false),
                        completeObservations(1));
        StabilityFaultMatrixContract.Report insufficientDuration =
                StabilityFaultMatrixContract.evaluate(
                        context(
                                StabilityFaultMatrixContract.EvidenceMode.ANDROID_APPLICATION,
                                59_999L,
                                false),
                        completeObservations(30));

        assertEquals(
                StabilityFaultMatrixContract.ReportCode.INCOMPLETE,
                insufficientIterations.getCode());
        assertEquals(18, insufficientIterations.getIncompleteCount());
        assertEquals(
                StabilityFaultMatrixContract.ReportCode.INCOMPLETE,
                insufficientDuration.getCode());
        assertFalse(insufficientDuration.isDurationComplete());
    }

    @Test
    public void crashAnrAndInvariantViolationFailReport() {
        assertFailureCode(
                1, 0, 0, StabilityFaultMatrixContract.CaseResultCode.UNEXPECTED_CRASH);
        assertFailureCode(
                0, 1, 0, StabilityFaultMatrixContract.CaseResultCode.ANR_DETECTED);
        assertFailureCode(
                0, 0, 1, StabilityFaultMatrixContract.CaseResultCode.INVARIANT_VIOLATION);
    }

    @Test
    public void outcomeMismatchAndRecoveryTimeoutFailReport() {
        List<StabilityFaultMatrixContract.Observation> observations = completeObservations(1);
        StabilityFaultMatrixContract.MatrixCase adapterDeath =
                StabilityFaultMatrixContract.matrix().get(1);
        observations.set(
                1,
                observation(
                        adapterDeath,
                        StabilityFaultMatrixContract.ExpectedOutcome.COMPLETED,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0L,
                        DIGEST_B));
        StabilityFaultMatrixContract.Report mismatch = StabilityFaultMatrixContract.evaluate(
                context(StabilityFaultMatrixContract.EvidenceMode.CONTRACT_TEST, 0L, false),
                observations);
        assertEquals(StabilityFaultMatrixContract.ReportCode.FAILED, mismatch.getCode());
        assertEquals(
                StabilityFaultMatrixContract.CaseResultCode.OUTCOME_MISMATCH,
                mismatch.getResults().get(1).getCode());

        observations.set(
                1,
                observation(
                        adapterDeath,
                        adapterDeath.getExpectedOutcome(),
                        1,
                        1,
                        0,
                        0,
                        0,
                        adapterDeath.getRecoveryBudgetMillis() + 1,
                        DIGEST_B));
        StabilityFaultMatrixContract.Report timeout = StabilityFaultMatrixContract.evaluate(
                context(StabilityFaultMatrixContract.EvidenceMode.CONTRACT_TEST, 0L, false),
                observations);
        assertEquals(
                StabilityFaultMatrixContract.CaseResultCode.RECOVERY_TIMEOUT,
                timeout.getResults().get(1).getCode());
    }

    @Test
    public void targetNeedsSeventyTwoHoursSamplesAndOwnerButNeverAutoQualifies() {
        StabilityFaultMatrixContract.Report shortRun = StabilityFaultMatrixContract.evaluate(
                context(
                        StabilityFaultMatrixContract.EvidenceMode.TARGET_ANDROID13_72H,
                        StabilityFaultMatrixContract.TARGET_DURATION_MILLIS - 1,
                        true),
                completeObservations(30));
        StabilityFaultMatrixContract.Report notApproved = StabilityFaultMatrixContract.evaluate(
                context(
                        StabilityFaultMatrixContract.EvidenceMode.TARGET_ANDROID13_72H,
                        StabilityFaultMatrixContract.TARGET_DURATION_MILLIS,
                        false),
                completeObservations(30));
        StabilityFaultMatrixContract.Report approved = StabilityFaultMatrixContract.evaluate(
                context(
                        StabilityFaultMatrixContract.EvidenceMode.TARGET_ANDROID13_72H,
                        StabilityFaultMatrixContract.TARGET_DURATION_MILLIS,
                        true),
                completeObservations(30));

        assertEquals(StabilityFaultMatrixContract.ReportCode.INCOMPLETE, shortRun.getCode());
        assertEquals(StabilityFaultMatrixContract.ReportCode.PASSED, notApproved.getCode());
        assertFalse(notApproved.isTargetEvidenceStructurallyComplete());
        assertTrue(approved.isTargetEvidenceStructurallyComplete());
        assertFalse(approved.isTargetHardwareQualified());
        assertFalse(approved.isProductionReady());
    }

    @Test
    public void reportDigestIsIndependentOfObservationInputOrder() {
        List<StabilityFaultMatrixContract.Observation> forward = completeObservations(1);
        List<StabilityFaultMatrixContract.Observation> reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);

        assertEquals(
                StabilityFaultMatrixContract.evaluate(
                        context(
                                StabilityFaultMatrixContract.EvidenceMode.CONTRACT_TEST,
                                0L,
                                false),
                        forward).getReportDigest(),
                StabilityFaultMatrixContract.evaluate(
                        context(
                                StabilityFaultMatrixContract.EvidenceMode.CONTRACT_TEST,
                                0L,
                                false),
                        reverse).getReportDigest());
    }

    @Test
    public void reportDigestBindsAllObservationCounters() {
        List<StabilityFaultMatrixContract.Observation> oneCrash = completeObservations(1);
        List<StabilityFaultMatrixContract.Observation> twoCrashes = completeObservations(1);
        StabilityFaultMatrixContract.MatrixCase first =
                StabilityFaultMatrixContract.matrix().get(0);
        oneCrash.set(0, observation(
                first, first.getExpectedOutcome(), 1, 1, 1, 0, 0, 0L, DIGEST_A));
        twoCrashes.set(0, observation(
                first, first.getExpectedOutcome(), 1, 1, 2, 0, 0, 0L, DIGEST_A));

        assertNotEquals(
                StabilityFaultMatrixContract.evaluate(
                        context(
                                StabilityFaultMatrixContract.EvidenceMode.CONTRACT_TEST,
                                0L,
                                false),
                        oneCrash).getReportDigest(),
                StabilityFaultMatrixContract.evaluate(
                        context(
                                StabilityFaultMatrixContract.EvidenceMode.CONTRACT_TEST,
                                0L,
                                false),
                        twoCrashes).getReportDigest());
    }

    private static void assertFailureCode(
            int crashCount,
            int anrCount,
            int invariantCount,
            StabilityFaultMatrixContract.CaseResultCode expectedCode) {
        List<StabilityFaultMatrixContract.Observation> observations = completeObservations(1);
        StabilityFaultMatrixContract.MatrixCase first =
                StabilityFaultMatrixContract.matrix().get(0);
        observations.set(
                0,
                observation(
                        first,
                        first.getExpectedOutcome(),
                        1,
                        1,
                        crashCount,
                        anrCount,
                        invariantCount,
                        0L,
                        DIGEST_B));
        StabilityFaultMatrixContract.Report report = StabilityFaultMatrixContract.evaluate(
                context(StabilityFaultMatrixContract.EvidenceMode.CONTRACT_TEST, 0L, false),
                observations);

        assertEquals(StabilityFaultMatrixContract.ReportCode.FAILED, report.getCode());
        assertEquals(expectedCode, report.getResults().get(0).getCode());
        assertNotEquals(completeReport().getReportDigest(), report.getReportDigest());
    }

    private static StabilityFaultMatrixContract.Report completeReport() {
        return StabilityFaultMatrixContract.evaluate(
                context(StabilityFaultMatrixContract.EvidenceMode.CONTRACT_TEST, 0L, false),
                completeObservations(1));
    }

    private static StabilityFaultMatrixContract.RunContext context(
            StabilityFaultMatrixContract.EvidenceMode mode,
            long durationMillis,
            boolean ownerApproved) {
        return new StabilityFaultMatrixContract.RunContext(
                mode,
                "p9-w02-contract",
                SOURCE_COMMIT,
                "cockpit-a13-contract",
                DIGEST_A,
                durationMillis,
                ownerApproved);
    }

    private static List<StabilityFaultMatrixContract.Observation> completeObservations(
            int iterations) {
        List<StabilityFaultMatrixContract.Observation> observations = new ArrayList<>();
        for (StabilityFaultMatrixContract.MatrixCase matrixCase
                : StabilityFaultMatrixContract.matrix()) {
            observations.add(observation(
                    matrixCase,
                    matrixCase.getExpectedOutcome(),
                    iterations,
                    iterations,
                    0,
                    0,
                    0,
                    matrixCase.getRecoveryBudgetMillis(),
                    DIGEST_A));
        }
        return observations;
    }

    private static StabilityFaultMatrixContract.Observation observation(
            StabilityFaultMatrixContract.MatrixCase matrixCase,
            StabilityFaultMatrixContract.ExpectedOutcome outcome,
            int attempted,
            int completed,
            int crashCount,
            int anrCount,
            int invariantCount,
            long maxRecoveryMillis,
            String digest) {
        return new StabilityFaultMatrixContract.Observation(
                matrixCase.getWorkload(),
                matrixCase.getFault(),
                outcome,
                attempted,
                completed,
                crashCount,
                anrCount,
                invariantCount,
                maxRecoveryMillis,
                digest);
    }
}
