package com.centralbrain.runtime.graph;

import com.centralbrain.sdk.plan.PlanContract;

import java.util.Objects;
import java.util.Set;

/** Fixed, immutable inputs accepted by P3-W02 typed node executors. */
public abstract class NodeExecutionInput {
    private static final Set<String> DIGEST_ONLY_TYPES = Set.of(
            "model.invoke", "tool.invoke", "memory.query", "memory.write");

    public enum ApprovalDecision {
        PENDING,
        APPROVED,
        REJECTED,
        EXPIRED
    }

    public static final class Identity {
        private final String executionId;
        private final String planId;
        private final String sessionId;
        private final String nodeId;
        private final String planDigest;
        private final String inputDigest;
        private final int attempt;
        private final long deadlineEpochMs;

        public Identity(
                String executionId,
                String planId,
                String sessionId,
                String nodeId,
                String planDigest,
                String inputDigest,
                int attempt,
                long deadlineEpochMs) {
            this.executionId = NodeExecutionContract.canonicalUuid(executionId, "executionId");
            this.planId = NodeExecutionContract.canonicalUuid(planId, "planId");
            this.sessionId = NodeExecutionContract.canonicalUuid(sessionId, "sessionId");
            this.nodeId = NodeExecutionContract.requireNodeId(nodeId, "nodeId");
            this.planDigest = NodeExecutionContract.requireDigest(planDigest, "planDigest");
            this.inputDigest = NodeExecutionContract.requireDigest(inputDigest, "inputDigest");
            if (attempt < 1 || attempt > PlanContract.MAX_ATTEMPTS) {
                throw NodeExecutionContract.violation("attempt is outside the Plan contract");
            }
            if (deadlineEpochMs <= 0) {
                throw NodeExecutionContract.violation("deadlineEpochMs must be positive");
            }
            this.attempt = attempt;
            this.deadlineEpochMs = deadlineEpochMs;
        }

        public String getExecutionId() {
            return executionId;
        }

        public String getPlanId() {
            return planId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getPlanDigest() {
            return planDigest;
        }

        public String getInputDigest() {
            return inputDigest;
        }

        public int getAttempt() {
            return attempt;
        }

        public long getDeadlineEpochMs() {
            return deadlineEpochMs;
        }

        private String digest(String nodeType) {
            return NodeExecutionContract.digest(
                    "node.input.identity.v1",
                    executionId,
                    planId,
                    sessionId,
                    nodeId,
                    nodeType,
                    planDigest,
                    inputDigest,
                    Integer.toString(attempt),
                    Long.toString(deadlineEpochMs));
        }
    }

    private final Identity identity;
    private final String nodeType;
    private final String schemaId;
    private final String contractDigest;

    NodeExecutionInput(Identity identity, String nodeType, String schemaId, String... fields) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.nodeType = NodeExecutionContract.requireNodeType(nodeType);
        this.schemaId = NodeExecutionContract.requireSchemaId(schemaId, "input schemaId");
        String[] bound = new String[fields.length + 1];
        bound[0] = identity.digest(nodeType);
        System.arraycopy(fields, 0, bound, 1, fields.length);
        this.contractDigest = NodeExecutionContract.digest(schemaId, bound);
    }

    public final int getSchemaVersion() {
        return NodeExecutionContract.SCHEMA_VERSION;
    }

    public final Identity getIdentity() {
        return identity;
    }

    public final String getNodeType() {
        return nodeType;
    }

    public final String getSchemaId() {
        return schemaId;
    }

    public final String getContractDigest() {
        return contractDigest;
    }

    public static final class ContextInput extends NodeExecutionInput {
        private final String expectedContextDigest;
        private final String capturedContextDigest;
        private final boolean productionTrusted;

        public ContextInput(
                Identity identity,
                String expectedContextDigest,
                String capturedContextDigest,
                boolean productionTrusted) {
            super(
                    identity,
                    "context.capture",
                    "node.input.context.v1",
                    NodeExecutionContract.requireDigest(
                            expectedContextDigest, "expectedContextDigest"),
                    NodeExecutionContract.requireDigest(
                            capturedContextDigest, "capturedContextDigest"),
                    Boolean.toString(productionTrusted));
            this.expectedContextDigest = expectedContextDigest;
            this.capturedContextDigest = capturedContextDigest;
            this.productionTrusted = productionTrusted;
        }

        public String getExpectedContextDigest() {
            return expectedContextDigest;
        }

        public String getCapturedContextDigest() {
            return capturedContextDigest;
        }

        public boolean isProductionTrusted() {
            return productionTrusted;
        }
    }

    public static final class PolicyInput extends NodeExecutionInput {
        private final String policyId;
        private final int policyVersion;
        private final int riskClass;
        private final boolean safetyAuthorityAllowed;
        private final boolean authorityTrusted;

        public PolicyInput(
                Identity identity,
                String policyId,
                int policyVersion,
                int riskClass,
                boolean safetyAuthorityAllowed,
                boolean authorityTrusted) {
            super(
                    identity,
                    "policy.evaluate",
                    "node.input.policy.v1",
                    validatePolicyId(policyId),
                    Integer.toString(validatePositive(policyVersion, "policyVersion")),
                    Integer.toString(validateRisk(riskClass)),
                    Boolean.toString(safetyAuthorityAllowed),
                    Boolean.toString(authorityTrusted));
            this.policyId = policyId;
            this.policyVersion = policyVersion;
            this.riskClass = riskClass;
            this.safetyAuthorityAllowed = safetyAuthorityAllowed;
            this.authorityTrusted = authorityTrusted;
        }

        public String getPolicyId() {
            return policyId;
        }

        public int getPolicyVersion() {
            return policyVersion;
        }

        public int getRiskClass() {
            return riskClass;
        }

        public boolean isSafetyAuthorityAllowed() {
            return safetyAuthorityAllowed;
        }

        public boolean isAuthorityTrusted() {
            return authorityTrusted;
        }
    }

    public static final class ApprovalInput extends NodeExecutionInput {
        private final String approvalRequestDigest;
        private final ApprovalDecision decision;
        private final boolean authorityTrusted;

        public ApprovalInput(
                Identity identity,
                String approvalRequestDigest,
                ApprovalDecision decision,
                boolean authorityTrusted) {
            super(
                    identity,
                    "approval.interrupt",
                    "node.input.approval.v1",
                    NodeExecutionContract.requireDigest(
                            approvalRequestDigest, "approvalRequestDigest"),
                    Objects.requireNonNull(decision, "decision").name(),
                    Boolean.toString(authorityTrusted));
            this.approvalRequestDigest = approvalRequestDigest;
            this.decision = decision;
            this.authorityTrusted = authorityTrusted;
        }

        public String getApprovalRequestDigest() {
            return approvalRequestDigest;
        }

        public ApprovalDecision getDecision() {
            return decision;
        }

        public boolean isAuthorityTrusted() {
            return authorityTrusted;
        }
    }

    public static final class EffectInput extends NodeExecutionInput {
        private final String capabilityId;
        private final String resourceKey;
        private final String targetDigest;
        private final boolean durableMaterialAvailable;

        public EffectInput(
                Identity identity,
                String capabilityId,
                String resourceKey,
                String targetDigest,
                boolean durableMaterialAvailable) {
            super(
                    identity,
                    "effect.execute",
                    "node.input.effect.v1",
                    validateCapability(capabilityId),
                    NodeExecutionContract.requireResourceKey(resourceKey, "resourceKey"),
                    NodeExecutionContract.requireDigest(targetDigest, "targetDigest"),
                    Boolean.toString(durableMaterialAvailable));
            this.capabilityId = capabilityId;
            this.resourceKey = resourceKey;
            this.targetDigest = targetDigest;
            this.durableMaterialAvailable = durableMaterialAvailable;
        }

        public String getCapabilityId() {
            return capabilityId;
        }

        public String getResourceKey() {
            return resourceKey;
        }

        public String getTargetDigest() {
            return targetDigest;
        }

        public boolean isDurableMaterialAvailable() {
            return durableMaterialAvailable;
        }
    }

    public static final class VerificationInput extends NodeExecutionInput {
        private final String capabilityId;
        private final String expectedDigest;
        private final String observedDigest;
        private final boolean observationAvailable;
        private final boolean productionTrusted;

        public VerificationInput(
                Identity identity,
                String capabilityId,
                String expectedDigest,
                String observedDigest,
                boolean observationAvailable,
                boolean productionTrusted) {
            super(
                    identity,
                    "effect.verify",
                    "node.input.verification.v1",
                    validateCapability(capabilityId),
                    NodeExecutionContract.requireDigest(expectedDigest, "expectedDigest"),
                    NodeExecutionContract.requireDigest(observedDigest, "observedDigest"),
                    Boolean.toString(observationAvailable),
                    Boolean.toString(productionTrusted));
            this.capabilityId = capabilityId;
            this.expectedDigest = expectedDigest;
            this.observedDigest = observedDigest;
            this.observationAvailable = observationAvailable;
            this.productionTrusted = productionTrusted;
        }

        public String getCapabilityId() {
            return capabilityId;
        }

        public String getExpectedDigest() {
            return expectedDigest;
        }

        public String getObservedDigest() {
            return observedDigest;
        }

        public boolean isObservationAvailable() {
            return observationAvailable;
        }

        public boolean isProductionTrusted() {
            return productionTrusted;
        }
    }

    public static final class SummaryInput extends NodeExecutionInput {
        private final String messageKey;
        private final int succeededCount;
        private final int failedCount;
        private final int skippedCount;
        private final String terminalProjectionDigest;

        public SummaryInput(
                Identity identity,
                String messageKey,
                int succeededCount,
                int failedCount,
                int skippedCount,
                String terminalProjectionDigest) {
            super(
                    identity,
                    "summary.render",
                    "node.input.summary.v1",
                    NodeExecutionContract.requireMessageKey(messageKey, "messageKey"),
                    Integer.toString(validateCount(succeededCount, "succeededCount")),
                    Integer.toString(validateCount(failedCount, "failedCount")),
                    Integer.toString(validateCount(skippedCount, "skippedCount")),
                    validateTotal(succeededCount, failedCount, skippedCount),
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

    public static final class CompensationInput extends NodeExecutionInput {
        private final String originalNodeId;
        private final String originalEffectDigest;
        private final String compensationMaterialDigest;
        private final boolean durableMaterialAvailable;

        public CompensationInput(
                Identity identity,
                String originalNodeId,
                String originalEffectDigest,
                String compensationMaterialDigest,
                boolean durableMaterialAvailable) {
            super(
                    identity,
                    "compensate",
                    "node.input.compensation.v1",
                    NodeExecutionContract.requireNodeId(originalNodeId, "originalNodeId"),
                    NodeExecutionContract.requireDigest(
                            originalEffectDigest, "originalEffectDigest"),
                    NodeExecutionContract.requireDigest(
                            compensationMaterialDigest, "compensationMaterialDigest"),
                    Boolean.toString(durableMaterialAvailable));
            this.originalNodeId = originalNodeId;
            this.originalEffectDigest = originalEffectDigest;
            this.compensationMaterialDigest = compensationMaterialDigest;
            this.durableMaterialAvailable = durableMaterialAvailable;
        }

        public String getOriginalNodeId() {
            return originalNodeId;
        }

        public String getOriginalEffectDigest() {
            return originalEffectDigest;
        }

        public String getCompensationMaterialDigest() {
            return compensationMaterialDigest;
        }

        public boolean isDurableMaterialAvailable() {
            return durableMaterialAvailable;
        }
    }

    /** Digest-only fixed schema for node types whose executor is deliberately unavailable in P3-W02. */
    public static final class DigestOnlyInput extends NodeExecutionInput {
        private final String operationDigest;

        public DigestOnlyInput(Identity identity, String nodeType, String operationDigest) {
            super(
                    identity,
                    validateDigestOnlyType(nodeType),
                    "node.input.digest-only.v1",
                    NodeExecutionContract.requireDigest(operationDigest, "operationDigest"));
            this.operationDigest = operationDigest;
        }

        public String getOperationDigest() {
            return operationDigest;
        }
    }

    private static String validatePolicyId(String value) {
        return NodeExecutionContract.requireQualifiedId(
                value, PlanContract.MAX_POLICY_ID_CHARS, "policyId");
    }

    private static String validateCapability(String value) {
        return NodeExecutionContract.requireQualifiedId(
                value, PlanContract.MAX_CAPABILITY_ID_CHARS, "capabilityId");
    }

    private static int validatePositive(int value, String label) {
        if (value < 1) {
            throw NodeExecutionContract.violation(label + " must be positive");
        }
        return value;
    }

    private static int validateRisk(int value) {
        if (value < PlanContract.RISK_LOW || value > PlanContract.RISK_CRITICAL) {
            throw NodeExecutionContract.violation("riskClass is unknown");
        }
        return value;
    }

    private static int validateCount(int value, String label) {
        if (value < 0 || value > PlanContract.MAX_NODES) {
            throw NodeExecutionContract.violation(label + " is outside the Plan bound");
        }
        return value;
    }

    private static String validateTotal(int succeeded, int failed, int skipped) {
        if (succeeded + failed + skipped > PlanContract.MAX_NODES) {
            throw NodeExecutionContract.violation("summary counts exceed the Plan bound");
        }
        return Integer.toString(succeeded + failed + skipped);
    }

    private static String validateDigestOnlyType(String nodeType) {
        NodeExecutionContract.requireNodeType(nodeType);
        if (!DIGEST_ONLY_TYPES.contains(nodeType)) {
            throw NodeExecutionContract.violation("nodeType does not use digest-only input");
        }
        return nodeType;
    }
}
