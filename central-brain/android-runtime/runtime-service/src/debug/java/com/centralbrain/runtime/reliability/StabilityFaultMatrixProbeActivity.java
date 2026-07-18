package com.centralbrain.runtime.reliability;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public final class StabilityFaultMatrixProbeActivity extends Activity {
    private static final String TAG = "CbStabilityProbe";
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
            List<StabilityFaultMatrixContract.Observation> observations = new ArrayList<>();
            for (StabilityFaultMatrixContract.MatrixCase matrixCase
                    : StabilityFaultMatrixContract.matrix()) {
                observations.add(new StabilityFaultMatrixContract.Observation(
                        matrixCase.getWorkload(),
                        matrixCase.getFault(),
                        matrixCase.getExpectedOutcome(),
                        1,
                        1,
                        0,
                        0,
                        0,
                        matrixCase.getRecoveryBudgetMillis(),
                        DIGEST));
            }
            StabilityFaultMatrixContract.Report report =
                    StabilityFaultMatrixContract.evaluate(
                            new StabilityFaultMatrixContract.RunContext(
                                    StabilityFaultMatrixContract.EvidenceMode.CONTRACT_TEST,
                                    "p9-w02-probe",
                                    SOURCE_COMMIT,
                                    "cockpit-a13-contract",
                                    DIGEST,
                                    0L,
                                    false),
                            observations);
            boolean matrixVerified = StabilityFaultMatrixContract.matrix().size() == 18
                    && StabilityFaultMatrixContract.WORKLOAD_COUNT == 3
                    && StabilityFaultMatrixContract.FAULT_COUNT == 6;
            boolean reportVerified = report.getCode()
                            == StabilityFaultMatrixContract.ReportCode.PASSED
                    && report.getPassCount() == 18
                    && report.getFailedCount() == 0
                    && report.getIncompleteCount() == 0;
            boolean boundaryVerified = !report.isTargetEvidenceStructurallyComplete()
                    && !report.isTargetHardwareQualified()
                    && !report.isFaultInjectionRuntimeWired()
                    && !report.isHardwareAccessed()
                    && !report.isProductionReady();

            Log.i(TAG, "nonce=" + nonce
                    + " stability_fault_matrix_probe_complete=true"
                    + " stability_fault_matrix_contract_verified="
                    + (matrixVerified && reportVerified && boundaryVerified)
                    + " stability_matrix_verified=" + matrixVerified
                    + " stability_report_validation_verified=" + reportVerified
                    + " stability_boundary_verified=" + boundaryVerified
                    + " stability_workload_count=3"
                    + " stability_fault_count=6"
                    + " stability_matrix_case_count=18"
                    + " stability_target_72h_complete=false"
                    + " stability_fault_injection_runtime_wired=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " stability_fault_matrix_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " stability_target_72h_complete=false"
                    + " stability_fault_injection_runtime_wired=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false", exception);
        }
    }
}
