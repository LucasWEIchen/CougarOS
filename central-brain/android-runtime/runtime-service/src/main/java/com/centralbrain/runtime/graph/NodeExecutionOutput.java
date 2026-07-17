package com.centralbrain.runtime.graph;

/** Fixed, immutable outputs returned by P3-W02 typed node executors. */
public abstract class NodeExecutionOutput {
    public enum PolicyDecision {
        ALLOWED,
        DENIED
    }

    public enum ApprovalDisposition {
        WAITING,
        APPROVED,
        REJECTED,
        EXPIRED,
        UNTRUSTED
    }

    public enum EffectDisposition {
        NOT_DISPATCHED
    }

    public enum VerificationDisposition {
        MATCHED,
        MISMATCH,
        UNAVAILABLE
    }

    public enum CompensationDisposition {
        NOT_DISPATCHED
    }

    private final String nodeType;
    private final String schemaId;
    private final boolean productionTrusted;
    private final String outputDigest;

    NodeExecutionOutput(
            String nodeType,
            String schemaId,
            boolean productionTrusted,
            String... fields) {
        this.nodeType = NodeExecutionContract.requireNodeType(nodeType);
        this.schemaId = NodeExecutionContract.requireSchemaId(schemaId, "output schemaId");
        String[] bound = new String[fields.length + 2];
        bound[0] = nodeType;
        bound[1] = Boolean.toString(productionTrusted);
        System.arraycopy(fields, 0, bound, 2, fields.length);
        this.productionTrusted = productionTrusted;
        this.outputDigest = NodeExecutionContract.digest(schemaId, bound);
    }

    public final int getSchemaVersion() {
        return NodeExecutionContract.SCHEMA_VERSION;
    }

    public final String getNodeType() {
        return nodeType;
    }

    public final String getSchemaId() {
        return schemaId;
    }

    public final boolean isProductionTrusted() {
        return productionTrusted;
    }

    public final String getOutputDigest() {
        return outputDigest;
    }

    public static final class ContextOutput extends NodeExecutionOutput {
        private final String snapshotDigest;

        public ContextOutput(String snapshotDigest, boolean productionTrusted) {
            super(
                    "context.capture",
                    "node.output.context.v1",
                    productionTrusted,
                    NodeExecutionContract.requireDigest(snapshotDigest, "snapshotDigest"));
            this.snapshotDigest = snapshotDigest;
        }

        public String getSnapshotDigest() {
            return snapshotDigest;
        }
    }

    public static final class PolicyOutput extends NodeExecutionOutput {
        private final PolicyDecision decision;
        private final String policyEvidenceDigest;

        public PolicyOutput(
                PolicyDecision decision,
                String policyEvidenceDigest,
                boolean productionTrusted) {
            super(
                    "policy.evaluate",
                    "node.output.policy.v1",
                    productionTrusted,
                    require(decision, "decision").name(),
                    NodeExecutionContract.requireDigest(
                            policyEvidenceDigest, "policyEvidenceDigest"));
            this.decision = decision;
            this.policyEvidenceDigest = policyEvidenceDigest;
        }

        public PolicyDecision getDecision() {
            return decision;
        }

        public String getPolicyEvidenceDigest() {
            return policyEvidenceDigest;
        }
    }

    public static final class ApprovalOutput extends NodeExecutionOutput {
        private final ApprovalDisposition disposition;
        private final String approvalEvidenceDigest;

        public ApprovalOutput(
                ApprovalDisposition disposition,
                String approvalEvidenceDigest,
                boolean productionTrusted) {
            super(
                    "approval.interrupt",
                    "node.output.approval.v1",
                    productionTrusted,
                    require(disposition, "disposition").name(),
                    NodeExecutionContract.requireDigest(
                            approvalEvidenceDigest, "approvalEvidenceDigest"));
            this.disposition = disposition;
            this.approvalEvidenceDigest = approvalEvidenceDigest;
        }

        public ApprovalDisposition getDisposition() {
            return disposition;
        }

        public String getApprovalEvidenceDigest() {
            return approvalEvidenceDigest;
        }
    }

    public static final class EffectOutput extends NodeExecutionOutput {
        private final EffectDisposition disposition;
        private final String targetDigest;

        public EffectOutput(EffectDisposition disposition, String targetDigest) {
            super(
                    "effect.execute",
                    "node.output.effect.v1",
                    false,
                    require(disposition, "disposition").name(),
                    NodeExecutionContract.requireDigest(targetDigest, "targetDigest"));
            this.disposition = disposition;
            this.targetDigest = targetDigest;
        }

        public EffectDisposition getDisposition() {
            return disposition;
        }

        public String getTargetDigest() {
            return targetDigest;
        }
    }

    public static final class VerificationOutput extends NodeExecutionOutput {
        private final VerificationDisposition disposition;
        private final String observationDigest;

        public VerificationOutput(
                VerificationDisposition disposition,
                String observationDigest,
                boolean productionTrusted) {
            super(
                    "effect.verify",
                    "node.output.verification.v1",
                    productionTrusted,
                    require(disposition, "disposition").name(),
                    NodeExecutionContract.requireDigest(
                            observationDigest, "observationDigest"));
            this.disposition = disposition;
            this.observationDigest = observationDigest;
        }

        public VerificationDisposition getDisposition() {
            return disposition;
        }

        public String getObservationDigest() {
            return observationDigest;
        }
    }

    public static final class SummaryOutput extends NodeExecutionOutput {
        private final String messageKey;
        private final int succeededCount;
        private final int failedCount;
        private final int skippedCount;
        private final String terminalProjectionDigest;

        public SummaryOutput(
                String messageKey,
                int succeededCount,
                int failedCount,
                int skippedCount,
                String terminalProjectionDigest) {
            super(
                    "summary.render",
                    "node.output.summary.v1",
                    false,
                    NodeExecutionContract.requireMessageKey(messageKey, "messageKey"),
                    Integer.toString(validateSummaryCount(succeededCount, "succeededCount")),
                    Integer.toString(validateSummaryCount(failedCount, "failedCount")),
                    Integer.toString(validateSummaryCount(skippedCount, "skippedCount")),
                    validateSummaryTotal(succeededCount, failedCount, skippedCount),
                    NodeExecutionContract.requireDigest(
                            terminalProjectionDigest, "terminalProjectionDigest"));
            this.messageKey = messageKey;
            this.succeededCount = succeededCount;
            this.failedCount = failedCount;
            this.skippedCount = skippedCount;
            this.terminalProjectionDigest = terminalProjectionDigest;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public int getSucceededCount() {
            return succeededCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public int getSkippedCount() {
            return skippedCount;
        }

        public String getTerminalProjectionDigest() {
            return terminalProjectionDigest;
        }
    }

    public static final class CompensationOutput extends NodeExecutionOutput {
        private final CompensationDisposition disposition;
        private final String materialDigest;

        public CompensationOutput(
                CompensationDisposition disposition,
                String materialDigest) {
            super(
                    "compensate",
                    "node.output.compensation.v1",
                    false,
                    require(disposition, "disposition").name(),
                    NodeExecutionContract.requireDigest(materialDigest, "materialDigest"));
            this.disposition = disposition;
            this.materialDigest = materialDigest;
        }

        public CompensationDisposition getDisposition() {
            return disposition;
        }

        public String getMaterialDigest() {
            return materialDigest;
        }
    }

    /** Fixed output schema for node types deliberately left without an executor in P3-W02. */
    public static final class DigestOnlyOutput extends NodeExecutionOutput {
        private final String projectionDigest;

        public DigestOnlyOutput(String nodeType, String projectionDigest) {
            super(
                    nodeType,
                    "node.output.digest-only.v1",
                    false,
                    NodeExecutionContract.requireDigest(
                            projectionDigest, "projectionDigest"));
            this.projectionDigest = projectionDigest;
        }

        public String getProjectionDigest() {
            return projectionDigest;
        }
    }

    private static <T> T require(T value, String label) {
        if (value == null) {
            throw NodeExecutionContract.violation(label + " is required");
        }
        return value;
    }

    private static int validateSummaryCount(int value, String label) {
        if (value < 0 || value > com.centralbrain.sdk.plan.PlanContract.MAX_NODES) {
            throw NodeExecutionContract.violation(label + " is outside the Plan bound");
        }
        return value;
    }

    private static String validateSummaryTotal(int succeeded, int failed, int skipped) {
        if (succeeded + failed + skipped > com.centralbrain.sdk.plan.PlanContract.MAX_NODES) {
            throw NodeExecutionContract.violation("summary counts exceed the Plan bound");
        }
        return Integer.toString(succeeded + failed + skipped);
    }
}
