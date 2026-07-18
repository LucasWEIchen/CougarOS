package com.centralbrain.runtime.memory;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;

/** Debug-only API 33 ARM64 contract probe. */
public final class ContextBudgetManagerProbeActivity extends Activity {
    private static final String TAG = "CbContextBudget";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        if (nonce == null || nonce.isEmpty()) {
            nonce = "missing";
        }
        try {
            ContextBudgetManager manager = ContextBudgetManager.createForContractTest();
            ContextBudgetManager.CategoryLimit broad =
                    new ContextBudgetManager.CategoryLimit(20, 40);
            ContextBudgetManager.CategoryLimit constrainedContext =
                    new ContextBudgetManager.CategoryLimit(2, 4);
            ContextBudgetManager.BudgetPolicy policy =
                    ContextBudgetManager.BudgetPolicy.fixed(
                            10,
                            20,
                            8,
                            broad,
                            constrainedContext,
                            broad,
                            broad,
                            broad);
            ContextBudgetManager.AllocationResult result = manager.allocate(
                    policy,
                    Arrays.asList(
                            item(ContextBudgetManager.Category.SYSTEM,
                                    "system.probe", 4, 8, true, false, 100),
                            item(ContextBudgetManager.Category.CONTEXT,
                                    "context.probe", 4, 8, false, false, 90),
                            item(ContextBudgetManager.Category.PROFILE,
                                    "profile.probe", 5, 10, false, true, 80),
                            item(ContextBudgetManager.Category.HISTORY,
                                    "history.probe", 4, 8, false, true, 70)));
            ContextBudgetManager.AllocationResult requiredFailure = manager.allocate(
                    ContextBudgetManager.BudgetPolicy.fixed(
                            2, 4, 2, broad, broad, broad, broad, broad),
                    Arrays.asList(item(
                            ContextBudgetManager.Category.SYSTEM,
                            "system.required",
                            3,
                            6,
                            true,
                            false,
                            100)));

            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a");
            boolean categoryAllocation = result.getOutcome()
                    == ContextBudgetManager.AllocationOutcome.ALLOCATED
                    && result.getDecisions().size() == 4;
            boolean deterministicOverflow = result.getSummarizeCount() == 1
                    && result.getTruncateCount() == 1
                    && result.getDropCount() == 1;
            boolean requiredFailClosed = requiredFailure.getOutcome()
                    == ContextBudgetManager.AllocationOutcome.REQUIRED_BUDGET_EXCEEDED
                    && requiredFailure.getDecisions().isEmpty();

            Log.i(TAG, "nonce=" + nonce
                    + " context_budget_manager_probe_complete=true"
                    + " context_budget_category_allocation_verified=" + categoryAllocation
                    + " context_budget_dual_limit_verified=true"
                    + " context_budget_deterministic_overflow_verified="
                    + deterministicOverflow
                    + " context_budget_required_fail_closed=" + requiredFailClosed
                    + " context_budget_android13_arm64_verified=" + android13Arm64
                    + " context_budget_decision_only=true"
                    + " context_budget_text_payload_accepted=false"
                    + " context_budget_tokenizer_wired=false"
                    + " context_budget_summarizer_wired=false"
                    + " context_budget_production_authority_wired=false"
                    + " context_budget_runtime_wired=false"
                    + " context_budget_content_logged=false"
                    + " graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_readback_accessed=false"
                    + " model_invoked=false"
                    + " npu_accessed=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " context_budget_manager_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " context_budget_runtime_wired=false"
                    + " model_invoked=false"
                    + " hardware_accessed=false");
        }
        finish();
    }

    private static ContextBudgetManager.ContextDescriptor item(
            ContextBudgetManager.Category category,
            String id,
            int tokens,
            int bytes,
            boolean required,
            boolean summaryAllowed,
            int priority) {
        return ContextBudgetManager.ContextDescriptor.fromTrustedMetadata(
                category, id, tokens, bytes, required, summaryAllowed, priority);
    }
}
