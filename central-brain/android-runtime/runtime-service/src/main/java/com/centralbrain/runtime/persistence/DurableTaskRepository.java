package com.centralbrain.runtime.persistence;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Transactional R4 task lifecycle owner. It stores metadata/digests and never dispatches effects. */
public final class DurableTaskRepository {
    public static final String STATE_ACCEPTED = "ACCEPTED";
    public static final String STATE_RUNNING = "RUNNING";
    public static final String STATE_COMPLETED = "COMPLETED";
    public static final String STATE_FAILED = "FAILED";
    public static final String STATE_CANCELLED = "CANCELLED";
    public static final String AUDIT_TASK_ACCEPTED = "TASK_ACCEPTED";
    public static final String AUDIT_TASK_TRANSITION = "TASK_TRANSITION";
    public static final String AUDIT_TERMINAL_DELIVERY_SETTLED =
            "TASK_TERMINAL_DELIVERY_SETTLED";

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
        return admit(
                ownerFingerprint,
                sessionId,
                clientRequestId,
                idempotencyKey,
                payloadDigest,
                true);
    }

    public Admission admit(
            String ownerFingerprint,
            String sessionId,
            String clientRequestId,
            String idempotencyKey,
            String payloadDigest,
            boolean creationAllowed) {
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
            if (!creationAllowed) {
                throw new AdmissionRejectedException("new task deadline has elapsed");
            }

            long now = now();
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

            TaskCheckpointEntity checkpoint = checkpoint(
                    task.taskId,
                    1,
                    0,
                    STATE_ACCEPTED,
                    payloadDigest,
                    now);
            dao.insertCheckpoint(checkpoint);

            insertAudit(
                    task.taskId,
                    ownerFingerprint,
                    AUDIT_TASK_ACCEPTED,
                    AdmissionOutcome.CREATED.name(),
                    payloadDigest,
                    now);
            return Admission.from(task, AdmissionOutcome.CREATED);
        });
    }

    public Transition transition(
            String taskId,
            String ownerFingerprint,
            String expectedState,
            String targetState,
            int progressPercent,
            long sequence,
            String checkpointDigest) {
        validateMetadata(taskId, "taskId", false);
        validateDigest(ownerFingerprint, "ownerFingerprint");
        validateState(expectedState);
        validateState(targetState);
        validateTransition(expectedState, targetState);
        validateProgress(targetState, progressPercent);
        if (sequence < 2) {
            throw new IllegalArgumentException("transition sequence must be at least 2");
        }
        validateDigest(checkpointDigest, "checkpointDigest");

        return runTransaction(() -> {
            RuntimeTaskEntity task = dao.findTask(taskId);
            if (task == null || !task.ownerFingerprint.equals(ownerFingerprint)) {
                return Transition.notFound();
            }
            TaskCheckpointEntity latest = dao.findLatestCheckpoint(taskId);
            if (task.state.equals(targetState)
                    && latest != null
                    && latest.sequence == sequence
                    && latest.state.equals(targetState)
                    && task.progressPercent == progressPercent
                    && latest.payloadDigest.equals(checkpointDigest)) {
                return Transition.from(task, sequence, TransitionOutcome.REPLAYED);
            }
            if (!task.state.equals(expectedState)) {
                throw new StateConflictException(
                        "durable task state conflict: expected " + expectedState
                                + " but was " + task.state);
            }
            long currentSequence = latest == null ? 0 : latest.sequence;
            if (sequence != currentSequence + 1) {
                throw new StateConflictException(
                        "durable task sequence conflict: expected " + (currentSequence + 1)
                                + " but was " + sequence);
            }
            if (progressPercent < task.progressPercent) {
                throw new StateConflictException("durable task progress cannot decrease");
            }

            long now = now();
            task.state = targetState;
            task.progressPercent = progressPercent;
            task.updatedAtWallMs = now;
            if (dao.updateTask(task) != 1) {
                throw new IllegalStateException("durable task update did not affect one row");
            }
            dao.insertCheckpoint(checkpoint(
                    task.taskId,
                    sequence,
                    (int) Math.min(Integer.MAX_VALUE, sequence - 1),
                    targetState,
                    checkpointDigest,
                    now));
            insertAudit(
                    task.taskId,
                    ownerFingerprint,
                    AUDIT_TASK_TRANSITION,
                    targetState,
                    checkpointDigest,
                    now);
            return Transition.from(task, sequence, TransitionOutcome.APPLIED);
        });
    }

    public Settlement settleTerminalDelivery(
            String taskId,
            String ownerFingerprint,
            String detailDigest) {
        validateMetadata(taskId, "taskId", false);
        validateDigest(ownerFingerprint, "ownerFingerprint");
        validateDigest(detailDigest, "detailDigest");
        return runTransaction(() -> {
            RuntimeTaskEntity task = dao.findTask(taskId);
            if (task == null || !task.ownerFingerprint.equals(ownerFingerprint)) {
                return Settlement.NOT_FOUND;
            }
            if (!isTerminal(task.state)) {
                throw new StateConflictException(
                        "terminal delivery cannot settle non-terminal task");
            }
            if (task.terminalDeliverySettled) {
                return Settlement.REPLAYED;
            }
            long now = now();
            task.terminalDeliverySettled = true;
            task.updatedAtWallMs = now;
            if (dao.updateTask(task) != 1) {
                throw new IllegalStateException("durable task settlement did not affect one row");
            }
            insertAudit(
                    task.taskId,
                    ownerFingerprint,
                    AUDIT_TERMINAL_DELIVERY_SETTLED,
                    task.state,
                    detailDigest,
                    now);
            return Settlement.APPLIED;
        });
    }

    public Snapshot findOwned(String taskId, String ownerFingerprint) {
        validateMetadata(taskId, "taskId", false);
        validateDigest(ownerFingerprint, "ownerFingerprint");
        return runTransaction(() -> {
            RuntimeTaskEntity task = dao.findTask(taskId);
            if (task == null || !task.ownerFingerprint.equals(ownerFingerprint)) {
                return null;
            }
            TaskCheckpointEntity latest = dao.findLatestCheckpoint(taskId);
            return Snapshot.from(task, latest == null ? 0 : latest.sequence);
        });
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
                || value.length() > MAX_GENERATED_ID_LENGTH
                || !value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException("unique ID source returned an invalid value");
        }
        return prefix + value;
    }

    private TaskCheckpointEntity checkpoint(
            String taskId,
            long sequence,
            int stepIndex,
            String state,
            String payloadDigest,
            long now) {
        TaskCheckpointEntity checkpoint = new TaskCheckpointEntity();
        checkpoint.checkpointId = generatedId("checkpoint-");
        checkpoint.taskId = taskId;
        checkpoint.sequence = sequence;
        checkpoint.stepIndex = stepIndex;
        checkpoint.state = state;
        checkpoint.payloadDigest = payloadDigest;
        checkpoint.createdAtWallMs = now;
        return checkpoint;
    }

    private void insertAudit(
            String taskId,
            String ownerFingerprint,
            String eventType,
            String outcome,
            String detailDigest,
            long now) {
        AuditEventEntity audit = new AuditEventEntity();
        audit.eventId = generatedId("audit-");
        audit.eventType = eventType;
        audit.subjectId = taskId;
        audit.ownerFingerprint = ownerFingerprint;
        audit.outcome = outcome;
        audit.detailDigest = detailDigest;
        audit.observedAtWallMs = now;
        dao.insertAuditEvent(audit);
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

    private static void validateState(String state) {
        if (!STATE_ACCEPTED.equals(state)
                && !STATE_RUNNING.equals(state)
                && !STATE_COMPLETED.equals(state)
                && !STATE_FAILED.equals(state)
                && !STATE_CANCELLED.equals(state)) {
            throw new IllegalArgumentException("unsupported durable task state: " + state);
        }
    }

    private static void validateTransition(String source, String target) {
        boolean valid = STATE_ACCEPTED.equals(source)
                && (STATE_RUNNING.equals(target)
                        || STATE_FAILED.equals(target)
                        || STATE_CANCELLED.equals(target));
        valid = valid || STATE_RUNNING.equals(source)
                && (STATE_COMPLETED.equals(target)
                        || STATE_FAILED.equals(target)
                        || STATE_CANCELLED.equals(target));
        if (!valid) {
            throw new IllegalArgumentException(
                    "invalid durable task transition " + source + " -> " + target);
        }
    }

    private static void validateProgress(String targetState, int progressPercent) {
        if (progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException("progress must be in range 0..100");
        }
        if (STATE_RUNNING.equals(targetState) && progressPercent >= 100) {
            throw new IllegalArgumentException("running progress must be below 100");
        }
        if (STATE_COMPLETED.equals(targetState) && progressPercent != 100) {
            throw new IllegalArgumentException("completed progress must be 100");
        }
    }

    private static boolean isTerminal(String state) {
        return STATE_COMPLETED.equals(state)
                || STATE_FAILED.equals(state)
                || STATE_CANCELLED.equals(state);
    }

    public enum AdmissionOutcome {
        CREATED,
        REPLAYED
    }

    public enum TransitionOutcome {
        APPLIED,
        REPLAYED,
        NOT_FOUND
    }

    public enum Settlement {
        APPLIED,
        REPLAYED,
        NOT_FOUND
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

    public static final class Transition {
        private final Snapshot snapshot;
        private final TransitionOutcome outcome;

        private Transition(Snapshot snapshot, TransitionOutcome outcome) {
            this.snapshot = snapshot;
            this.outcome = outcome;
        }

        static Transition from(
                RuntimeTaskEntity task,
                long sequence,
                TransitionOutcome outcome) {
            return new Transition(Snapshot.from(task, sequence), outcome);
        }

        static Transition notFound() {
            return new Transition(null, TransitionOutcome.NOT_FOUND);
        }

        public Snapshot getSnapshot() {
            return snapshot;
        }

        public TransitionOutcome getOutcome() {
            return outcome;
        }
    }

    public static final class Snapshot {
        private final String taskId;
        private final String state;
        private final int progressPercent;
        private final long sequence;
        private final long acceptedAtWallMs;
        private final boolean terminalDeliverySettled;

        private Snapshot(
                String taskId,
                String state,
                int progressPercent,
                long sequence,
                long acceptedAtWallMs,
                boolean terminalDeliverySettled) {
            this.taskId = taskId;
            this.state = state;
            this.progressPercent = progressPercent;
            this.sequence = sequence;
            this.acceptedAtWallMs = acceptedAtWallMs;
            this.terminalDeliverySettled = terminalDeliverySettled;
        }

        static Snapshot from(RuntimeTaskEntity task, long sequence) {
            return new Snapshot(
                    task.taskId,
                    task.state,
                    task.progressPercent,
                    sequence,
                    task.acceptedAtWallMs,
                    task.terminalDeliverySettled);
        }

        public String getTaskId() {
            return taskId;
        }

        public String getState() {
            return state;
        }

        public int getProgressPercent() {
            return progressPercent;
        }

        public long getSequence() {
            return sequence;
        }

        public long getAcceptedAtWallMs() {
            return acceptedAtWallMs;
        }

        public boolean isTerminalDeliverySettled() {
            return terminalDeliverySettled;
        }

        public boolean isTerminal() {
            return DurableTaskRepository.isTerminal(state);
        }
    }

    public static final class IdempotencyConflictException extends IllegalStateException {
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }

    public static final class AdmissionRejectedException extends IllegalStateException {
        public AdmissionRejectedException(String message) {
            super(message);
        }
    }

    public static final class StateConflictException extends IllegalStateException {
        public StateConflictException(String message) {
            super(message);
        }
    }
}
