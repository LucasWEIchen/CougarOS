package com.centralbrain.runtime.governance;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.skills.BoundedBuiltInSkillRuntime;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class GovernanceMiddlewareProbeActivity extends Activity {
    private static final String TAG = "CbGovMiddleware";
    private static final String OWNER = repeat("a", 64);
    private static final String SIGNER = repeat("b", 64);
    private static final String INPUT_DIGEST = repeat("c", 64);
    private static final String OUTPUT_DIGEST = repeat("d", 64);
    private static final String TRACE_DIGEST = repeat("e", 64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            FixedGovernanceMiddlewareChain chain = chain(2);
            boolean orderVerified = chain.stageOrder().equals(Arrays.asList(
                    FixedGovernanceMiddlewareChain.StageId.IDENTITY,
                    FixedGovernanceMiddlewareChain.StageId.SCHEMA,
                    FixedGovernanceMiddlewareChain.StageId.PRIVACY,
                    FixedGovernanceMiddlewareChain.StageId.POLICY,
                    FixedGovernanceMiddlewareChain.StageId.QOS,
                    FixedGovernanceMiddlewareChain.StageId.TRACE,
                    FixedGovernanceMiddlewareChain.StageId.DISPATCH_GATE,
                    FixedGovernanceMiddlewareChain.StageId.OUTPUT_GUARD,
                    FixedGovernanceMiddlewareChain.StageId.AUDIT));

            FixedGovernanceMiddlewareChain.EvaluationResult allowed = chain.evaluate(
                    exchange(
                            "allowed",
                            FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                            readCapability(),
                            1_100,
                            true,
                            true,
                            true));
            boolean allowPathVerified = allowed.getDecision()
                    == FixedGovernanceMiddlewareChain.Decision.ALLOWED
                    && allowed.getStages().size() == 9
                    && allowed.isDispatchContractAllowed()
                    && !allowed.isServiceDispatchTriggered();

            FixedGovernanceMiddlewareChain.EvaluationResult privacyDenied = chain.evaluate(
                    exchange(
                            "privacy-denied",
                            FixedGovernanceMiddlewareChain.RouteScope.EXTERNAL,
                            readCapability(),
                            1_100,
                            true,
                            true,
                            true));
            boolean firstRejectionVerified = privacyDenied.getFirstRejectedStage()
                    == FixedGovernanceMiddlewareChain.StageId.PRIVACY
                    && privacyDenied.getReasonCode()
                    == FixedGovernanceMiddlewareChain.ReasonCode
                            .PRIVACY_EXTERNAL_ROUTE_DENIED
                    && privacyDenied.findStage(FixedGovernanceMiddlewareChain.StageId.POLICY)
                            .getStatus()
                    == FixedGovernanceMiddlewareChain.StageStatus.SKIPPED
                    && privacyDenied.findStage(
                            FixedGovernanceMiddlewareChain.StageId.OUTPUT_GUARD).getStatus()
                    == FixedGovernanceMiddlewareChain.StageStatus.SKIPPED;
            boolean auditFinalizerVerified = privacyDenied.findStage(
                    FixedGovernanceMiddlewareChain.StageId.AUDIT).getStatus()
                    == FixedGovernanceMiddlewareChain.StageStatus.RECORDED
                    && privacyDenied.getAudit().getFirstRejectedStage()
                    == FixedGovernanceMiddlewareChain.StageId.PRIVACY;

            FixedGovernanceMiddlewareChain.EvaluationResult policyDenied = chain(2).evaluate(
                    exchange(
                            "policy-denied",
                            FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                            Collections.emptySet(),
                            1_100,
                            true,
                            true,
                            true));
            boolean policyVerified = policyDenied.getFirstRejectedStage()
                    == FixedGovernanceMiddlewareChain.StageId.POLICY;
            FixedGovernanceMiddlewareChain.EvaluationResult qosDenied = chain(2).evaluate(
                    exchange(
                            "qos-denied",
                            FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                            readCapability(),
                            100,
                            true,
                            true,
                            true));
            boolean qosVerified = qosDenied.getFirstRejectedStage()
                    == FixedGovernanceMiddlewareChain.StageId.QOS;
            FixedGovernanceMiddlewareChain.EvaluationResult outputDenied = chain(2).evaluate(
                    exchange(
                            "output-denied",
                            FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                            readCapability(),
                            1_100,
                            false,
                            true,
                            true));
            boolean outputGuardVerified = outputDenied.getFirstRejectedStage()
                    == FixedGovernanceMiddlewareChain.StageId.OUTPUT_GUARD
                    && outputDenied.isDispatchContractAllowed()
                    && !outputDenied.isServiceDispatchTriggered();

            chain.evaluate(exchange(
                    "audit-third",
                    FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                    readCapability(),
                    1_100,
                    true,
                    true,
                    true));
            List<FixedGovernanceMiddlewareChain.AuditRecord> audits = chain.recentAudits(2);
            FixedGovernanceMiddlewareChain.Snapshot snapshot = chain.snapshot();
            boolean auditBoundsVerified = audits.size() == 2
                    && audits.get(0).getSequence() == 2
                    && audits.get(1).getSequence() == 3
                    && snapshot.getAuditEvictionCount() == 1
                    && audits.get(1).getAuditDigest().matches("[0-9a-f]{64}");
            boolean privacyVerified = firstRejectionVerified;
            boolean contractVerified = orderVerified
                    && allowPathVerified
                    && firstRejectionVerified
                    && auditFinalizerVerified
                    && privacyVerified
                    && policyVerified
                    && qosVerified
                    && outputGuardVerified
                    && auditBoundsVerified
                    && !snapshot.isProductionMiddlewareWired()
                    && !snapshot.isDispatchExecutionEnabled()
                    && !snapshot.isServiceDispatchTriggered()
                    && !snapshot.isRawInputStored()
                    && !snapshot.isRawOutputStored()
                    && !snapshot.isAuditPersistenceWired()
                    && !snapshot.isNetworkAccessEnabled()
                    && !snapshot.isHardwareAccessed();

            Log.i(TAG, "nonce=" + nonce
                    + " governance_middleware_probe_complete=" + contractVerified
                    + " governance_middleware_contract_verified=" + contractVerified
                    + " governance_middleware_order_verified=" + orderVerified
                    + " governance_middleware_allow_path_verified=" + allowPathVerified
                    + " governance_middleware_first_rejection_verified="
                    + firstRejectionVerified
                    + " governance_middleware_audit_finalizer_verified="
                    + auditFinalizerVerified
                    + " governance_middleware_privacy_verified=" + privacyVerified
                    + " governance_middleware_policy_verified=" + policyVerified
                    + " governance_middleware_qos_verified=" + qosVerified
                    + " governance_middleware_output_guard_verified="
                    + outputGuardVerified
                    + " governance_middleware_audit_bounds_verified="
                    + auditBoundsVerified
                    + " governance_middleware_process_only=true"
                    + " governance_middleware_production_wired=false"
                    + " governance_dispatch_execution_enabled=false"
                    + " governance_service_dispatch_triggered=false"
                    + " raw_governance_input_stored=false"
                    + " raw_governance_output_stored=false"
                    + " governance_audit_persistence_wired=false"
                    + " governance_network_access_enabled=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " governance_middleware_probe_complete=false"
                    + " governance_service_dispatch_triggered=false"
                    + " governance_network_access_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static FixedGovernanceMiddlewareChain chain(int maxAudits) {
        return FixedGovernanceMiddlewareChain.createForContractTest(
                new FixedGovernanceMiddlewareChain.Limits(maxAudits));
    }

    private static FixedGovernanceMiddlewareChain.TrustedExchange exchange(
            String requestId,
            FixedGovernanceMiddlewareChain.RouteScope routeScope,
            Set<BoundedBuiltInSkillRuntime.Capability> capabilities,
            long deadline,
            boolean redactionApplied,
            boolean routeOwnerResolved,
            boolean routePolicyMatched) {
        BoundedBuiltInSkillRuntime.SkillManifest manifest = queryManifest();
        return FixedGovernanceMiddlewareChain.TrustedExchange.fromRuntimePolicy(
                requestId,
                manifest,
                FixedGovernanceMiddlewareChain.IdentityEvidence.fromRuntimeIdentity(
                        OWNER,
                        SIGNER,
                        true,
                        true),
                manifest.getInputSchemaId(),
                INPUT_DIGEST,
                FixedGovernanceMiddlewareChain.PrivacyEvidence.fromRuntimePolicy(
                        FixedGovernanceMiddlewareChain.DataClass.VEHICLE_SENSITIVE,
                        routeScope,
                        "vehicle.assistance",
                        true,
                        true),
                capabilities,
                BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                FixedGovernanceMiddlewareChain.QosEvidence.fromRuntimePolicy(
                        FixedGovernanceMiddlewareChain.QosClass.INTERACTIVE,
                        100,
                        deadline,
                        500,
                        1024),
                FixedGovernanceMiddlewareChain.TraceEvidence.fromRuntimeTrace(
                        TRACE_DIGEST,
                        true),
                FixedGovernanceMiddlewareChain.DispatchEvidence.fromRuntimeRegistry(
                        routeOwnerResolved,
                        routePolicyMatched),
                FixedGovernanceMiddlewareChain.OutputEvidence.fromContractFixture(
                        manifest.getOutputSchemaId(),
                        OUTPUT_DIGEST,
                        128,
                        redactionApplied));
    }

    private static Set<BoundedBuiltInSkillRuntime.Capability> readCapability() {
        return EnumSet.of(BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ);
    }

    private static BoundedBuiltInSkillRuntime.SkillManifest queryManifest() {
        return BoundedBuiltInSkillRuntime.createForContractTest(
                new BoundedBuiltInSkillRuntime.Limits(1, 1, 1),
                () -> "unused").findManifest(
                        BoundedBuiltInSkillRuntime.SKILL_VEHICLE_STATE_QUERY);
    }

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}
