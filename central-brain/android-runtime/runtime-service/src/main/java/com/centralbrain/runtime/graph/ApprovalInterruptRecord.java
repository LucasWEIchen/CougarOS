package com.centralbrain.runtime.graph;

import java.util.Objects;

/** Immutable approval interrupt state whose binding can be checkpointed without raw payloads. */
public final class ApprovalInterruptRecord {
    public enum Decision {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED,
        EXPIRED
    }

    private final String approvalId;
    private final String ownerFingerprint;
    private final String sessionId;
    private final String planId;
    private final String nodeId;
    private final String actionDigest;
    private final String planDigest;
    private final String contextDigest;
    private final String policyDigest;
    private final String safetyStateDigest;
    private final long createdAtEpochMs;
    private final long expiresAtEpochMs;
    private final long planDeadlineEpochMs;
    private final Decision decision;
    private final String authorityDigest;
    private final boolean authorityTrusted;
    private final long decidedAtEpochMs;
    private final String recordDigest;

    private ApprovalInterruptRecord(
            String approvalId,
            String ownerFingerprint,
            String sessionId,
            String planId,
            String nodeId,
            String actionDigest,
            String planDigest,
            String contextDigest,
            String policyDigest,
            String safetyStateDigest,
            long createdAtEpochMs,
            long expiresAtEpochMs,
            long planDeadlineEpochMs,
            Decision decision,
            String authorityDigest,
            boolean authorityTrusted,
            long decidedAtEpochMs) {
        this.approvalId = NodeExecutionContract.canonicalUuid(approvalId, "approvalId");
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
        if (createdAtEpochMs <= 0L
                || expiresAtEpochMs <= createdAtEpochMs
                || planDeadlineEpochMs < expiresAtEpochMs) {
            throw violation("approval time bounds are invalid");
        }
        this.createdAtEpochMs = createdAtEpochMs;
        this.expiresAtEpochMs = expiresAtEpochMs;
        this.planDeadlineEpochMs = planDeadlineEpochMs;
        this.decision = Objects.requireNonNull(decision, "decision");
        if (decision == Decision.PENDING) {
            if (authorityDigest == null
                    || !authorityDigest.isEmpty()
                    || authorityTrusted
                    || decidedAtEpochMs != 0L) {
                throw violation("pending approval cannot carry authority decision material");
            }
            this.authorityDigest = "";
        } else {
            this.authorityDigest = NodeExecutionContract.requireDigest(
                    authorityDigest, "authorityDigest");
            if (!authorityTrusted) {
                throw violation("terminal approval requires trusted authority evidence");
            }
            if (decision == Decision.EXPIRED) {
                if (decidedAtEpochMs < expiresAtEpochMs) {
                    throw violation("expired decision precedes expiry");
                }
            } else if (decidedAtEpochMs < createdAtEpochMs
                    || decidedAtEpochMs >= expiresAtEpochMs) {
                throw violation("approval decision is outside its validity window");
            }
        }
        this.authorityTrusted = authorityTrusted;
        this.decidedAtEpochMs = decidedAtEpochMs;
        this.recordDigest = NodeExecutionContract.digest(
                "graph.approval.record.v1",
                this.approvalId,
                this.ownerFingerprint,
                this.sessionId,
                this.planId,
                this.nodeId,
                this.actionDigest,
                this.planDigest,
                this.contextDigest,
                this.policyDigest,
                this.safetyStateDigest,
                Long.toString(this.createdAtEpochMs),
                Long.toString(this.expiresAtEpochMs),
                Long.toString(this.planDeadlineEpochMs),
                this.decision.name(),
                this.authorityDigest,
                Boolean.toString(this.authorityTrusted),
                Long.toString(this.decidedAtEpochMs));
    }

    static ApprovalInterruptRecord pending(
            String approvalId,
            String ownerFingerprint,
            String sessionId,
            String planId,
            String nodeId,
            String actionDigest,
            String planDigest,
            String contextDigest,
            String policyDigest,
            String safetyStateDigest,
            long createdAtEpochMs,
            long expiresAtEpochMs,
            long planDeadlineEpochMs) {
        return new ApprovalInterruptRecord(
                approvalId,
                ownerFingerprint,
                sessionId,
                planId,
                nodeId,
                actionDigest,
                planDigest,
                contextDigest,
                policyDigest,
                safetyStateDigest,
                createdAtEpochMs,
                expiresAtEpochMs,
                planDeadlineEpochMs,
                Decision.PENDING,
                "",
                false,
                0L);
    }

    static ApprovalInterruptRecord decided(
            ApprovalInterruptRecord pending,
            Decision decision,
            String authorityDigest,
            long decidedAtEpochMs) {
        Objects.requireNonNull(pending, "pending");
        return restore(
                pending.approvalId,
                pending.ownerFingerprint,
                pending.sessionId,
                pending.planId,
                pending.nodeId,
                pending.actionDigest,
                pending.planDigest,
                pending.contextDigest,
                pending.policyDigest,
                pending.safetyStateDigest,
                pending.createdAtEpochMs,
                pending.expiresAtEpochMs,
                pending.planDeadlineEpochMs,
                decision,
                authorityDigest,
                true,
                decidedAtEpochMs);
    }

    static ApprovalInterruptRecord restore(
            String approvalId,
            String ownerFingerprint,
            String sessionId,
            String planId,
            String nodeId,
            String actionDigest,
            String planDigest,
            String contextDigest,
            String policyDigest,
            String safetyStateDigest,
            long createdAtEpochMs,
            long expiresAtEpochMs,
            long planDeadlineEpochMs,
            Decision decision,
            String authorityDigest,
            boolean authorityTrusted,
            long decidedAtEpochMs) {
        return new ApprovalInterruptRecord(
                approvalId,
                ownerFingerprint,
                sessionId,
                planId,
                nodeId,
                actionDigest,
                planDigest,
                contextDigest,
                policyDigest,
                safetyStateDigest,
                createdAtEpochMs,
                expiresAtEpochMs,
                planDeadlineEpochMs,
                decision,
                authorityDigest,
                authorityTrusted,
                decidedAtEpochMs);
    }

    public String getApprovalId() {
        return approvalId;
    }

    public String getOwnerFingerprint() {
        return ownerFingerprint;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPlanId() {
        return planId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getActionDigest() {
        return actionDigest;
    }

    public String getPlanDigest() {
        return planDigest;
    }

    public String getContextDigest() {
        return contextDigest;
    }

    public String getPolicyDigest() {
        return policyDigest;
    }

    public String getSafetyStateDigest() {
        return safetyStateDigest;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public long getExpiresAtEpochMs() {
        return expiresAtEpochMs;
    }

    public long getPlanDeadlineEpochMs() {
        return planDeadlineEpochMs;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getAuthorityDigest() {
        return authorityDigest;
    }

    public boolean isAuthorityTrusted() {
        return authorityTrusted;
    }

    public long getDecidedAtEpochMs() {
        return decidedAtEpochMs;
    }

    public String getRecordDigest() {
        return recordDigest;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ApprovalInterruptRecord)) {
            return false;
        }
        ApprovalInterruptRecord that = (ApprovalInterruptRecord) other;
        return recordDigest.equals(that.recordDigest)
                && approvalId.equals(that.approvalId)
                && decision == that.decision;
    }

    @Override
    public int hashCode() {
        return recordDigest.hashCode();
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_APPROVAL_RECORD: " + message);
    }
}
