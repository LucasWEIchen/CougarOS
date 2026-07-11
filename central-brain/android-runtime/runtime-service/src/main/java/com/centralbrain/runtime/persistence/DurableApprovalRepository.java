package com.centralbrain.runtime.persistence;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Owner-scoped durable approval request/status/cancel storage with no grant or dispatch path. */
public final class DurableApprovalRepository {
    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_CANCELLED = "CANCELLED";
    public static final String STATE_EXPIRED = "EXPIRED";
    public static final String AUDIT_APPROVAL_REQUESTED = "APPROVAL_REQUESTED";
    public static final String AUDIT_APPROVAL_CANCELLED = "APPROVAL_CANCELLED";
    public static final String AUDIT_APPROVAL_EXPIRED = "APPROVAL_EXPIRED";

    private static final int MAX_METADATA_LENGTH = 256;

    private final CentralBrainDatabase database;
    private final RuntimeStateDao dao;
    private final int maxPendingRecords;
    private final long pendingTtlMs;
    private final LongSupplier wallClockMs;
    private final Supplier<String> uniqueIdSource;

    public DurableApprovalRepository(
            CentralBrainDatabase database,
            int maxPendingRecords,
            long pendingTtlMs,
            LongSupplier wallClockMs,
            Supplier<String> uniqueIdSource) {
        this.database = Objects.requireNonNull(database, "database");
        this.dao = database.runtimeStateDao();
        if (maxPendingRecords < 1 || pendingTtlMs < 1) {
            throw new IllegalArgumentException("approval bounds must be positive");
        }
        this.maxPendingRecords = maxPendingRecords;
        this.pendingTtlMs = pendingTtlMs;
        this.wallClockMs = Objects.requireNonNull(wallClockMs, "wallClockMs");
        this.uniqueIdSource = Objects.requireNonNull(uniqueIdSource, "uniqueIdSource");
    }

    public static DurableApprovalRepository create(
            CentralBrainDatabase database,
            int maxPendingRecords,
            long pendingTtlMs) {
        return new DurableApprovalRepository(
                database,
                maxPendingRecords,
                pendingTtlMs,
                System::currentTimeMillis,
                () -> UUID.randomUUID().toString());
    }

    public RequestResult request(
            String ownerFingerprint,
            String idempotencyKey,
            String actionId,
            String riskClass,
            String reasonCode) {
        return request(
                ownerFingerprint,
                idempotencyKey,
                actionId,
                riskClass,
                reasonCode,
                true);
    }

    public RequestResult request(
            String ownerFingerprint,
            String idempotencyKey,
            String actionId,
            String riskClass,
            String reasonCode,
            boolean creationAllowed) {
        validateDigest(ownerFingerprint, "ownerFingerprint");
        validateMetadata(idempotencyKey, "idempotencyKey");
        validateMetadata(actionId, "actionId");
        validateMetadata(riskClass, "riskClass");
        validateMetadata(reasonCode, "reasonCode");

        return runTransaction(() -> {
            long now = now();
            expirePending(now);
            ApprovalRequestEntity existing = dao.findApprovalByOwnerAndIdempotency(
                    ownerFingerprint,
                    idempotencyKey);
            if (existing != null) {
                if (!existing.actionId.equals(actionId)) {
                    throw new IdempotencyConflictException(
                            "approval idempotency key is bound to another action");
                }
                return RequestResult.from(existing, RequestOutcome.REPLAYED);
            }
            if (!creationAllowed) {
                throw new ApprovalRejectedException(
                        "current policy decision does not allow a new approval request");
            }
            validateHighRisk(riskClass);
            if (dao.countApprovalsInState(STATE_PENDING) >= maxPendingRecords) {
                throw new CapacityExceededException(maxPendingRecords);
            }

            ApprovalRequestEntity approval = new ApprovalRequestEntity();
            approval.approvalId = generatedId("approval-");
            approval.ownerFingerprint = ownerFingerprint;
            approval.actionId = actionId;
            approval.riskClass = riskClass;
            approval.state = STATE_PENDING;
            approval.reasonCode = reasonCode;
            approval.idempotencyKey = idempotencyKey;
            approval.createdAtWallMs = now;
            approval.expiresAtWallMs = saturatedAdd(now, pendingTtlMs);
            approval.updatedAtWallMs = now;
            dao.insertApproval(approval);
            insertAudit(
                    approval,
                    AUDIT_APPROVAL_REQUESTED,
                    RequestOutcome.CREATED.name(),
                    approvalDigest(approval),
                    now);
            return RequestResult.from(approval, RequestOutcome.CREATED);
        });
    }

    public Snapshot findOwned(String approvalId, String ownerFingerprint) {
        validateMetadata(approvalId, "approvalId");
        validateDigest(ownerFingerprint, "ownerFingerprint");
        return runTransaction(() -> {
            expirePending(now());
            ApprovalRequestEntity approval = dao.findApproval(approvalId);
            return approval != null && approval.ownerFingerprint.equals(ownerFingerprint)
                    ? Snapshot.from(approval)
                    : null;
        });
    }

    public CancelOutcome cancelOwned(String approvalId, String ownerFingerprint) {
        validateMetadata(approvalId, "approvalId");
        validateDigest(ownerFingerprint, "ownerFingerprint");
        return runTransaction(() -> {
            long now = now();
            expirePending(now);
            ApprovalRequestEntity approval = dao.findApproval(approvalId);
            if (approval == null || !approval.ownerFingerprint.equals(ownerFingerprint)) {
                return CancelOutcome.NOT_FOUND;
            }
            if (STATE_CANCELLED.equals(approval.state)) {
                return CancelOutcome.REPLAYED;
            }
            if (!STATE_PENDING.equals(approval.state)) {
                return CancelOutcome.NOT_PENDING;
            }
            approval.state = STATE_CANCELLED;
            approval.updatedAtWallMs = now;
            updateOne(approval);
            insertAudit(
                    approval,
                    AUDIT_APPROVAL_CANCELLED,
                    STATE_CANCELLED,
                    approvalDigest(approval),
                    now);
            return CancelOutcome.APPLIED;
        });
    }

    public boolean supportsApprovalGrant() {
        return false;
    }

    public boolean isDurable() {
        return true;
    }

    private void expirePending(long now) {
        for (ApprovalRequestEntity approval : dao.findExpiredPendingApprovals(now)) {
            approval.state = STATE_EXPIRED;
            approval.updatedAtWallMs = now;
            updateOne(approval);
            insertAudit(
                    approval,
                    AUDIT_APPROVAL_EXPIRED,
                    STATE_EXPIRED,
                    approvalDigest(approval),
                    now);
        }
    }

    private void updateOne(ApprovalRequestEntity approval) {
        if (dao.updateApproval(approval) != 1) {
            throw new IllegalStateException("approval update did not affect one row");
        }
    }

    private void insertAudit(
            ApprovalRequestEntity approval,
            String eventType,
            String outcome,
            String detailDigest,
            long now) {
        AuditEventEntity audit = new AuditEventEntity();
        audit.eventId = generatedId("audit-");
        audit.eventType = eventType;
        audit.subjectId = approval.approvalId;
        audit.ownerFingerprint = approval.ownerFingerprint;
        audit.outcome = outcome;
        audit.detailDigest = detailDigest;
        audit.observedAtWallMs = now;
        dao.insertAuditEvent(audit);
    }

    private static String approvalDigest(ApprovalRequestEntity approval) {
        return DurableDigest.sha256(
                "central-brain-approval-v1",
                approval.approvalId,
                approval.actionId,
                approval.riskClass,
                approval.reasonCode,
                approval.state,
                approval.idempotencyKey);
    }

    private <T> T runTransaction(Callable<T> operation) {
        return database.runInTransaction(operation);
    }

    private long now() {
        long value = wallClockMs.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("wall clock must be non-negative");
        }
        return value;
    }

    private String generatedId(String prefix) {
        String value = uniqueIdSource.get();
        if (value == null
                || value.isEmpty()
                || value.length() > 128
                || !value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException("unique ID source returned an invalid value");
        }
        return prefix + value;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static void validateMetadata(String value, String name) {
        if (value == null || value.trim().isEmpty() || value.length() > MAX_METADATA_LENGTH) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void validateDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
    }

    private static void validateHighRisk(String riskClass) {
        if (!"DRIVER_DISTRACTION".equals(riskClass)
                && !"DIAGNOSTIC_WRITE".equals(riskClass)
                && !"OTA".equals(riskClass)) {
            throw new IllegalArgumentException("only high-risk approvals may be persisted");
        }
    }

    public enum RequestOutcome {
        CREATED,
        REPLAYED
    }

    public enum CancelOutcome {
        APPLIED,
        REPLAYED,
        NOT_FOUND,
        NOT_PENDING
    }

    public static final class RequestResult {
        private final Snapshot snapshot;
        private final RequestOutcome outcome;

        private RequestResult(Snapshot snapshot, RequestOutcome outcome) {
            this.snapshot = snapshot;
            this.outcome = outcome;
        }

        static RequestResult from(ApprovalRequestEntity entity, RequestOutcome outcome) {
            return new RequestResult(Snapshot.from(entity), outcome);
        }

        public Snapshot getSnapshot() {
            return snapshot;
        }

        public RequestOutcome getOutcome() {
            return outcome;
        }
    }

    public static final class Snapshot {
        private final String approvalId;
        private final String actionId;
        private final String riskClass;
        private final String state;
        private final String reasonCode;
        private final long createdAtWallMs;
        private final long expiresAtWallMs;

        private Snapshot(
                String approvalId,
                String actionId,
                String riskClass,
                String state,
                String reasonCode,
                long createdAtWallMs,
                long expiresAtWallMs) {
            this.approvalId = approvalId;
            this.actionId = actionId;
            this.riskClass = riskClass;
            this.state = state;
            this.reasonCode = reasonCode;
            this.createdAtWallMs = createdAtWallMs;
            this.expiresAtWallMs = expiresAtWallMs;
        }

        static Snapshot from(ApprovalRequestEntity entity) {
            return new Snapshot(
                    entity.approvalId,
                    entity.actionId,
                    entity.riskClass,
                    entity.state,
                    entity.reasonCode,
                    entity.createdAtWallMs,
                    entity.expiresAtWallMs);
        }

        public String getApprovalId() {
            return approvalId;
        }

        public String getActionId() {
            return actionId;
        }

        public String getRiskClass() {
            return riskClass;
        }

        public String getState() {
            return state;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public long getCreatedAtWallMs() {
            return createdAtWallMs;
        }

        public long getExpiresAtWallMs() {
            return expiresAtWallMs;
        }
    }

    public static final class IdempotencyConflictException extends IllegalStateException {
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }

    public static final class ApprovalRejectedException extends IllegalStateException {
        public ApprovalRejectedException(String message) {
            super(message);
        }
    }

    public static final class CapacityExceededException extends IllegalStateException {
        CapacityExceededException(int maxPendingRecords) {
            super("pending approval capacity exhausted: " + maxPendingRecords);
        }
    }
}
