package com.centralbrain.runtime.graph;

import com.centralbrain.runtime.graph.NodeExecutionInput.ApprovalDecision;
import com.centralbrain.runtime.graph.NodeExecutionInput.ApprovalInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.CompensationInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.ContextInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.EffectInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.PolicyInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.SummaryInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.VerificationInput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.ApprovalDisposition;
import com.centralbrain.runtime.graph.NodeExecutionOutput.ApprovalOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.CompensationDisposition;
import com.centralbrain.runtime.graph.NodeExecutionOutput.CompensationOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.ContextOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.EffectDisposition;
import com.centralbrain.runtime.graph.NodeExecutionOutput.EffectOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.PolicyDecision;
import com.centralbrain.runtime.graph.NodeExecutionOutput.PolicyOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.SummaryOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.VerificationDisposition;
import com.centralbrain.runtime.graph.NodeExecutionOutput.VerificationOutput;
import com.centralbrain.runtime.graph.NodeExecutionResult.ReasonCode;
import com.centralbrain.runtime.graph.NodeExecutionResult.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug/test-only deterministic executors. The explicit switch is the only harness dispatch and
 * never reaches AgentGraphRuntime, a Service, a model, an Effect adapter, network, or hardware.
 */
public final class DeterministicNodeExecutors {
    private static final Map<String, TypedNodeExecutor<?, ?>> EXECUTORS;

    static {
        Map<String, TypedNodeExecutor<?, ?>> executors = new LinkedHashMap<>();
        register(executors, new ApprovalExecutor());
        register(executors, new CompensationExecutor());
        register(executors, new ContextExecutor());
        register(executors, new EffectExecutor());
        register(executors, new VerificationExecutor());
        register(executors, new PolicyExecutor());
        register(executors, new SummaryExecutor());
        EXECUTORS = Collections.unmodifiableMap(executors);
    }

    private DeterministicNodeExecutors() {}

    public static List<TypedNodeExecutor<?, ?>> all() {
        return Collections.unmodifiableList(new ArrayList<>(EXECUTORS.values()));
    }

    public static boolean supports(String nodeType) {
        return EXECUTORS.containsKey(nodeType);
    }

    public static NodeExecutionResult<?> execute(
            NodeExecutorRegistry registry,
            NodeExecutionInput input) {
        if (registry == null || input == null) {
            throw violation("registry and input are required");
        }
        TypedNodeExecutor<?, ?> executor = EXECUTORS.get(input.getNodeType());
        if (executor == null) {
            throw violation("executor unavailable for node type");
        }
        registry.validateExecutor(executor);
        registry.validateInput(input.getNodeType(), input);
        NodeExecutionResult<?> result;
        switch (input.getNodeType()) {
            case "context.capture":
                result = new ContextExecutor().execute((ContextInput) input);
                break;
            case "policy.evaluate":
                result = new PolicyExecutor().execute((PolicyInput) input);
                break;
            case "approval.interrupt":
                result = new ApprovalExecutor().execute((ApprovalInput) input);
                break;
            case "effect.execute":
                result = new EffectExecutor().execute((EffectInput) input);
                break;
            case "effect.verify":
                result = new VerificationExecutor().execute((VerificationInput) input);
                break;
            case "summary.render":
                result = new SummaryExecutor().execute((SummaryInput) input);
                break;
            case "compensate":
                result = new CompensationExecutor().execute((CompensationInput) input);
                break;
            default:
                throw violation("executor unavailable for node type");
        }
        registry.validateResult(input.getNodeType(), result);
        return result;
    }

    private static final class ContextExecutor
            implements TypedNodeExecutor<ContextInput, ContextOutput> {
        @Override
        public String nodeType() {
            return "context.capture";
        }

        @Override
        public Class<ContextInput> inputType() {
            return ContextInput.class;
        }

        @Override
        public Class<ContextOutput> outputType() {
            return ContextOutput.class;
        }

        @Override
        public NodeExecutionResult<ContextOutput> execute(ContextInput input) {
            ContextOutput output = new ContextOutput(
                    input.getCapturedContextDigest(), false);
            if (!input.getExpectedContextDigest().equals(input.getCapturedContextDigest())) {
                return NodeExecutionResult.of(
                        Status.FAILED, ReasonCode.CONTEXT_DIGEST_MISMATCH, output);
            }
            return NodeExecutionResult.of(Status.SUCCEEDED, ReasonCode.CONTEXT_CAPTURED, output);
        }
    }

    private static final class PolicyExecutor
            implements TypedNodeExecutor<PolicyInput, PolicyOutput> {
        @Override
        public String nodeType() {
            return "policy.evaluate";
        }

        @Override
        public Class<PolicyInput> inputType() {
            return PolicyInput.class;
        }

        @Override
        public Class<PolicyOutput> outputType() {
            return PolicyOutput.class;
        }

        @Override
        public NodeExecutionResult<PolicyOutput> execute(PolicyInput input) {
            String evidence = NodeExecutionContract.digest(
                    "node.output.policy.evidence.v1",
                    input.getPolicyId(),
                    Integer.toString(input.getPolicyVersion()),
                    Integer.toString(input.getRiskClass()),
                    Boolean.toString(input.isSafetyAuthorityAllowed()),
                    Boolean.toString(input.isAuthorityTrusted()),
                    input.getContractDigest());
            if (!input.isAuthorityTrusted()) {
                return NodeExecutionResult.of(
                        Status.REJECTED,
                        ReasonCode.POLICY_AUTHORITY_UNTRUSTED,
                        new PolicyOutput(PolicyDecision.DENIED, evidence, false));
            }
            if (!input.isSafetyAuthorityAllowed()) {
                return NodeExecutionResult.of(
                        Status.REJECTED,
                        ReasonCode.POLICY_DENIED,
                        new PolicyOutput(PolicyDecision.DENIED, evidence, false));
            }
            return NodeExecutionResult.of(
                    Status.SUCCEEDED,
                    ReasonCode.POLICY_ALLOWED,
                    new PolicyOutput(PolicyDecision.ALLOWED, evidence, false));
        }
    }

    private static final class ApprovalExecutor
            implements TypedNodeExecutor<ApprovalInput, ApprovalOutput> {
        @Override
        public String nodeType() {
            return "approval.interrupt";
        }

        @Override
        public Class<ApprovalInput> inputType() {
            return ApprovalInput.class;
        }

        @Override
        public Class<ApprovalOutput> outputType() {
            return ApprovalOutput.class;
        }

        @Override
        public NodeExecutionResult<ApprovalOutput> execute(ApprovalInput input) {
            if (!input.isAuthorityTrusted() && input.getDecision() != ApprovalDecision.PENDING) {
                return approval(
                        input,
                        Status.REJECTED,
                        ReasonCode.APPROVAL_AUTHORITY_UNTRUSTED,
                        ApprovalDisposition.UNTRUSTED);
            }
            switch (input.getDecision()) {
                case PENDING:
                    return approval(
                            input,
                            Status.WAITING,
                            ReasonCode.APPROVAL_PENDING,
                            ApprovalDisposition.WAITING);
                case APPROVED:
                    return approval(
                            input,
                            Status.SUCCEEDED,
                            ReasonCode.APPROVAL_APPROVED,
                            ApprovalDisposition.APPROVED);
                case REJECTED:
                    return approval(
                            input,
                            Status.REJECTED,
                            ReasonCode.APPROVAL_REJECTED,
                            ApprovalDisposition.REJECTED);
                case EXPIRED:
                    return approval(
                            input,
                            Status.REJECTED,
                            ReasonCode.APPROVAL_EXPIRED,
                            ApprovalDisposition.EXPIRED);
                default:
                    throw violation("unknown approval decision");
            }
        }

        private static NodeExecutionResult<ApprovalOutput> approval(
                ApprovalInput input,
                Status status,
                ReasonCode reason,
                ApprovalDisposition disposition) {
            return NodeExecutionResult.of(
                    status,
                    reason,
                    new ApprovalOutput(disposition, input.getApprovalRequestDigest(), false));
        }
    }

    private static final class EffectExecutor
            implements TypedNodeExecutor<EffectInput, EffectOutput> {
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
            return NodeExecutionResult.of(
                    Status.WAITING,
                    ReasonCode.EFFECT_DISPATCH_DISABLED,
                    new EffectOutput(EffectDisposition.NOT_DISPATCHED, input.getTargetDigest()));
        }
    }

    private static final class VerificationExecutor
            implements TypedNodeExecutor<VerificationInput, VerificationOutput> {
        @Override
        public String nodeType() {
            return "effect.verify";
        }

        @Override
        public Class<VerificationInput> inputType() {
            return VerificationInput.class;
        }

        @Override
        public Class<VerificationOutput> outputType() {
            return VerificationOutput.class;
        }

        @Override
        public NodeExecutionResult<VerificationOutput> execute(VerificationInput input) {
            if (!input.isObservationAvailable()) {
                return NodeExecutionResult.of(
                        Status.WAITING,
                        ReasonCode.VERIFICATION_UNAVAILABLE,
                        new VerificationOutput(
                                VerificationDisposition.UNAVAILABLE,
                                input.getObservedDigest(),
                                false));
            }
            if (!input.getExpectedDigest().equals(input.getObservedDigest())) {
                return NodeExecutionResult.of(
                        Status.FAILED,
                        ReasonCode.VERIFICATION_MISMATCH,
                        new VerificationOutput(
                                VerificationDisposition.MISMATCH,
                                input.getObservedDigest(),
                                false));
            }
            return NodeExecutionResult.of(
                    Status.SUCCEEDED,
                    ReasonCode.VERIFICATION_MATCHED,
                    new VerificationOutput(
                            VerificationDisposition.MATCHED,
                            input.getObservedDigest(),
                            false));
        }
    }

    private static final class SummaryExecutor
            implements TypedNodeExecutor<SummaryInput, SummaryOutput> {
        @Override
        public String nodeType() {
            return "summary.render";
        }

        @Override
        public Class<SummaryInput> inputType() {
            return SummaryInput.class;
        }

        @Override
        public Class<SummaryOutput> outputType() {
            return SummaryOutput.class;
        }

        @Override
        public NodeExecutionResult<SummaryOutput> execute(SummaryInput input) {
            return NodeExecutionResult.of(
                    Status.SUCCEEDED,
                    ReasonCode.SUMMARY_RENDERED,
                    new SummaryOutput(
                            input.getMessageKey(),
                            input.getSucceededCount(),
                            input.getFailedCount(),
                            input.getSkippedCount(),
                            input.getTerminalProjectionDigest()));
        }
    }

    private static final class CompensationExecutor
            implements TypedNodeExecutor<CompensationInput, CompensationOutput> {
        @Override
        public String nodeType() {
            return "compensate";
        }

        @Override
        public Class<CompensationInput> inputType() {
            return CompensationInput.class;
        }

        @Override
        public Class<CompensationOutput> outputType() {
            return CompensationOutput.class;
        }

        @Override
        public NodeExecutionResult<CompensationOutput> execute(CompensationInput input) {
            return NodeExecutionResult.of(
                    Status.REJECTED,
                    ReasonCode.COMPENSATION_DISABLED,
                    new CompensationOutput(
                            CompensationDisposition.NOT_DISPATCHED,
                            input.getCompensationMaterialDigest()));
        }
    }

    private static void register(
            Map<String, TypedNodeExecutor<?, ?>> executors,
            TypedNodeExecutor<?, ?> executor) {
        if (executors.put(executor.nodeType(), executor) != null) {
            throw new IllegalStateException("duplicate deterministic executor");
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_NODE_EXECUTOR: " + message);
    }
}
