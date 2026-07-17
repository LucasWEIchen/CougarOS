package com.centralbrain.runtime.graph;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.graph.ApprovalInterruptRecord.Decision;
import com.centralbrain.runtime.graph.ApprovalResumeValidator.Reason;
import com.centralbrain.runtime.graph.ApprovalResumeValidator.ResumeContext;
import com.centralbrain.runtime.graph.ApprovalResumeValidator.SafetyState;

import java.util.List;

public final class ApprovalInterruptProbeActivity extends Activity {
    private static final String TAG = "CbApprovalInterrupt";
    private static final long NOW = 1_700_000_000_000L;
    private static final String OWNER = "a".repeat(64);
    private static final String ACTION = "b".repeat(64);
    private static final String PLAN = "c".repeat(64);
    private static final String CONTEXT = "d".repeat(64);
    private static final String POLICY = "e".repeat(64);
    private static final String SAFETY = "f".repeat(64);
    private static final String AUTHORITY = "1".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            ApprovalInterruptRecord pending = ApprovalInterruptExecutor.createPending(
                    request(), NOW);
            boolean bindingVerified = pending.getDecision() == Decision.PENDING
                    && pending.getOwnerFingerprint().equals(OWNER)
                    && pending.getPlanDigest().equals(PLAN)
                    && pending.getContextDigest().equals(CONTEXT)
                    && pending.getPolicyDigest().equals(POLICY)
                    && pending.getExpiresAtEpochMs() == NOW + 60_000L;

            JsonPrimitiveCheckpointSerializer serializer =
                    new JsonPrimitiveCheckpointSerializer(List.of(
                            ApprovalInterruptExecutor.checkpointRegistration()));
            CheckpointEnvelope envelope = serializer.create(
                    ApprovalInterruptExecutor.CHECKPOINT_TYPE,
                    ApprovalInterruptExecutor.CHECKPOINT_SCHEMA_VERSION,
                    pending.getNodeId(),
                    pending.getPlanDigest(),
                    pending.getContextDigest(),
                    pending,
                    NOW);
            ApprovalInterruptRecord restored = serializer.decodePayload(
                    serializer.deserialize(serializer.serialize(envelope)),
                    ApprovalInterruptRecord.class);
            boolean checkpointVerified = pending.equals(restored)
                    && pending.getRecordDigest().equals(restored.getRecordDigest());

            ApprovalInterruptRecord approved = ApprovalInterruptExecutor.recordDecision(
                    restored, Decision.APPROVED, AUTHORITY, true, NOW + 1_000L);
            boolean trustedDecisionVerified = approved.getDecision() == Decision.APPROVED
                    && approved.isAuthorityTrusted()
                    && approved.getAuthorityDigest().equals(AUTHORITY);
            boolean resumeBindingVerified = ApprovalResumeValidator.validate(
                    approved, context(SAFETY, SafetyState.SAFE, true, NOW + 2_000L))
                    .isAllowed();
            boolean safetyVerified = ApprovalResumeValidator.validate(
                            approved,
                            context("2".repeat(64), SafetyState.SAFE, true, NOW + 2_000L))
                    .getReason() == Reason.SAFETY_CHANGED
                    && ApprovalResumeValidator.validate(
                            approved,
                            context(SAFETY, SafetyState.UNKNOWN, true, NOW + 2_000L))
                    .getReason() == Reason.SAFETY_UNSAFE
                    && ApprovalResumeValidator.validate(
                            approved,
                            context(SAFETY, SafetyState.SAFE, false, NOW + 2_000L))
                    .getReason() == Reason.SAFETY_UNTRUSTED;
            boolean expiryVerified = ApprovalResumeValidator.validate(
                            approved,
                            context(SAFETY, SafetyState.SAFE, true, approved.getExpiresAtEpochMs()))
                    .getReason() == Reason.EXPIRED;
            boolean allVerified = bindingVerified
                    && checkpointVerified
                    && trustedDecisionVerified
                    && resumeBindingVerified
                    && safetyVerified
                    && expiryVerified;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            Log.i(TAG, "nonce=" + nonce
                    + " approval_interrupt_probe_complete=" + allVerified
                    + " approval_interrupt_record_defined=" + allVerified
                    + " approval_interrupt_binding_verified=" + bindingVerified
                    + " approval_interrupt_checkpoint_roundtrip_verified=" + checkpointVerified
                    + " approval_interrupt_trusted_decision_verified=" + trustedDecisionVerified
                    + " approval_resume_owner_plan_context_policy_verified="
                    + resumeBindingVerified
                    + " approval_resume_safety_revalidation_verified=" + safetyVerified
                    + " approval_resume_expiry_verified=" + expiryVerified
                    + " approval_interrupt_android13_arm64_verified="
                    + android13Arm64Verified
                    + " approval_interrupt_persistence_wired=false"
                    + " approval_grant_service_published=false"
                    + " agent_graph_executor_dispatch_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " model_invoked=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " approval_interrupt_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " approval_interrupt_persistence_wired=false"
                    + " approval_grant_service_published=false"
                    + " agent_graph_executor_dispatch_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static ApprovalInterruptExecutor.Request request() {
        return new ApprovalInterruptExecutor.Request(
                "10000000-0000-4000-8000-000000000001",
                OWNER,
                "20000000-0000-4000-8000-000000000002",
                "30000000-0000-4000-8000-000000000003",
                "approval_gate",
                ACTION,
                PLAN,
                CONTEXT,
                POLICY,
                SAFETY,
                60_000L,
                NOW + 120_000L);
    }

    private static ResumeContext context(
            String safetyDigest,
            SafetyState state,
            boolean trusted,
            long nowEpochMs) {
        return new ResumeContext(
                OWNER,
                "20000000-0000-4000-8000-000000000002",
                "30000000-0000-4000-8000-000000000003",
                "approval_gate",
                ACTION,
                PLAN,
                CONTEXT,
                POLICY,
                safetyDigest,
                nowEpochMs,
                true,
                true,
                true,
                trusted,
                state);
    }
}
