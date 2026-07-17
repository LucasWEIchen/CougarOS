package com.centralbrain.runtime.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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
import com.centralbrain.runtime.graph.NodeExecutionOutput.EffectOutput;
import com.centralbrain.runtime.graph.NodeExecutionResult.ReasonCode;
import com.centralbrain.runtime.graph.NodeExecutionResult.Status;
import com.centralbrain.sdk.plan.PlanContract;

import org.junit.Test;

public final class TypedNodeExecutorsTest {
    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);
    private static final String SHA_C = "c".repeat(64);

    @Test
    public void schemasExactlyCoverPlanAllowlistAndExposeSevenDebugContracts() {
        assertEquals(PlanContract.allowedNodeTypes(), NodeExecutionSchemas.nodeTypes());
        assertEquals(11, NodeExecutionSchemas.all().size());
        assertEquals(7, NodeExecutionSchemas.all().stream()
                .filter(NodeExecutionSchemas.Schema::isDebugExecutorAvailable)
                .count());
        assertEquals(7, DeterministicNodeExecutors.all().size());
        assertFalse(NodeExecutionSchemas.schemaFor("model.invoke").isDebugExecutorAvailable());
        assertFalse(DeterministicNodeExecutors.supports("model.invoke"));
        assertThrows(
                IllegalArgumentException.class,
                () -> NodeExecutionSchemas.schemaFor("arbitrary.deserialize"));
    }

    @Test
    public void registryEnforcesExactClassesAndRejectsUnsafeExecutorDeclarations() {
        NodeExecutorRegistry registry = NodeExecutorRegistry.controlOnlyContractRegistry();
        for (TypedNodeExecutor<?, ?> executor : DeterministicNodeExecutors.all()) {
            registry.validateExecutor(executor);
        }
        ContextInput context = contextInput(SHA_A, SHA_A, false);
        registry.validateInput("context.capture", context);
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.validateInput("effect.execute", context));

        TypedNodeExecutor<EffectInput, EffectOutput> unsafe =
                new TypedNodeExecutor<EffectInput, EffectOutput>() {
                    @Override
                    public String nodeType() {
                        return "effect.execute";
                    }

                    @Override
                    public Class<EffectInput> inputType() {
                        return EffectInput.class;
                    }

                    @Override
                    public Class<EffectOutput> outputType() {
                        return EffectOutput.class;
                    }

                    @Override
                    public NodeExecutionResult<EffectOutput> execute(EffectInput input) {
                        throw new AssertionError("must not execute");
                    }

                    @Override
                    public boolean mayDispatchEffect() {
                        return true;
                    }
                };
        assertThrows(IllegalArgumentException.class, () -> registry.validateExecutor(unsafe));
        assertFalse(registry.isDispatchEnabled());
        assertFalse(registry.isProductionAuthorized());
    }

    @Test
    public void contextExecutorBindsExpectedSnapshotAndNeverUpgradesTrust() {
        NodeExecutionResult<?> success = execute(contextInput(SHA_A, SHA_A, true));
        NodeExecutionResult<?> mismatch = execute(contextInput(SHA_A, SHA_B, false));

        assertEquals(Status.SUCCEEDED, success.getStatus());
        assertEquals(ReasonCode.CONTEXT_CAPTURED, success.getReasonCode());
        assertFalse(success.getOutput().isProductionTrusted());
        assertEquals(Status.FAILED, mismatch.getStatus());
        assertEquals(ReasonCode.CONTEXT_DIGEST_MISMATCH, mismatch.getReasonCode());
        assertNotEquals(success.getResultDigest(), mismatch.getResultDigest());
    }

    @Test
    public void policyAndApprovalRequireExplicitTrustedAuthorityEvidence() {
        PolicyInput untrusted = new PolicyInput(
                identity("evaluate_policy"),
                "policy.fatigue.assist.v1",
                1,
                PlanContract.RISK_HIGH,
                true,
                false);
        PolicyInput denied = new PolicyInput(
                identity("evaluate_policy"),
                "policy.fatigue.assist.v1",
                1,
                PlanContract.RISK_HIGH,
                false,
                true);
        PolicyInput allowed = new PolicyInput(
                identity("evaluate_policy"),
                "policy.fatigue.assist.v1",
                1,
                PlanContract.RISK_HIGH,
                true,
                true);
        assertEquals(ReasonCode.POLICY_AUTHORITY_UNTRUSTED, execute(untrusted).getReasonCode());
        assertEquals(ReasonCode.POLICY_DENIED, execute(denied).getReasonCode());
        assertEquals(Status.SUCCEEDED, execute(allowed).getStatus());

        ApprovalInput pending = new ApprovalInput(
                identity("request_approval"), SHA_A, ApprovalDecision.PENDING, false);
        ApprovalInput untrustedApproval = new ApprovalInput(
                identity("request_approval"), SHA_A, ApprovalDecision.APPROVED, false);
        ApprovalInput approved = new ApprovalInput(
                identity("request_approval"), SHA_A, ApprovalDecision.APPROVED, true);
        assertEquals(Status.WAITING, execute(pending).getStatus());
        assertEquals(
                ReasonCode.APPROVAL_AUTHORITY_UNTRUSTED,
                execute(untrustedApproval).getReasonCode());
        assertEquals(Status.SUCCEEDED, execute(approved).getStatus());
        assertFalse(execute(approved).getOutput().isProductionTrusted());
    }

    @Test
    public void effectAndCompensationExecutorsAlwaysFailClosedWithoutDispatch() {
        EffectInput effect = new EffectInput(
                identity("set_hvac_power"),
                "vehicle.hvac.power",
                "vehicle:hvac:power:row1-driver",
                SHA_A,
                true);
        CompensationInput compensation = new CompensationInput(
                identity("rollback_hvac"),
                "set_hvac_power",
                SHA_A,
                SHA_B,
                true);

        NodeExecutionResult<?> effectResult = execute(effect);
        NodeExecutionResult<?> compensationResult = execute(compensation);
        assertEquals(Status.WAITING, effectResult.getStatus());
        assertEquals(ReasonCode.EFFECT_DISPATCH_DISABLED, effectResult.getReasonCode());
        assertEquals(Status.REJECTED, compensationResult.getStatus());
        assertEquals(ReasonCode.COMPENSATION_DISABLED, compensationResult.getReasonCode());
        assertFalse(effectResult.getOutput().isProductionTrusted());
        assertFalse(compensationResult.getOutput().isProductionTrusted());
    }

    @Test
    public void verificationDistinguishesUnavailableMismatchAndMatch() {
        VerificationInput unavailable = verification(SHA_A, SHA_A, false, false);
        VerificationInput mismatch = verification(SHA_A, SHA_B, true, false);
        VerificationInput match = verification(SHA_A, SHA_A, true, true);

        assertEquals(Status.WAITING, execute(unavailable).getStatus());
        assertEquals(ReasonCode.VERIFICATION_UNAVAILABLE, execute(unavailable).getReasonCode());
        assertEquals(Status.FAILED, execute(mismatch).getStatus());
        assertEquals(ReasonCode.VERIFICATION_MISMATCH, execute(mismatch).getReasonCode());
        assertEquals(Status.SUCCEEDED, execute(match).getStatus());
        assertEquals(ReasonCode.VERIFICATION_MATCHED, execute(match).getReasonCode());
        assertFalse(execute(match).getOutput().isProductionTrusted());
    }

    @Test
    public void summaryIsBoundedDeterministicAndContainsNoFreeFormModelText() {
        SummaryInput first = new SummaryInput(
                identity("render_summary"), "scenario.fatigue.partial", 4, 1, 2, SHA_C);
        SummaryInput second = new SummaryInput(
                identity("render_summary"), "scenario.fatigue.partial", 4, 1, 2, SHA_C);

        NodeExecutionResult<?> firstResult = execute(first);
        NodeExecutionResult<?> secondResult = execute(second);
        assertEquals(Status.SUCCEEDED, firstResult.getStatus());
        assertEquals(ReasonCode.SUMMARY_RENDERED, firstResult.getReasonCode());
        assertEquals(first.getContractDigest(), second.getContractDigest());
        assertEquals(firstResult.getResultDigest(), secondResult.getResultDigest());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SummaryInput(
                        identity("render_summary"),
                        "scenario.fatigue.partial",
                        64,
                        1,
                        0,
                        SHA_C));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SummaryInput(
                        identity("render_summary"),
                        "This is unrestricted model text",
                        1,
                        0,
                        0,
                        SHA_C));
    }

    @Test
    public void modelToolAndMemorySchemasHaveNoDebugExecutorOrFallback() {
        for (String nodeType : new String[] {
                "model.invoke", "tool.invoke", "memory.query", "memory.write"}) {
            DigestOnlyInput input = new DigestOnlyInput(
                    identity("digest_only"), nodeType, SHA_A);
            NodeExecutionSchemas.validateInput(nodeType, input);
            assertFalse(DeterministicNodeExecutors.supports(nodeType));
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(input));
            assertTrue(exception.getMessage().startsWith("CB_NODE_EXECUTOR:"));
        }
    }

    private static ContextInput contextInput(
            String expectedDigest,
            String capturedDigest,
            boolean productionTrusted) {
        return new ContextInput(
                identity("capture_context"),
                expectedDigest,
                capturedDigest,
                productionTrusted);
    }

    private static VerificationInput verification(
            String expectedDigest,
            String observedDigest,
            boolean available,
            boolean productionTrusted) {
        return new VerificationInput(
                identity("verify_hvac"),
                "vehicle.hvac.power",
                expectedDigest,
                observedDigest,
                available,
                productionTrusted);
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

    private static NodeExecutionResult<?> execute(NodeExecutionInput input) {
        return DeterministicNodeExecutors.execute(
                NodeExecutorRegistry.controlOnlyContractRegistry(), input);
    }
}
