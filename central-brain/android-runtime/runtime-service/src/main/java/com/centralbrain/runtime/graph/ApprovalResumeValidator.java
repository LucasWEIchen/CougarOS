package com.centralbrain.runtime.graph;

import java.util.Objects;

/** Revalidates approval bindings and current Safety State before Graph resume. */
public final class ApprovalResumeValidator {
    public enum SafetyState {
        SAFE,
        UNSAFE,
        UNKNOWN
    }

    public enum Reason {
        VALID,
        NOT_APPROVED,
        EXPIRED,
        AUTHORITY_UNTRUSTED,
        OWNER_MISMATCH,
        BINDING_MISMATCH,
        CONTEXT_STALE,
        CONTEXT_CHANGED,
        POLICY_DENIED,
        POLICY_CHANGED,
        CAPABILITY_DENIED,
        SAFETY_UNTRUSTED,
        SAFETY_UNSAFE,
        SAFETY_CHANGED
    }

    private ApprovalResumeValidator() {}

    public static ResumeResult validate(
            ApprovalInterruptRecord record,
            ResumeContext context) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(context, "context");
        Reason reason = evaluate(record, context);
        return new ResumeResult(
                reason == Reason.VALID,
                reason,
                NodeExecutionContract.digest(
                        "graph.approval.resume.v1",
                        record.getRecordDigest(),
                        reason.name(),
                        context.ownerFingerprint,
                        context.sessionId,
                        context.planId,
                        context.nodeId,
                        context.actionDigest,
                        context.planDigest,
                        context.contextDigest,
                        context.policyDigest,
                        context.safetyStateDigest,
                        Long.toString(context.nowEpochMs)));
    }

    private static Reason evaluate(
            ApprovalInterruptRecord record,
            ResumeContext context) {
        if (record.getDecision() != ApprovalInterruptRecord.Decision.APPROVED) {
            return Reason.NOT_APPROVED;
        }
        if (context.nowEpochMs >= record.getExpiresAtEpochMs()
                || context.nowEpochMs >= record.getPlanDeadlineEpochMs()) {
            return Reason.EXPIRED;
        }
        if (!record.isAuthorityTrusted()) {
            return Reason.AUTHORITY_UNTRUSTED;
        }
        if (!record.getOwnerFingerprint().equals(context.ownerFingerprint)) {
            return Reason.OWNER_MISMATCH;
        }
        if (!record.getSessionId().equals(context.sessionId)
                || !record.getPlanId().equals(context.planId)
                || !record.getNodeId().equals(context.nodeId)
                || !record.getActionDigest().equals(context.actionDigest)
                || !record.getPlanDigest().equals(context.planDigest)) {
            return Reason.BINDING_MISMATCH;
        }
        if (!context.contextFresh) {
            return Reason.CONTEXT_STALE;
        }
        if (!record.getContextDigest().equals(context.contextDigest)) {
            return Reason.CONTEXT_CHANGED;
        }
        if (!context.policyAuthorized) {
            return Reason.POLICY_DENIED;
        }
        if (!record.getPolicyDigest().equals(context.policyDigest)) {
            return Reason.POLICY_CHANGED;
        }
        if (!context.capabilityAllowed) {
            return Reason.CAPABILITY_DENIED;
        }
        if (!context.safetyStateTrusted) {
            return Reason.SAFETY_UNTRUSTED;
        }
        if (context.safetyState != SafetyState.SAFE) {
            return Reason.SAFETY_UNSAFE;
        }
        if (!record.getSafetyStateDigest().equals(context.safetyStateDigest)) {
            return Reason.SAFETY_CHANGED;
        }
        return Reason.VALID;
    }

    public static final class ResumeContext {
        private final String ownerFingerprint;
        private final String sessionId;
        private final String planId;
        private final String nodeId;
        private final String actionDigest;
        private final String planDigest;
        private final String contextDigest;
        private final String policyDigest;
        private final String safetyStateDigest;
        private final long nowEpochMs;
        private final boolean contextFresh;
        private final boolean policyAuthorized;
        private final boolean capabilityAllowed;
        private final boolean safetyStateTrusted;
        private final SafetyState safetyState;

        public ResumeContext(
                String ownerFingerprint,
                String sessionId,
                String planId,
                String nodeId,
                String actionDigest,
                String planDigest,
                String contextDigest,
                String policyDigest,
                String safetyStateDigest,
                long nowEpochMs,
                boolean contextFresh,
                boolean policyAuthorized,
                boolean capabilityAllowed,
                boolean safetyStateTrusted,
                SafetyState safetyState) {
            this.ownerFingerprint = NodeExecutionContract.requireDigest(
                    ownerFingerprint, "ownerFingerprint");
            this.sessionId = NodeExecutionContract.canonicalUuid(sessionId, "sessionId");
            this.planId = NodeExecutionContract.canonicalUuid(planId, "planId");
            this.nodeId = NodeExecutionContract.requireNodeId(nodeId, "nodeId");
            this.actionDigest = NodeExecutionContract.requireDigest(actionDigest, "actionDigest");
            this.planDigest = NodeExecutionContract.requireDigest(planDigest, "planDigest");
            this.contextDigest = NodeExecutionContract.requireDigest(contextDigest, "contextDigest");
            this.policyDigest = NodeExecutionContract.requireDigest(policyDigest, "policyDigest");
            this.safetyStateDigest = NodeExecutionContract.requireDigest(
                    safetyStateDigest, "safetyStateDigest");
            if (nowEpochMs <= 0L) {
                throw violation("resume time must be positive");
            }
            this.nowEpochMs = nowEpochMs;
            this.contextFresh = contextFresh;
            this.policyAuthorized = policyAuthorized;
            this.capabilityAllowed = capabilityAllowed;
            this.safetyStateTrusted = safetyStateTrusted;
            this.safetyState = Objects.requireNonNull(safetyState, "safetyState");
        }
    }

    public static final class ResumeResult {
        private final boolean allowed;
        private final Reason reason;
        private final String resultDigest;

        private ResumeResult(boolean allowed, Reason reason, String resultDigest) {
            this.allowed = allowed;
            this.reason = Objects.requireNonNull(reason, "reason");
            this.resultDigest = NodeExecutionContract.requireDigest(
                    resultDigest, "resultDigest");
        }

        public boolean isAllowed() {
            return allowed;
        }

        public Reason getReason() {
            return reason;
        }

        public String getResultDigest() {
            return resultDigest;
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_APPROVAL_RESUME: " + message);
    }
}
