package com.centralbrain.runtime.graph;

import java.util.Objects;

/** Governed executor result with enum-only reason and digest-only projection. */
public final class NodeExecutionResult<O extends NodeExecutionOutput> {
    public enum Status {
        SUCCEEDED,
        WAITING,
        FAILED,
        REJECTED
    }

    public enum ReasonCode {
        CONTEXT_CAPTURED,
        CONTEXT_DIGEST_MISMATCH,
        POLICY_ALLOWED,
        POLICY_DENIED,
        POLICY_AUTHORITY_UNTRUSTED,
        APPROVAL_PENDING,
        APPROVAL_APPROVED,
        APPROVAL_REJECTED,
        APPROVAL_EXPIRED,
        APPROVAL_AUTHORITY_UNTRUSTED,
        EFFECT_DISPATCH_DISABLED,
        VERIFICATION_MATCHED,
        VERIFICATION_MISMATCH,
        VERIFICATION_UNAVAILABLE,
        SUMMARY_RENDERED,
        COMPENSATION_DISABLED
    }

    private final Status status;
    private final ReasonCode reasonCode;
    private final O output;
    private final String resultDigest;

    private NodeExecutionResult(Status status, ReasonCode reasonCode, O output) {
        this.status = Objects.requireNonNull(status, "status");
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        this.output = Objects.requireNonNull(output, "output");
        this.resultDigest = NodeExecutionContract.digest(
                "node.execution.result.v1",
                output.getNodeType(),
                status.name(),
                reasonCode.name(),
                output.getSchemaId(),
                output.getOutputDigest(),
                Boolean.toString(output.isProductionTrusted()));
    }

    public static <O extends NodeExecutionOutput> NodeExecutionResult<O> of(
            Status status,
            ReasonCode reasonCode,
            O output) {
        return new NodeExecutionResult<>(status, reasonCode, output);
    }

    public Status getStatus() {
        return status;
    }

    public ReasonCode getReasonCode() {
        return reasonCode;
    }

    public O getOutput() {
        return output;
    }

    public String getResultDigest() {
        return resultDigest;
    }
}
