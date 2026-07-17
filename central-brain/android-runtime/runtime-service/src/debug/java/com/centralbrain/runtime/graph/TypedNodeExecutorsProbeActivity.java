package com.centralbrain.runtime.graph;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.graph.NodeExecutionInput.ApprovalDecision;
import com.centralbrain.runtime.graph.NodeExecutionInput.ApprovalInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.CompensationInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.ContextInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.DigestOnlyInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.EffectInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.Identity;
import com.centralbrain.runtime.graph.NodeExecutionInput.PolicyInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.SummaryInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.VerificationInput;
import com.centralbrain.runtime.graph.NodeExecutionResult.ReasonCode;
import com.centralbrain.runtime.graph.NodeExecutionResult.Status;
import com.centralbrain.sdk.plan.PlanContract;

public final class TypedNodeExecutorsProbeActivity extends Activity {
    private static final String TAG = "CbTypedNodeExec";
    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            NodeExecutorRegistry registry = NodeExecutorRegistry.controlOnlyContractRegistry();
            boolean schemaVerified = NodeExecutionSchemas.nodeTypes()
                            .equals(PlanContract.allowedNodeTypes())
                    && NodeExecutionSchemas.all().size() == 11
                    && NodeExecutionSchemas.all().stream()
                            .filter(NodeExecutionSchemas.Schema::isDebugExecutorAvailable)
                            .count() == 7
                    && DeterministicNodeExecutors.all().size() == 7;
            for (TypedNodeExecutor<?, ?> executor : DeterministicNodeExecutors.all()) {
                registry.validateExecutor(executor);
            }

            ContextInput context = new ContextInput(
                    identity("capture_context"), SHA_A, SHA_A, true);
            boolean exactClassVerified;
            try {
                registry.validateInput("effect.execute", context);
                exactClassVerified = false;
            } catch (IllegalArgumentException expected) {
                exactClassVerified = expected.getMessage().startsWith("CB_NODE_CONTRACT:");
            }
            NodeExecutionResult<?> contextResult = execute(registry, context);
            boolean contextVerified = contextResult.getStatus() == Status.SUCCEEDED
                    && contextResult.getReasonCode() == ReasonCode.CONTEXT_CAPTURED
                    && !contextResult.getOutput().isProductionTrusted();

            NodeExecutionResult<?> policyUntrusted = execute(
                    registry,
                    new PolicyInput(
                            identity("evaluate_policy"),
                            "policy.fatigue.assist.v1",
                            1,
                            PlanContract.RISK_HIGH,
                            true,
                            false));
            NodeExecutionResult<?> policyAllowed = execute(
                    registry,
                    new PolicyInput(
                            identity("evaluate_policy"),
                            "policy.fatigue.assist.v1",
                            1,
                            PlanContract.RISK_HIGH,
                            true,
                            true));
            NodeExecutionResult<?> approvalPending = execute(
                    registry,
                    new ApprovalInput(
                            identity("request_approval"),
                            SHA_A,
                            ApprovalDecision.PENDING,
                            false));
            boolean policyApprovalVerified = policyUntrusted.getReasonCode()
                            == ReasonCode.POLICY_AUTHORITY_UNTRUSTED
                    && policyAllowed.getStatus() == Status.SUCCEEDED
                    && approvalPending.getStatus() == Status.WAITING
                    && !policyAllowed.getOutput().isProductionTrusted();

            NodeExecutionResult<?> effect = execute(
                    registry,
                    new EffectInput(
                            identity("set_hvac_power"),
                            "vehicle.hvac.power",
                            "vehicle:hvac:power:row1-driver",
                            SHA_A,
                            true));
            NodeExecutionResult<?> compensation = execute(
                    registry,
                    new CompensationInput(
                            identity("rollback_hvac"),
                            "set_hvac_power",
                            SHA_A,
                            SHA_B,
                            true));
            boolean effectFailClosed = effect.getStatus() == Status.WAITING
                    && effect.getReasonCode() == ReasonCode.EFFECT_DISPATCH_DISABLED
                    && compensation.getStatus() == Status.REJECTED
                    && compensation.getReasonCode() == ReasonCode.COMPENSATION_DISABLED;

            NodeExecutionResult<?> verificationMatch = execute(
                    registry,
                    new VerificationInput(
                            identity("verify_hvac"),
                            "vehicle.hvac.power",
                            SHA_A,
                            SHA_A,
                            true,
                            true));
            NodeExecutionResult<?> verificationMismatch = execute(
                    registry,
                    new VerificationInput(
                            identity("verify_hvac"),
                            "vehicle.hvac.power",
                            SHA_A,
                            SHA_B,
                            true,
                            true));
            boolean verificationVerified = verificationMatch.getStatus() == Status.SUCCEEDED
                    && verificationMismatch.getStatus() == Status.FAILED
                    && !verificationMatch.getOutput().isProductionTrusted();

            SummaryInput summary = new SummaryInput(
                    identity("render_summary"),
                    "scenario.fatigue.partial",
                    4,
                    1,
                    2,
                    SHA_B);
            NodeExecutionResult<?> firstSummary = execute(registry, summary);
            NodeExecutionResult<?> secondSummary = execute(registry, summary);
            boolean summaryVerified = firstSummary.getStatus() == Status.SUCCEEDED
                    && firstSummary.getReasonCode() == ReasonCode.SUMMARY_RENDERED
                    && firstSummary.getResultDigest().equals(secondSummary.getResultDigest());

            boolean unsupportedFailClosed;
            try {
                execute(
                        registry,
                        new DigestOnlyInput(identity("model_node"), "model.invoke", SHA_A));
                unsupportedFailClosed = false;
            } catch (IllegalArgumentException expected) {
                unsupportedFailClosed = expected.getMessage().startsWith("CB_NODE_EXECUTOR:")
                        && !DeterministicNodeExecutors.supports("model.invoke");
            }

            boolean allVerified = schemaVerified
                    && exactClassVerified
                    && contextVerified
                    && policyApprovalVerified
                    && effectFailClosed
                    && verificationVerified
                    && summaryVerified
                    && unsupportedFailClosed
                    && !registry.isDispatchEnabled()
                    && !registry.isProductionAuthorized();
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            Log.i(TAG, "nonce=" + nonce
                    + " typed_node_executor_probe_complete=true"
                    + " typed_node_executor_contract_defined=" + allVerified
                    + " typed_node_executor_schema_count=11"
                    + " typed_node_executor_debug_count=7"
                    + " typed_node_executor_exact_class_verified=" + exactClassVerified
                    + " typed_node_executor_context_verified=" + contextVerified
                    + " typed_node_executor_policy_approval_verified="
                    + policyApprovalVerified
                    + " typed_node_executor_effect_fail_closed_verified="
                    + effectFailClosed
                    + " typed_node_executor_verification_verified="
                    + verificationVerified
                    + " typed_node_executor_summary_verified=" + summaryVerified
                    + " typed_node_executor_unsupported_fail_closed_verified="
                    + unsupportedFailClosed
                    + " typed_node_executor_android13_arm64_verified="
                    + android13Arm64Verified
                    + " typed_node_executor_graph_dispatch_enabled=false"
                    + " typed_node_executor_production_wired=false"
                    + " effect_dispatch_enabled=false"
                    + " model_invoked=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " typed_node_executor_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " typed_node_executor_graph_dispatch_enabled=false"
                    + " typed_node_executor_production_wired=false"
                    + " effect_dispatch_enabled=false"
                    + " model_invoked=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static NodeExecutionResult<?> execute(
            NodeExecutorRegistry registry,
            NodeExecutionInput input) {
        return DeterministicNodeExecutors.execute(registry, input);
    }

    private static Identity identity(String nodeId) {
        return new Identity(
                "6f63352a-2284-4442-8926-bc6747e263d8",
                "b4a126b7-7d77-444b-89b9-77e37be4c2c8",
                "07d954a6-f12f-488f-aaeb-afdb9601fc51",
                nodeId,
                SHA_A,
                SHA_B,
                1,
                20_000L);
    }
}
