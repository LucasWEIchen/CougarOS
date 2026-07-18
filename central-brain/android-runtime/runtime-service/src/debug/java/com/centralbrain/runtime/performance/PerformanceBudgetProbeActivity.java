package com.centralbrain.runtime.performance;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public final class PerformanceBudgetProbeActivity extends Activity {
    private static final String TAG = "CbPerfBudgetProbe";
    private static final String SOURCE_COMMIT = "a".repeat(40);
    private static final String DIGEST = "b".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            List<PerformanceBudgetContract.Measurement> measurements = new ArrayList<>();
            for (PerformanceBudgetContract.Budget budget : PerformanceBudgetContract.catalog()) {
                measurements.add(new PerformanceBudgetContract.Measurement(
                        budget.getMetricId(),
                        budget.getUnit(),
                        budget.getLimit(),
                        1,
                        DIGEST));
            }
            PerformanceBudgetContract.Report report = PerformanceBudgetContract.evaluate(
                    new PerformanceBudgetContract.ReportContext(
                            PerformanceBudgetContract.EvidenceMode.CONTRACT_TEST,
                            "p9-w01-probe",
                            SOURCE_COMMIT,
                            "cockpit-a13-contract",
                            DIGEST,
                            false),
                    measurements);
            boolean catalogVerified = PerformanceBudgetContract.catalog().size() == 10
                    && PerformanceBudgetContract.METRIC_CATEGORY_COUNT == 7;
            boolean reportVerified = report.getCode()
                            == PerformanceBudgetContract.ReportCode.PASSED
                    && report.getPassCount() == 10
                    && report.getIncompleteCount() == 0;
            boolean boundaryVerified = !report.isTargetEvidenceStructurallyComplete()
                    && !report.isTargetHardwareQualified()
                    && !report.isRuntimeWired()
                    && !report.isHardwareAccessed()
                    && !report.isProductionReady();

            Log.i(TAG, "nonce=" + nonce
                    + " performance_budget_probe_complete=true"
                    + " performance_budget_contract_verified="
                    + (catalogVerified && reportVerified && boundaryVerified)
                    + " performance_budget_catalog_verified=" + catalogVerified
                    + " performance_budget_report_validation_verified=" + reportVerified
                    + " performance_budget_boundary_verified=" + boundaryVerified
                    + " performance_budget_category_count=7"
                    + " performance_budget_metric_count=10"
                    + " performance_budget_target_measurement_complete=false"
                    + " performance_budget_runtime_wired=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " performance_budget_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " performance_budget_target_measurement_complete=false"
                    + " performance_budget_runtime_wired=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false", exception);
        }
    }
}
