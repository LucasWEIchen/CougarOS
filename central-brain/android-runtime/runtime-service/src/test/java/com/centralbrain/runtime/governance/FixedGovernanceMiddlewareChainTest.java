package com.centralbrain.runtime.governance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.centralbrain.runtime.skills.BoundedBuiltInSkillRuntime;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public final class FixedGovernanceMiddlewareChainTest {
    private static final String OWNER = repeat("a", 64);
    private static final String SIGNER = repeat("b", 64);
    private static final String INPUT_DIGEST = repeat("c", 64);
    private static final String OUTPUT_DIGEST = repeat("d", 64);
    private static final String TRACE_DIGEST = repeat("e", 64);

    @Test
    public void fixedStageOrderIsImmutable() {
        FixedGovernanceMiddlewareChain chain = chain(4);
        List<FixedGovernanceMiddlewareChain.StageId> expected = Arrays.asList(
                FixedGovernanceMiddlewareChain.StageId.IDENTITY,
                FixedGovernanceMiddlewareChain.StageId.SCHEMA,
                FixedGovernanceMiddlewareChain.StageId.PRIVACY,
                FixedGovernanceMiddlewareChain.StageId.POLICY,
                FixedGovernanceMiddlewareChain.StageId.QOS,
                FixedGovernanceMiddlewareChain.StageId.TRACE,
                FixedGovernanceMiddlewareChain.StageId.DISPATCH_GATE,
                FixedGovernanceMiddlewareChain.StageId.OUTPUT_GUARD,
                FixedGovernanceMiddlewareChain.StageId.AUDIT);
        assertEquals(expected, chain.stageOrder());
        assertEquals(expected, chain.snapshot().getStageOrder());
        try {
            chain.stageOrder().clear();
            fail("middleware order must be immutable");
        } catch (UnsupportedOperationException expectedFailure) {
            // Expected.
        }
    }

    @Test
    public void allowedContractRunsEveryStageWithoutDispatch() {
        FixedGovernanceMiddlewareChain chain = chain(4);
        FixedGovernanceMiddlewareChain.EvaluationResult result = chain.evaluate(
                validExchange("allow-1"));
        assertEquals(FixedGovernanceMiddlewareChain.Decision.ALLOWED,
                result.getDecision());
        assertNull(result.getFirstRejectedStage());
        assertEquals(FixedGovernanceMiddlewareChain.ReasonCode.NONE,
                result.getReasonCode());
        assertEquals(9, result.getStages().size());
        for (int index = 0; index < result.getStages().size() - 1; index++) {
            assertEquals(FixedGovernanceMiddlewareChain.StageStatus.PASSED,
                    result.getStages().get(index).getStatus());
        }
        assertEquals(FixedGovernanceMiddlewareChain.StageStatus.RECORDED,
                result.findStage(FixedGovernanceMiddlewareChain.StageId.AUDIT).getStatus());
        assertTrue(result.isDispatchContractAllowed());
        assertFalse(result.isServiceDispatchTriggered());
        assertEquals(1, result.getAudit().getSequence());
    }

    @Test
    public void firstRejectionSkipsBusinessStagesAndAlwaysAudits() {
        FixedGovernanceMiddlewareChain chain = chain(4);
        FixedGovernanceMiddlewareChain.EvaluationResult result = chain.evaluate(exchange(
                "privacy-denied",
                true,
                true,
                queryManifest().getInputSchemaId(),
                FixedGovernanceMiddlewareChain.DataClass.VEHICLE_SENSITIVE,
                FixedGovernanceMiddlewareChain.RouteScope.EXTERNAL,
                true,
                true,
                readCapability(),
                BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                100,
                1_100,
                500,
                1024,
                true,
                true,
                true,
                queryManifest().getOutputSchemaId(),
                128,
                true));
        assertEquals(FixedGovernanceMiddlewareChain.Decision.DENIED,
                result.getDecision());
        assertEquals(FixedGovernanceMiddlewareChain.StageId.PRIVACY,
                result.getFirstRejectedStage());
        assertEquals(FixedGovernanceMiddlewareChain.ReasonCode.PRIVACY_EXTERNAL_ROUTE_DENIED,
                result.getReasonCode());
        assertEquals(FixedGovernanceMiddlewareChain.StageStatus.PASSED,
                result.findStage(FixedGovernanceMiddlewareChain.StageId.SCHEMA).getStatus());
        assertEquals(FixedGovernanceMiddlewareChain.StageStatus.REJECTED,
                result.findStage(FixedGovernanceMiddlewareChain.StageId.PRIVACY).getStatus());
        for (FixedGovernanceMiddlewareChain.StageId stage : Arrays.asList(
                FixedGovernanceMiddlewareChain.StageId.POLICY,
                FixedGovernanceMiddlewareChain.StageId.QOS,
                FixedGovernanceMiddlewareChain.StageId.TRACE,
                FixedGovernanceMiddlewareChain.StageId.DISPATCH_GATE,
                FixedGovernanceMiddlewareChain.StageId.OUTPUT_GUARD)) {
            assertEquals(FixedGovernanceMiddlewareChain.StageStatus.SKIPPED,
                    result.findStage(stage).getStatus());
        }
        assertEquals(FixedGovernanceMiddlewareChain.StageStatus.RECORDED,
                result.findStage(FixedGovernanceMiddlewareChain.StageId.AUDIT).getStatus());
        assertEquals(FixedGovernanceMiddlewareChain.StageId.PRIVACY,
                result.getAudit().getFirstRejectedStage());
        assertFalse(result.isDispatchContractAllowed());
        assertFalse(result.isServiceDispatchTriggered());
    }

    @Test
    public void identitySchemaPolicyQosTraceAndDispatchFailClosed() {
        assertRejected(
                exchange(
                        "identity",
                        false,
                        true,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        true,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        100,
                        1_100,
                        500,
                        1024,
                        true,
                        true,
                        true,
                        queryManifest().getOutputSchemaId(),
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.IDENTITY,
                FixedGovernanceMiddlewareChain.ReasonCode.IDENTITY_UNTRUSTED);
        assertRejected(
                exchange(
                        "schema",
                        true,
                        true,
                        "skill.vehicle.state.query.input.v2",
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        true,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        100,
                        1_100,
                        500,
                        1024,
                        true,
                        true,
                        true,
                        queryManifest().getOutputSchemaId(),
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.SCHEMA,
                FixedGovernanceMiddlewareChain.ReasonCode.INPUT_SCHEMA_MISMATCH);
        assertRejected(
                exchange(
                        "policy",
                        true,
                        true,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        true,
                        Collections.emptySet(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        100,
                        1_100,
                        500,
                        1024,
                        true,
                        true,
                        true,
                        queryManifest().getOutputSchemaId(),
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.POLICY,
                FixedGovernanceMiddlewareChain.ReasonCode.POLICY_CAPABILITY_DENIED);
        assertRejected(
                exchange(
                        "qos",
                        true,
                        true,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        true,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        1_100,
                        1_100,
                        500,
                        1024,
                        true,
                        true,
                        true,
                        queryManifest().getOutputSchemaId(),
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.QOS,
                FixedGovernanceMiddlewareChain.ReasonCode.QOS_DEADLINE_EXPIRED);
        assertRejected(
                exchange(
                        "trace",
                        true,
                        true,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        true,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        100,
                        1_100,
                        500,
                        1024,
                        false,
                        true,
                        true,
                        queryManifest().getOutputSchemaId(),
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.TRACE,
                FixedGovernanceMiddlewareChain.ReasonCode.TRACE_CONTEXT_UNTRUSTED);
        assertRejected(
                exchange(
                        "dispatch",
                        true,
                        true,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        true,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        100,
                        1_100,
                        500,
                        1024,
                        true,
                        false,
                        true,
                        queryManifest().getOutputSchemaId(),
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.DISPATCH_GATE,
                FixedGovernanceMiddlewareChain.ReasonCode.DISPATCH_ROUTE_UNRESOLVED);
    }

    @Test
    public void remainingDecisionBranchesFailClosed() {
        assertRejected(
                exchange(
                        "signer",
                        true,
                        false,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        true,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        100,
                        1_100,
                        500,
                        1024,
                        true,
                        true,
                        true,
                        queryManifest().getOutputSchemaId(),
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.IDENTITY,
                FixedGovernanceMiddlewareChain.ReasonCode.IDENTITY_SIGNER_MISMATCH);
        assertRejected(
                exchange(
                        "purpose",
                        true,
                        true,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        false,
                        true,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        100,
                        1_100,
                        500,
                        1024,
                        true,
                        true,
                        true,
                        queryManifest().getOutputSchemaId(),
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.PRIVACY,
                FixedGovernanceMiddlewareChain.ReasonCode.PRIVACY_PURPOSE_DENIED);
        assertRejected(
                exchange(
                        "consent",
                        true,
                        true,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.CABIN_PROFILE,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        false,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        100,
                        1_100,
                        500,
                        1024,
                        true,
                        true,
                        true,
                        queryManifest().getOutputSchemaId(),
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.PRIVACY,
                FixedGovernanceMiddlewareChain.ReasonCode.PRIVACY_CONSENT_REQUIRED);
        assertRejected(
                exchange(
                        "safety",
                        true,
                        true,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        true,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.EMERGENCY,
                        100,
                        1_100,
                        500,
                        1024,
                        true,
                        true,
                        true,
                        queryManifest().getOutputSchemaId(),
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.POLICY,
                FixedGovernanceMiddlewareChain.ReasonCode.POLICY_SAFETY_STATE_DENIED);
        assertRejected(
                exchange(
                        "qos-budget",
                        true,
                        true,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        true,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        -1,
                        1_100,
                        2_000,
                        1024,
                        true,
                        true,
                        true,
                        queryManifest().getOutputSchemaId(),
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.QOS,
                FixedGovernanceMiddlewareChain.ReasonCode.QOS_BUDGET_INVALID);
        assertRejected(
                exchange(
                        "dispatch-policy",
                        true,
                        true,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        true,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        100,
                        1_100,
                        500,
                        1024,
                        true,
                        true,
                        false,
                        queryManifest().getOutputSchemaId(),
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.DISPATCH_GATE,
                FixedGovernanceMiddlewareChain.ReasonCode.DISPATCH_POLICY_DENIED);
        assertRejected(
                exchange(
                        "output-schema",
                        true,
                        true,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        true,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        100,
                        1_100,
                        500,
                        1024,
                        true,
                        true,
                        true,
                        "skill.vehicle.state.query.output.v2",
                        128,
                        true),
                FixedGovernanceMiddlewareChain.StageId.OUTPUT_GUARD,
                FixedGovernanceMiddlewareChain.ReasonCode.OUTPUT_SCHEMA_MISMATCH);
        assertRejected(
                exchange(
                        "output-size",
                        true,
                        true,
                        queryManifest().getInputSchemaId(),
                        FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                        FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                        true,
                        true,
                        readCapability(),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                        100,
                        1_100,
                        500,
                        1024,
                        true,
                        true,
                        true,
                        queryManifest().getOutputSchemaId(),
                        2_048,
                        true),
                FixedGovernanceMiddlewareChain.StageId.OUTPUT_GUARD,
                FixedGovernanceMiddlewareChain.ReasonCode.OUTPUT_SIZE_EXCEEDED);
    }

    @Test
    public void outputGuardAndAuditEvidenceAreDigestOnlyAndBounded() {
        FixedGovernanceMiddlewareChain chain = chain(2);
        FixedGovernanceMiddlewareChain.EvaluationResult outputDenied = chain.evaluate(exchange(
                "output-denied",
                true,
                true,
                queryManifest().getInputSchemaId(),
                FixedGovernanceMiddlewareChain.DataClass.CABIN_PROFILE,
                FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                true,
                true,
                readCapability(),
                BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                100,
                1_100,
                500,
                1024,
                true,
                true,
                true,
                queryManifest().getOutputSchemaId(),
                128,
                false));
        assertEquals(FixedGovernanceMiddlewareChain.StageId.OUTPUT_GUARD,
                outputDenied.getFirstRejectedStage());
        assertEquals(FixedGovernanceMiddlewareChain.ReasonCode.OUTPUT_REDACTION_REQUIRED,
                outputDenied.getReasonCode());
        assertTrue(outputDenied.isDispatchContractAllowed());
        assertFalse(outputDenied.isServiceDispatchTriggered());

        chain.evaluate(validExchange("audit-2"));
        chain.evaluate(validExchange("audit-3"));
        List<FixedGovernanceMiddlewareChain.AuditRecord> audits = chain.recentAudits(2);
        assertEquals(2, audits.size());
        assertEquals(2, audits.get(0).getSequence());
        assertEquals(3, audits.get(1).getSequence());
        for (FixedGovernanceMiddlewareChain.AuditRecord audit : audits) {
            assertTrue(audit.getRequestFingerprint().matches("[0-9a-f]{64}"));
            assertTrue(audit.getAuditDigest().matches("[0-9a-f]{64}"));
            assertFalse(audit.getRequestFingerprint().equals(INPUT_DIGEST));
            assertFalse(audit.getAuditDigest().equals(OUTPUT_DIGEST));
        }
        assertEquals(1, chain.snapshot().getAuditEvictionCount());
        try {
            audits.clear();
            fail("audit records must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void snapshotKeepsProductionNetworkAndHardwareDisabled() {
        FixedGovernanceMiddlewareChain chain = chain(2);
        chain.evaluate(validExchange("snapshot"));
        FixedGovernanceMiddlewareChain.Snapshot snapshot = chain.snapshot();
        assertEquals(1, snapshot.getEvaluationCount());
        assertEquals(1, snapshot.getAllowedCount());
        assertEquals(0, snapshot.getDeniedCount());
        assertEquals(1, snapshot.getRetainedAuditCount());
        assertFalse(snapshot.isProductionMiddlewareWired());
        assertFalse(snapshot.isDispatchExecutionEnabled());
        assertFalse(snapshot.isServiceDispatchTriggered());
        assertFalse(snapshot.isRawInputStored());
        assertFalse(snapshot.isRawOutputStored());
        assertFalse(snapshot.isAuditPersistenceWired());
        assertFalse(snapshot.isNetworkAccessEnabled());
        assertFalse(snapshot.isHardwareAccessed());
    }

    private static void assertRejected(
            FixedGovernanceMiddlewareChain.TrustedExchange exchange,
            FixedGovernanceMiddlewareChain.StageId stage,
            FixedGovernanceMiddlewareChain.ReasonCode reason) {
        FixedGovernanceMiddlewareChain.EvaluationResult result = chain(4).evaluate(exchange);
        assertEquals(FixedGovernanceMiddlewareChain.Decision.DENIED,
                result.getDecision());
        assertEquals(stage, result.getFirstRejectedStage());
        assertEquals(reason, result.getReasonCode());
        assertEquals(FixedGovernanceMiddlewareChain.StageStatus.REJECTED,
                result.findStage(stage).getStatus());
        assertEquals(FixedGovernanceMiddlewareChain.StageStatus.RECORDED,
                result.findStage(FixedGovernanceMiddlewareChain.StageId.AUDIT).getStatus());
        assertFalse(result.isServiceDispatchTriggered());
    }

    private static FixedGovernanceMiddlewareChain chain(int maxAudits) {
        return FixedGovernanceMiddlewareChain.createForContractTest(
                new FixedGovernanceMiddlewareChain.Limits(maxAudits));
    }

    private static FixedGovernanceMiddlewareChain.TrustedExchange validExchange(
            String requestId) {
        return exchange(
                requestId,
                true,
                true,
                queryManifest().getInputSchemaId(),
                FixedGovernanceMiddlewareChain.DataClass.PUBLIC,
                FixedGovernanceMiddlewareChain.RouteScope.LOCAL_PROCESS,
                true,
                true,
                readCapability(),
                BoundedBuiltInSkillRuntime.SafetyState.NORMAL,
                100,
                1_100,
                500,
                1024,
                true,
                true,
                true,
                queryManifest().getOutputSchemaId(),
                128,
                true);
    }

    private static FixedGovernanceMiddlewareChain.TrustedExchange exchange(
            String requestId,
            boolean identityVerified,
            boolean sameSigner,
            String inputSchema,
            FixedGovernanceMiddlewareChain.DataClass dataClass,
            FixedGovernanceMiddlewareChain.RouteScope routeScope,
            boolean purposeAuthorized,
            boolean consentSatisfied,
            Set<BoundedBuiltInSkillRuntime.Capability> capabilities,
            BoundedBuiltInSkillRuntime.SafetyState safetyState,
            long now,
            long deadline,
            long budget,
            int maxOutputBytes,
            boolean traceTrusted,
            boolean routeOwnerResolved,
            boolean routePolicyMatched,
            String outputSchema,
            int outputSize,
            boolean redactionApplied) {
        BoundedBuiltInSkillRuntime.SkillManifest manifest = queryManifest();
        return FixedGovernanceMiddlewareChain.TrustedExchange.fromRuntimePolicy(
                requestId,
                manifest,
                FixedGovernanceMiddlewareChain.IdentityEvidence.fromRuntimeIdentity(
                        OWNER,
                        SIGNER,
                        identityVerified,
                        sameSigner),
                inputSchema,
                INPUT_DIGEST,
                FixedGovernanceMiddlewareChain.PrivacyEvidence.fromRuntimePolicy(
                        dataClass,
                        routeScope,
                        "vehicle.assistance",
                        purposeAuthorized,
                        consentSatisfied),
                capabilities,
                safetyState,
                FixedGovernanceMiddlewareChain.QosEvidence.fromRuntimePolicy(
                        FixedGovernanceMiddlewareChain.QosClass.INTERACTIVE,
                        now,
                        deadline,
                        budget,
                        maxOutputBytes),
                FixedGovernanceMiddlewareChain.TraceEvidence.fromRuntimeTrace(
                        TRACE_DIGEST,
                        traceTrusted),
                FixedGovernanceMiddlewareChain.DispatchEvidence.fromRuntimeRegistry(
                        routeOwnerResolved,
                        routePolicyMatched),
                FixedGovernanceMiddlewareChain.OutputEvidence.fromContractFixture(
                        outputSchema,
                        OUTPUT_DIGEST,
                        outputSize,
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
