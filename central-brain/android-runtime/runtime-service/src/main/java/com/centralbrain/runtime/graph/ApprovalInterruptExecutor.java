package com.centralbrain.runtime.graph;

import com.centralbrain.runtime.graph.ApprovalInterruptRecord.Decision;
import com.centralbrain.runtime.graph.CheckpointSerializer.PayloadCodec;
import com.centralbrain.runtime.graph.CheckpointSerializer.Registration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Creates and transitions digest-bound approval interrupts without dispatching an action. */
public final class ApprovalInterruptExecutor {
    public static final long MAX_APPROVAL_TTL_MS = 5L * 60L * 1_000L;
    public static final String CHECKPOINT_TYPE = "graph.approval.interrupt";
    public static final int CHECKPOINT_SCHEMA_VERSION = 1;

    private ApprovalInterruptExecutor() {}

    public static ApprovalInterruptRecord createPending(Request request, long nowEpochMs) {
        Objects.requireNonNull(request, "request");
        if (nowEpochMs <= 0L || request.planDeadlineEpochMs <= nowEpochMs) {
            throw violation("approval cannot outlive an elapsed plan deadline");
        }
        long requestedExpiry = saturatedAdd(nowEpochMs, request.ttlMs);
        long expiresAtEpochMs = Math.min(requestedExpiry, request.planDeadlineEpochMs);
        if (expiresAtEpochMs <= nowEpochMs) {
            throw violation("approval expiry must follow creation");
        }
        return ApprovalInterruptRecord.pending(
                request.approvalId,
                request.ownerFingerprint,
                request.sessionId,
                request.planId,
                request.nodeId,
                request.actionDigest,
                request.planDigest,
                request.contextDigest,
                request.policyDigest,
                request.safetyStateDigest,
                nowEpochMs,
                expiresAtEpochMs,
                request.planDeadlineEpochMs);
    }

    public static ApprovalInterruptRecord recordDecision(
            ApprovalInterruptRecord pending,
            Decision decision,
            String authorityDigest,
            boolean authorityTrusted,
            long decidedAtEpochMs) {
        requirePending(pending);
        if (decision != Decision.APPROVED
                && decision != Decision.REJECTED
                && decision != Decision.CANCELLED) {
            throw violation("interactive decision is not allowlisted");
        }
        if (!authorityTrusted) {
            throw violation("approval authority is not trusted");
        }
        NodeExecutionContract.requireDigest(authorityDigest, "authorityDigest");
        if (decidedAtEpochMs < pending.getCreatedAtEpochMs()
                || decidedAtEpochMs >= pending.getExpiresAtEpochMs()) {
            throw violation("approval decision is outside its validity window");
        }
        return ApprovalInterruptRecord.decided(
                pending, decision, authorityDigest, decidedAtEpochMs);
    }

    public static ApprovalInterruptRecord expire(
            ApprovalInterruptRecord pending,
            String authorityDigest,
            boolean authorityTrusted,
            long nowEpochMs) {
        requirePending(pending);
        if (!authorityTrusted) {
            throw violation("expiry authority is not trusted");
        }
        NodeExecutionContract.requireDigest(authorityDigest, "authorityDigest");
        if (nowEpochMs < pending.getExpiresAtEpochMs()) {
            throw violation("approval cannot expire before its deadline");
        }
        return ApprovalInterruptRecord.decided(
                pending, Decision.EXPIRED, authorityDigest, nowEpochMs);
    }

    public static Registration<ApprovalInterruptRecord> checkpointRegistration() {
        return new Registration<>(
                CHECKPOINT_TYPE,
                CHECKPOINT_SCHEMA_VERSION,
                ApprovalInterruptRecord.class,
                new ApprovalInterruptCodec());
    }

    private static void requirePending(ApprovalInterruptRecord record) {
        Objects.requireNonNull(record, "record");
        if (record.getDecision() != Decision.PENDING) {
            throw violation("approval decision is terminal and cannot be replayed");
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_APPROVAL_INTERRUPT: " + message);
    }

    /** Caller-owned immutable request; the executor does not generate IDs or read a clock. */
    public static final class Request {
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
        private final long ttlMs;
        private final long planDeadlineEpochMs;

        public Request(
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
                long ttlMs,
                long planDeadlineEpochMs) {
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
            if (ttlMs <= 0L || ttlMs > MAX_APPROVAL_TTL_MS) {
                throw violation("approval ttl is outside the bounded window");
            }
            if (planDeadlineEpochMs <= 0L) {
                throw violation("plan deadline must be positive");
            }
            this.ttlMs = ttlMs;
            this.planDeadlineEpochMs = planDeadlineEpochMs;
        }
    }

    private static final class ApprovalInterruptCodec
            implements PayloadCodec<ApprovalInterruptRecord> {
        @Override
        public CheckpointValue encode(ApprovalInterruptRecord value) {
            Objects.requireNonNull(value, "value");
            Map<String, CheckpointValue> fields = new LinkedHashMap<>();
            fields.put("actionDigest", CheckpointValue.string(value.getActionDigest()));
            fields.put("approvalId", CheckpointValue.string(value.getApprovalId()));
            fields.put("authorityDigest", CheckpointValue.string(value.getAuthorityDigest()));
            fields.put("authorityTrusted", CheckpointValue.bool(value.isAuthorityTrusted()));
            fields.put("contextDigest", CheckpointValue.string(value.getContextDigest()));
            fields.put("createdAtEpochMs", CheckpointValue.string(
                    Long.toString(value.getCreatedAtEpochMs())));
            fields.put("decidedAtEpochMs", CheckpointValue.string(
                    Long.toString(value.getDecidedAtEpochMs())));
            fields.put("decision", CheckpointValue.enumName(value.getDecision()));
            fields.put("expiresAtEpochMs", CheckpointValue.string(
                    Long.toString(value.getExpiresAtEpochMs())));
            fields.put("nodeId", CheckpointValue.string(value.getNodeId()));
            fields.put("ownerFingerprint", CheckpointValue.string(value.getOwnerFingerprint()));
            fields.put("planDeadlineEpochMs", CheckpointValue.string(
                    Long.toString(value.getPlanDeadlineEpochMs())));
            fields.put("planDigest", CheckpointValue.string(value.getPlanDigest()));
            fields.put("planId", CheckpointValue.string(value.getPlanId()));
            fields.put("policyDigest", CheckpointValue.string(value.getPolicyDigest()));
            fields.put("recordDigest", CheckpointValue.string(value.getRecordDigest()));
            fields.put("safetyStateDigest", CheckpointValue.string(value.getSafetyStateDigest()));
            fields.put("sessionId", CheckpointValue.string(value.getSessionId()));
            return CheckpointValue.map(fields);
        }

        @Override
        public ApprovalInterruptRecord decode(CheckpointValue value) {
            value.requireOnlyFields(
                    "actionDigest",
                    "approvalId",
                    "authorityDigest",
                    "authorityTrusted",
                    "contextDigest",
                    "createdAtEpochMs",
                    "decidedAtEpochMs",
                    "decision",
                    "expiresAtEpochMs",
                    "nodeId",
                    "ownerFingerprint",
                    "planDeadlineEpochMs",
                    "planDigest",
                    "planId",
                    "policyDigest",
                    "recordDigest",
                    "safetyStateDigest",
                    "sessionId");
            ApprovalInterruptRecord restored = ApprovalInterruptRecord.restore(
                    value.requireField("approvalId").asString(),
                    value.requireField("ownerFingerprint").asString(),
                    value.requireField("sessionId").asString(),
                    value.requireField("planId").asString(),
                    value.requireField("nodeId").asString(),
                    value.requireField("actionDigest").asString(),
                    value.requireField("planDigest").asString(),
                    value.requireField("contextDigest").asString(),
                    value.requireField("policyDigest").asString(),
                    value.requireField("safetyStateDigest").asString(),
                    parseEpoch(value, "createdAtEpochMs"),
                    parseEpoch(value, "expiresAtEpochMs"),
                    parseEpoch(value, "planDeadlineEpochMs"),
                    parseDecision(value.requireField("decision").asString()),
                    value.requireField("authorityDigest").asString(),
                    value.requireField("authorityTrusted").asBoolean(),
                    parseEpoch(value, "decidedAtEpochMs"));
            String suppliedDigest = NodeExecutionContract.requireDigest(
                    value.requireField("recordDigest").asString(), "recordDigest");
            if (!restored.getRecordDigest().equals(suppliedDigest)) {
                throw violation("restored approval record digest does not match");
            }
            return restored;
        }

        private static long parseEpoch(CheckpointValue value, String field) {
            String encoded = value.requireField(field).asString();
            try {
                if (!isCanonicalDecimal(encoded)) {
                    throw violation(field + " is not canonical");
                }
                return Long.parseLong(encoded);
            } catch (NumberFormatException exception) {
                throw violation(field + " is outside the long range");
            }
        }

        private static boolean isCanonicalDecimal(String encoded) {
            if (encoded.isEmpty() || (encoded.length() > 1 && encoded.charAt(0) == '0')) {
                return false;
            }
            for (int index = 0; index < encoded.length(); index++) {
                if (encoded.charAt(index) < '0' || encoded.charAt(index) > '9') {
                    return false;
                }
            }
            return true;
        }

        private static Decision parseDecision(String encoded) {
            try {
                return Decision.valueOf(encoded);
            } catch (IllegalArgumentException exception) {
                throw violation("approval decision is not allowlisted");
            }
        }
    }
}
