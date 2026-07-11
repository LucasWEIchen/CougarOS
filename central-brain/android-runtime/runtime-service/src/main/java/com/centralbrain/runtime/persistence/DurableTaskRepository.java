package com.centralbrain.runtime.persistence;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Transactional R4 task admission owner. It stores metadata/digests and never dispatches effects. */
public final class DurableTaskRepository {
    public static final String STATE_ACCEPTED = "ACCEPTED";
    public static final String AUDIT_TASK_ACCEPTED = "TASK_ACCEPTED";

    private static final int MAX_METADATA_LENGTH = 256;
    private static final int MAX_GENERATED_ID_LENGTH = 128;

    private final CentralBrainDatabase database;
    private final RuntimeStateDao dao;
    private final LongSupplier wallClockMs;
    private final Supplier<String> uniqueIdSource;

    public DurableTaskRepository(
            CentralBrainDatabase database,
            LongSupplier wallClockMs,
            Supplier<String> uniqueIdSource) {
        this.database = Objects.requireNonNull(database, "database");
        this.dao = database.runtimeStateDao();
        this.wallClockMs = Objects.requireNonNull(wallClockMs, "wallClockMs");
        this.uniqueIdSource = Objects.requireNonNull(uniqueIdSource, "uniqueIdSource");
    }

    public static DurableTaskRepository create(CentralBrainDatabase database) {
        return new DurableTaskRepository(
                database,
                System::currentTimeMillis,
                () -> UUID.randomUUID().toString());
    }

    public Admission admit(
            String ownerFingerprint,
            String sessionId,
            String clientRequestId,
            String idempotencyKey,
            String payloadDigest) {
        validateDigest(ownerFingerprint, "ownerFingerprint");
        validateMetadata(sessionId, "sessionId", true);
        validateMetadata(clientRequestId, "clientRequestId", false);
        validateMetadata(idempotencyKey, "idempotencyKey", false);
        validateDigest(payloadDigest, "payloadDigest");

        return runTransaction(() -> {
            RuntimeTaskEntity existing = dao.findTaskByOwnerAndIdempotency(
                    ownerFingerprint,
                    idempotencyKey);
            if (existing != null) {
                if (sameAdmission(existing, sessionId, clientRequestId, payloadDigest)) {
                    return Admission.from(existing, AdmissionOutcome.REPLAYED);
                }
                throw new IdempotencyConflictException(
                        "idempotency key is already bound to a different task payload");
            }

            long now = wallClockMs.getAsLong();
            if (now < 0) {
                throw new IllegalStateException("wall clock must be non-negative");
            }
            RuntimeTaskEntity task = new RuntimeTaskEntity();
            task.taskId = generatedId("task-");
            task.sessionId = sessionId;
            task.ownerFingerprint = ownerFingerprint;
            task.clientRequestId = clientRequestId;
            task.idempotencyKey = idempotencyKey;
            task.state = STATE_ACCEPTED;
            task.progressPercent = 0;
            task.payloadDigest = payloadDigest;
            task.acceptedAtWallMs = now;
            task.updatedAtWallMs = now;
            task.terminalDeliverySettled = false;
            dao.insertTask(task);

            AuditEventEntity audit = new AuditEventEntity();
            audit.eventId = generatedId("audit-");
            audit.eventType = AUDIT_TASK_ACCEPTED;
            audit.subjectId = task.taskId;
            audit.ownerFingerprint = ownerFingerprint;
            audit.outcome = AdmissionOutcome.CREATED.name();
            audit.detailDigest = payloadDigest;
            audit.observedAtWallMs = now;
            dao.insertAuditEvent(audit);
            return Admission.from(task, AdmissionOutcome.CREATED);
        });
    }

    private <T> T runTransaction(Callable<T> operation) {
        return database.runInTransaction(operation);
    }

    private String generatedId(String prefix) {
        String value = uniqueIdSource.get();
        if (value == null
                || value.isEmpty()
                || value.length() > MAX_GENERATED_ID_LENGTH
                || !value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException("unique ID source returned an invalid value");
        }
        return prefix + value;
    }

    private static boolean sameAdmission(
            RuntimeTaskEntity existing,
            String sessionId,
            String clientRequestId,
            String payloadDigest) {
        return existing.sessionId.equals(sessionId)
                && existing.clientRequestId.equals(clientRequestId)
                && existing.payloadDigest.equals(payloadDigest);
    }

    private static void validateMetadata(String value, String name, boolean allowEmpty) {
        if (value == null
                || (!allowEmpty && value.trim().isEmpty())
                || value.length() > MAX_METADATA_LENGTH) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void validateDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
    }

    public enum AdmissionOutcome {
        CREATED,
        REPLAYED
    }

    public static final class Admission {
        private final String taskId;
        private final String state;
        private final long acceptedAtWallMs;
        private final AdmissionOutcome outcome;

        private Admission(
                String taskId,
                String state,
                long acceptedAtWallMs,
                AdmissionOutcome outcome) {
            this.taskId = taskId;
            this.state = state;
            this.acceptedAtWallMs = acceptedAtWallMs;
            this.outcome = outcome;
        }

        static Admission from(RuntimeTaskEntity task, AdmissionOutcome outcome) {
            return new Admission(task.taskId, task.state, task.acceptedAtWallMs, outcome);
        }

        public String getTaskId() {
            return taskId;
        }

        public String getState() {
            return state;
        }

        public long getAcceptedAtWallMs() {
            return acceptedAtWallMs;
        }

        public AdmissionOutcome getOutcome() {
            return outcome;
        }
    }

    public static final class IdempotencyConflictException extends IllegalStateException {
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }
}
