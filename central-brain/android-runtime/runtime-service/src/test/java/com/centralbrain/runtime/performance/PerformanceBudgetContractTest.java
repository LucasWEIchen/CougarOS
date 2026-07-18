package com.centralbrain.runtime.performance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class PerformanceBudgetContractTest {
    private static final String SOURCE_COMMIT = "a".repeat(40);
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    @Test
    public void fixedCatalogContainsSevenCategoriesAndTenMetrics() {
        assertEquals(7, PerformanceBudgetContract.METRIC_CATEGORY_COUNT);
        assertEquals(10, PerformanceBudgetContract.METRIC_COUNT);
        assertEquals(10, PerformanceBudgetContract.catalog().size());
        assertEquals(
                10_000L,
                PerformanceBudgetContract.MetricId.BINDER_PROTOCOL.getLimit());
        assertEquals(
                3_000_000L,
                PerformanceBudgetContract.MetricId.RUNTIME_COLD_START.getLimit());
        assertTrue(PerformanceBudgetContract.catalogDigest().matches("[0-9a-f]{64}"));
    }

    @Test
    public void completeContractReportPassesWithoutTargetQualification() {
        PerformanceBudgetContract.Report report = PerformanceBudgetContract.evaluate(
                context(PerformanceBudgetContract.EvidenceMode.CONTRACT_TEST, false),
                completeMeasurements(1, 0));

        assertEquals(PerformanceBudgetContract.ReportCode.PASSED, report.getCode());
        assertEquals(10, report.getPassCount());
        assertEquals(0, report.getExceededCount());
        assertEquals(0, report.getIncompleteCount());
        assertFalse(report.isTargetEvidenceStructurallyComplete());
        assertFalse(report.isTargetHardwareQualified());
        assertFalse(report.isProductionReady());
        assertFalse(report.isHardwareAccessed());
    }

    @Test
    public void exactThresholdPassesAndOneUnitAboveFails() {
        List<PerformanceBudgetContract.Measurement> measurements = completeMeasurements(1, 0);
        PerformanceBudgetContract.Report atLimit = PerformanceBudgetContract.evaluate(
                context(PerformanceBudgetContract.EvidenceMode.CONTRACT_TEST, false),
                measurements);
        measurements.set(
                PerformanceBudgetContract.MetricId.PLAN_COMPILE.ordinal(),
                measurement(
                        PerformanceBudgetContract.MetricId.PLAN_COMPILE,
                        PerformanceBudgetContract.MetricId.PLAN_COMPILE.getLimit() + 1,
                        1,
                        DIGEST_B));
        PerformanceBudgetContract.Report exceeded = PerformanceBudgetContract.evaluate(
                context(PerformanceBudgetContract.EvidenceMode.CONTRACT_TEST, false),
                measurements);

        assertEquals(PerformanceBudgetContract.ReportCode.PASSED, atLimit.getCode());
        assertEquals(PerformanceBudgetContract.ReportCode.EXCEEDED, exceeded.getCode());
        assertEquals(1, exceeded.getExceededCount());
        assertNotEquals(atLimit.getReportDigest(), exceeded.getReportDigest());
    }

    @Test
    public void missingAndInsufficientSamplesFailClosed() {
        List<PerformanceBudgetContract.Measurement> missing = completeMeasurements(1, 0);
        missing.remove(missing.size() - 1);
        PerformanceBudgetContract.Report missingReport = PerformanceBudgetContract.evaluate(
                context(PerformanceBudgetContract.EvidenceMode.CONTRACT_TEST, false),
                missing);
        PerformanceBudgetContract.Report insufficient = PerformanceBudgetContract.evaluate(
                context(PerformanceBudgetContract.EvidenceMode.ANDROID_APPLICATION, false),
                completeMeasurements(1, 0));

        assertEquals(PerformanceBudgetContract.ReportCode.INCOMPLETE, missingReport.getCode());
        assertEquals(1, missingReport.getIncompleteCount());
        assertEquals(PerformanceBudgetContract.ReportCode.INCOMPLETE, insufficient.getCode());
        assertEquals(10, insufficient.getIncompleteCount());
    }

    @Test
    public void duplicateMetricIsRejected() {
        List<PerformanceBudgetContract.Measurement> measurements = completeMeasurements(1, 0);
        measurements.set(measurements.size() - 1, measurements.get(0));
        try {
            PerformanceBudgetContract.evaluate(
                    context(PerformanceBudgetContract.EvidenceMode.CONTRACT_TEST, false),
                    measurements);
            fail("duplicate metric must fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("duplicate"));
        }
    }

    @Test
    public void unitMismatchFailsClosed() {
        List<PerformanceBudgetContract.Measurement> measurements = completeMeasurements(1, 0);
        measurements.set(
                0,
                new PerformanceBudgetContract.Measurement(
                        PerformanceBudgetContract.MetricId.BINDER_PROTOCOL,
                        PerformanceBudgetContract.Unit.BYTES,
                        1,
                        1,
                        DIGEST_B));
        PerformanceBudgetContract.Report report = PerformanceBudgetContract.evaluate(
                context(PerformanceBudgetContract.EvidenceMode.CONTRACT_TEST, false),
                measurements);

        assertEquals(PerformanceBudgetContract.ReportCode.INCOMPLETE, report.getCode());
        assertEquals(
                PerformanceBudgetContract.ResultCode.UNIT_MISMATCH,
                report.getResults().get(0).getCode());
    }

    @Test
    public void reportDigestIsIndependentOfMeasurementInputOrder() {
        List<PerformanceBudgetContract.Measurement> forward = completeMeasurements(1, 0);
        List<PerformanceBudgetContract.Measurement> reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);

        assertEquals(
                PerformanceBudgetContract.evaluate(
                        context(PerformanceBudgetContract.EvidenceMode.CONTRACT_TEST, false),
                        forward).getReportDigest(),
                PerformanceBudgetContract.evaluate(
                        context(PerformanceBudgetContract.EvidenceMode.CONTRACT_TEST, false),
                        reverse).getReportDigest());
    }

    @Test
    public void targetEvidenceNeedsSamplesAndOwnerButNeverAutoQualifiesHardware() {
        PerformanceBudgetContract.Report notApproved = PerformanceBudgetContract.evaluate(
                context(PerformanceBudgetContract.EvidenceMode.TARGET_ANDROID13, false),
                completeMeasurements(30, 0));
        PerformanceBudgetContract.Report approved = PerformanceBudgetContract.evaluate(
                context(PerformanceBudgetContract.EvidenceMode.TARGET_ANDROID13, true),
                completeMeasurements(30, 0));

        assertEquals(PerformanceBudgetContract.ReportCode.PASSED, notApproved.getCode());
        assertFalse(notApproved.isTargetEvidenceStructurallyComplete());
        assertTrue(approved.isTargetEvidenceStructurallyComplete());
        assertFalse(approved.isTargetHardwareQualified());
        assertFalse(approved.isRuntimeWired());
        assertFalse(approved.isProductionReady());
    }

    private static PerformanceBudgetContract.ReportContext context(
            PerformanceBudgetContract.EvidenceMode mode,
            boolean ownerApproved) {
        return new PerformanceBudgetContract.ReportContext(
                mode,
                "p9-w01-contract",
                SOURCE_COMMIT,
                "cockpit-a13-contract",
                DIGEST_A,
                ownerApproved);
    }

    private static List<PerformanceBudgetContract.Measurement> completeMeasurements(
            int sampleCount,
            long limitAdjustment) {
        List<PerformanceBudgetContract.Measurement> measurements = new ArrayList<>();
        for (PerformanceBudgetContract.Budget budget : PerformanceBudgetContract.catalog()) {
            measurements.add(measurement(
                    budget.getMetricId(),
                    budget.getLimit() + limitAdjustment,
                    sampleCount,
                    DIGEST_A));
        }
        return measurements;
    }

    private static PerformanceBudgetContract.Measurement measurement(
            PerformanceBudgetContract.MetricId metricId,
            long observedValue,
            int sampleCount,
            String digest) {
        return new PerformanceBudgetContract.Measurement(
                metricId,
                metricId.getUnit(),
                observedValue,
                sampleCount,
                digest);
    }
}
