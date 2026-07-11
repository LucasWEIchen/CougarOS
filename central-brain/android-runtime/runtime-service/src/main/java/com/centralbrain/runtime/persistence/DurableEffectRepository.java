package com.centralbrain.runtime.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Transactional effect/outbox owner. R4C2B exposes no dispatcher or adapter call. */
public final class DurableEffectRepository {
    public static final String EFFECT_TYPE_ACTION = "ACTION";
    public static final String EFFECT_TYPE_SOA = "SOA";
    public static final String EFFECT_TYPE_SKILL = "SKILL";
    public static final String DESTINATION_UIB_ACTION = "UIB_ACTION";
    public static final String DESTINATION_SOA_OPERATION = "SOA_OPERATION";
    public static final String DESTINATION_SKILL = "SKILL";
    public static final String EFFECT_STATE_PREPARED = "PREPARED";
    public static final String EFFECT_STATE_IN_FLIGHT = "IN_FLIGHT";
    public static final String EFFECT_STATE_APPLIED = "APPLIED";
    public static final String EFFECT_STATE_FAILED = "FAILED";
    public static final String EFFECT_STATE_CANCELLED = "CANCELLED";
    public static final String OUTBOX_STATE_PENDING = "PENDING";
    public static final String OUTBOX_STATE_IN_FLIGHT = "IN_FLIGHT";
    public static final String OUTBOX_STATE_DELIVERED = "DELIVERED";
    public static final String OUTBOX_STATE_DEAD_LETTER = "DEAD_LETTER";
    public static final String OUTBOX_STATE_CANCELLED = "CANCELLED";
    public static final String AUDIT_EFFECT_PREPARED = "EFFECT_PREPARED";
    public static final String AUDIT_EFFECT_CLAIMED = "EFFECT_CLAIMED";
    public static final String AUDIT_EFFECT_CLAIM_RECOVERED = "EFFECT_CLAIM_RECOVERED";
    public static final String AUDIT_EFFECT_CLAIM_EXHAUSTED = "EFFECT_CLAIM_EXHAUSTED";
    public static final String AUDIT_EFFECT_RETRY_SCHEDULED = "EFFECT_RETRY_SCHEDULED";
    public static final String AUDIT_EFFECT_APPLIED = "EFFECT_APPLIED";
    public static final String AUDIT_EFFECT_DEAD_LETTERED = "EFFECT_DEAD_LETTERED";
    public static final String AUDIT_EFFECT_CANCELLED = "EFFECT_CANCELLED";

    private static final int MAX_METADATA_LENGTH = 256;
    private static final int MAX_GENERATED_ID_LENGTH = 128;
    private static final long MAX_RETRY_DELAY_MS = 86_400_000L;
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final CentralBrainDatabase database;
    private final RuntimeStateDao dao;
    private final int maxAttempts;
    private final LongSupplier wallClockMs;
    private final Supplier<String> uniqueIdSource;

    public DurableEffectRepository(
            CentralBrainDatabase database,
            LongSupplier wallClockMs,
            Supplier<String> uniqueIdSource) {
        this(database, DEFAULT_MAX_ATTEMPTS, wallClockMs, uniqueIdSource);
    }

    public DurableEffectRepository(
            CentralBrainDatabase database,
            int maxAttempts,
            LongSupplier wallClockMs,
            Supplier<String> uniqueIdSource) {
        this.database = Objects.requireNonNull(database, "database");
        this.dao = database.runtimeStateDao();
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("maxAttempts must be in range 1..100");
        }
        this.maxAttempts = maxAttempts;
        this.wallClockMs = Objects.requireNonNull(wallClockMs, "wallClockMs");
        this.uniqueIdSource = Objects.requireNonNull(uniqueIdSource, "uniqueIdSource");
    }

    public static DurableEffectRepository create(CentralBrainDatabase database) {
        return new DurableEffectRepository(
                database,
                DEFAULT_MAX_ATTEMPTS,
                System::currentTimeMillis,
                () -> UUID.randomUUID().toString());
    }

    public PrepareResult prepare(
            String ownerFingerprint,
            String taskId,
            String idempotencyKey,
            String effectType,
            String actionId,
            String payloadDigest,
            String destination,
            String envelopeDigest) {
        validateDigest(ownerFingerprint, "ownerFingerprint");
        validateMetadata(taskId, "taskId");
        validateMetadata(idempotencyKey, "idempotencyKey");
        validateMetadata(actionId, "actionId");
        validateDigest(payloadDigest, "payloadDigest");
        validateDigest(envelopeDigest, "envelopeDigest");
        validateRoute(effectType, destination);
        String scopedIdempotencyKey = DurableDigest.sha256(
                "central-brain-effect-idempotency-v1",
                ownerFingerprint,
                idempotencyKey);

        return runTransaction(() -> {
            PendingEffectEntity existing = dao.findPendingEffectByIdempotency(
                    scopedIdempotencyKey);
            if (existing != null) {
                RuntimeTaskEntity existingTask = requireOwnedTask(
                        existing.taskId,
                        ownerFingerprint);
                OutboxEntity existingOutbox = requireOutbox(existing.effectId);
                if (samePreparation(
                        existing,
                        existingOutbox,
                        taskId,
                        effectType,
                        actionId,
                        payloadDigest,
                        destination,
                        envelopeDigest)) {
                    return new PrepareResult(
                            Snapshot.from(existing, existingOutbox, existingTask.ownerFingerprint),
                            PrepareOutcome.REPLAYED);
                }
                throw new IdempotencyConflictException(
                        "effect idempotency key is bound to a different operation");
            }

            RuntimeTaskEntity task = requireOwnedTask(taskId, ownerFingerprint);
            if (!DurableTaskRepository.STATE_RUNNING.equals(task.state)) {
                throw new TaskNotEligibleException("new effect requires a RUNNING task");
            }

            long now = now();
            PendingEffectEntity effect = new PendingEffectEntity();
            effect.effectId = generatedId("effect-");
            effect.taskId = taskId;
            effect.idempotencyKey = scopedIdempotencyKey;
            effect.effectType = effectType;
            effect.actionId = actionId;
            effect.payloadDigest = payloadDigest;
            effect.state = EFFECT_STATE_PREPARED;
            effect.createdAtWallMs = now;
            effect.updatedAtWallMs = now;

            OutboxEntity outbox = new OutboxEntity();
            outbox.outboxId = generatedId("outbox-");
            outbox.effectId = effect.effectId;
            outbox.destination = destination;
            outbox.envelopeDigest = envelopeDigest;
            outbox.state = OUTBOX_STATE_PENDING;
            outbox.attemptCount = 0;
            outbox.notBeforeWallMs = now;
            outbox.createdAtWallMs = now;
            outbox.updatedAtWallMs = now;

            dao.insertPendingEffect(effect);
            dao.insertOutbox(outbox);
            String detailDigest = DurableDigest.sha256(
                    "central-brain-effect-prepared-v1",
                    effect.effectId,
                    taskId,
                    scopedIdempotencyKey,
                    effectType,
                    actionId,
                    payloadDigest,
                    destination,
                    envelopeDigest);
            insertAudit(
                    effect.effectId,
                    ownerFingerprint,
                    AUDIT_EFFECT_PREPARED,
                    PrepareOutcome.CREATED.name(),
                    detailDigest,
                    now);
            return new PrepareResult(
                    Snapshot.from(effect, outbox, ownerFingerprint),
                    PrepareOutcome.CREATED);
        });
    }

    public Claim claimNext(String destination) {
        validateDestination(destination);
        return runTransaction(() -> {
            long now = now();
            OutboxEntity outbox = dao.findNextClaimableOutbox(
                    destination,
                    now,
                    maxAttempts);
            if (outbox == null) {
                return null;
            }
            PendingEffectEntity effect = requireEffect(outbox.effectId);
            RuntimeTaskEntity task = requireTask(effect.taskId);
            if (!EFFECT_STATE_PREPARED.equals(effect.state)
                    || !OUTBOX_STATE_PENDING.equals(outbox.state)
                    || !DurableTaskRepository.STATE_RUNNING.equals(task.state)) {
                throw new StateConflictException("claimable effect state changed unexpectedly");
            }
            if (outbox.attemptCount >= maxAttempts) {
                throw new StateConflictException("outbox attempt counter is exhausted");
            }

            effect.state = EFFECT_STATE_IN_FLIGHT;
            effect.updatedAtWallMs = now;
            outbox.state = OUTBOX_STATE_IN_FLIGHT;
            outbox.attemptCount++;
            outbox.updatedAtWallMs = now;
            updateEffectAndOutbox(effect, outbox);
            String detailDigest = DurableDigest.sha256(
                    "central-brain-effect-claim-v1",
                    effect.effectId,
                    outbox.outboxId,
                    outbox.destination,
                    Integer.toString(outbox.attemptCount),
                    outbox.envelopeDigest);
            insertAudit(
                    effect.effectId,
                    task.ownerFingerprint,
                    AUDIT_EFFECT_CLAIMED,
                    "ATTEMPT_" + outbox.attemptCount,
                    detailDigest,
                    now);
            return new Claim(
                    Snapshot.from(effect, outbox, task.ownerFingerprint),
                    maxAttempts);
        });
    }

    public MutationOutcome recordSuccess(
            String effectId,
            String outboxId,
            String ownerFingerprint,
            int expectedAttempt,
            String resultDigest) {
        validateClaimReference(effectId, outboxId, ownerFingerprint, expectedAttempt);
        validateDigest(resultDigest, "resultDigest");
        return runTransaction(() -> {
            OwnedRows rows = requireOwnedRows(effectId, outboxId, ownerFingerprint);
            String detailDigest = outcomeDigest(
                    "central-brain-effect-applied-v1",
                    rows,
                    expectedAttempt,
                    resultDigest);
            if (EFFECT_STATE_APPLIED.equals(rows.effect.state)
                    && OUTBOX_STATE_DELIVERED.equals(rows.outbox.state)) {
                return requireReplay(
                        rows,
                        expectedAttempt,
                        AUDIT_EFFECT_APPLIED,
                        detailDigest);
            }
            requireInFlightClaim(rows, expectedAttempt);
            long now = now();
            rows.effect.state = EFFECT_STATE_APPLIED;
            rows.effect.updatedAtWallMs = now;
            rows.outbox.state = OUTBOX_STATE_DELIVERED;
            rows.outbox.updatedAtWallMs = now;
            updateEffectAndOutbox(rows.effect, rows.outbox);
            insertAudit(
                    effectId,
                    ownerFingerprint,
                    AUDIT_EFFECT_APPLIED,
                    "ATTEMPT_" + expectedAttempt,
                    detailDigest,
                    now);
            return MutationOutcome.APPLIED;
        });
    }

    public MutationOutcome scheduleRetry(
            String effectId,
            String outboxId,
            String ownerFingerprint,
            int expectedAttempt,
            long retryDelayMs,
            String failureDigest) {
        validateClaimReference(effectId, outboxId, ownerFingerprint, expectedAttempt);
        validateDigest(failureDigest, "failureDigest");
        if (retryDelayMs < 0 || retryDelayMs > MAX_RETRY_DELAY_MS) {
            throw new IllegalArgumentException("retryDelayMs is outside the supported range");
        }
        if (expectedAttempt >= maxAttempts) {
            throw new AttemptLimitException("claim has no retry attempts remaining");
        }
        return runTransaction(() -> {
            OwnedRows rows = requireOwnedRows(effectId, outboxId, ownerFingerprint);
            if (EFFECT_STATE_PREPARED.equals(rows.effect.state)
                    && OUTBOX_STATE_PENDING.equals(rows.outbox.state)) {
                String replayDigest = retryDigest(
                        rows,
                        expectedAttempt,
                        rows.outbox.notBeforeWallMs,
                        retryDelayMs,
                        failureDigest);
                return requireReplay(
                        rows,
                        expectedAttempt,
                        AUDIT_EFFECT_RETRY_SCHEDULED,
                        replayDigest);
            }
            requireInFlightClaim(rows, expectedAttempt);
            long now = now();
            long notBeforeWallMs;
            try {
                notBeforeWallMs = Math.addExact(now, retryDelayMs);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("retry schedule overflow", exception);
            }
            rows.effect.state = EFFECT_STATE_PREPARED;
            rows.effect.updatedAtWallMs = now;
            rows.outbox.state = OUTBOX_STATE_PENDING;
            rows.outbox.notBeforeWallMs = notBeforeWallMs;
            rows.outbox.updatedAtWallMs = now;
            updateEffectAndOutbox(rows.effect, rows.outbox);
            String detailDigest = retryDigest(
                    rows,
                    expectedAttempt,
                    notBeforeWallMs,
                    retryDelayMs,
                    failureDigest);
            insertAudit(
                    effectId,
                    ownerFingerprint,
                    AUDIT_EFFECT_RETRY_SCHEDULED,
                    "ATTEMPT_" + expectedAttempt,
                    detailDigest,
                    now);
            return MutationOutcome.APPLIED;
        });
    }

    public MutationOutcome deadLetter(
            String effectId,
            String outboxId,
            String ownerFingerprint,
            int expectedAttempt,
            String failureDigest) {
        validateClaimReference(effectId, outboxId, ownerFingerprint, expectedAttempt);
        validateDigest(failureDigest, "failureDigest");
        return runTransaction(() -> {
            OwnedRows rows = requireOwnedRows(effectId, outboxId, ownerFingerprint);
            String detailDigest = outcomeDigest(
                    "central-brain-effect-dead-letter-v1",
                    rows,
                    expectedAttempt,
                    failureDigest);
            if (EFFECT_STATE_FAILED.equals(rows.effect.state)
                    && OUTBOX_STATE_DEAD_LETTER.equals(rows.outbox.state)) {
                return requireReplay(
                        rows,
                        expectedAttempt,
                        AUDIT_EFFECT_DEAD_LETTERED,
                        detailDigest);
            }
            requireInFlightClaim(rows, expectedAttempt);
            long now = now();
            rows.effect.state = EFFECT_STATE_FAILED;
            rows.effect.updatedAtWallMs = now;
            rows.outbox.state = OUTBOX_STATE_DEAD_LETTER;
            rows.outbox.updatedAtWallMs = now;
            updateEffectAndOutbox(rows.effect, rows.outbox);
            insertAudit(
                    effectId,
                    ownerFingerprint,
                    AUDIT_EFFECT_DEAD_LETTERED,
                    "ATTEMPT_" + expectedAttempt,
                    detailDigest,
                    now);
            return MutationOutcome.APPLIED;
        });
    }

    public MutationOutcome cancelPrepared(
            String effectId,
            String outboxId,
            String ownerFingerprint,
            int expectedAttempt,
            String reasonDigest) {
        validateMetadata(effectId, "effectId");
        validateMetadata(outboxId, "outboxId");
        validateDigest(ownerFingerprint, "ownerFingerprint");
        validateDigest(reasonDigest, "reasonDigest");
        if (expectedAttempt < 0) {
            throw new IllegalArgumentException("expectedAttempt must be non-negative");
        }
        return runTransaction(() -> {
            OwnedRows rows = requireOwnedRows(effectId, outboxId, ownerFingerprint);
            String detailDigest = outcomeDigest(
                    "central-brain-effect-cancelled-v1",
                    rows,
                    expectedAttempt,
                    reasonDigest);
            if (EFFECT_STATE_CANCELLED.equals(rows.effect.state)
                    && OUTBOX_STATE_CANCELLED.equals(rows.outbox.state)) {
                return requireReplay(
                        rows,
                        expectedAttempt,
                        AUDIT_EFFECT_CANCELLED,
                        detailDigest);
            }
            if (!EFFECT_STATE_PREPARED.equals(rows.effect.state)
                    || !OUTBOX_STATE_PENDING.equals(rows.outbox.state)
                    || rows.outbox.attemptCount != expectedAttempt) {
                throw new StateConflictException(
                        "only the expected pending attempt can cancel");
            }
            long now = now();
            rows.effect.state = EFFECT_STATE_CANCELLED;
            rows.effect.updatedAtWallMs = now;
            rows.outbox.state = OUTBOX_STATE_CANCELLED;
            rows.outbox.updatedAtWallMs = now;
            updateEffectAndOutbox(rows.effect, rows.outbox);
            insertAudit(
                    effectId,
                    ownerFingerprint,
                    AUDIT_EFFECT_CANCELLED,
                    "ATTEMPT_" + expectedAttempt,
                    detailDigest,
                    now);
            return MutationOutcome.APPLIED;
        });
    }

    public ReconciliationReport reconcileInterruptedClaims() {
        return runTransaction(() -> {
            long now = now();
            List<String> requeuedEffectIds = new ArrayList<>();
            List<String> deadLetteredEffectIds = new ArrayList<>();
            for (OutboxEntity outbox : dao.findOutboxesInState(OUTBOX_STATE_IN_FLIGHT)) {
                PendingEffectEntity effect = requireEffect(outbox.effectId);
                RuntimeTaskEntity task = requireTask(effect.taskId);
                if (!EFFECT_STATE_IN_FLIGHT.equals(effect.state)) {
                    throw new StateConflictException(
                            "in-flight outbox has a non-in-flight effect");
                }
                if (outbox.attemptCount >= maxAttempts) {
                    effect.state = EFFECT_STATE_FAILED;
                    effect.updatedAtWallMs = now;
                    outbox.state = OUTBOX_STATE_DEAD_LETTER;
                    outbox.updatedAtWallMs = now;
                    updateEffectAndOutbox(effect, outbox);
                    String detailDigest = DurableDigest.sha256(
                            "central-brain-effect-claim-exhausted-v1",
                            effect.effectId,
                            outbox.outboxId,
                            Integer.toString(outbox.attemptCount));
                    insertAudit(
                            effect.effectId,
                            task.ownerFingerprint,
                            AUDIT_EFFECT_CLAIM_EXHAUSTED,
                            OUTBOX_STATE_DEAD_LETTER,
                            detailDigest,
                            now);
                    deadLetteredEffectIds.add(effect.effectId);
                } else {
                    effect.state = EFFECT_STATE_PREPARED;
                    effect.updatedAtWallMs = now;
                    outbox.state = OUTBOX_STATE_PENDING;
                    outbox.notBeforeWallMs = now;
                    outbox.updatedAtWallMs = now;
                    updateEffectAndOutbox(effect, outbox);
                    String detailDigest = DurableDigest.sha256(
                            "central-brain-effect-claim-recovery-v1",
                            effect.effectId,
                            outbox.outboxId,
                            Integer.toString(outbox.attemptCount));
                    insertAudit(
                            effect.effectId,
                            task.ownerFingerprint,
                            AUDIT_EFFECT_CLAIM_RECOVERED,
                            OUTBOX_STATE_PENDING,
                            detailDigest,
                            now);
                    requeuedEffectIds.add(effect.effectId);
                }
            }
            return new ReconciliationReport(requeuedEffectIds, deadLetteredEffectIds);
        });
    }

    public Snapshot findOwned(String effectId, String ownerFingerprint) {
        validateMetadata(effectId, "effectId");
        validateDigest(ownerFingerprint, "ownerFingerprint");
        return runTransaction(() -> {
            PendingEffectEntity effect = dao.findPendingEffect(effectId);
            if (effect == null) {
                return null;
            }
            RuntimeTaskEntity task = dao.findTask(effect.taskId);
            if (task == null || !task.ownerFingerprint.equals(ownerFingerprint)) {
                return null;
            }
            return Snapshot.from(effect, requireOutbox(effect.effectId), ownerFingerprint);
        });
    }

    public boolean isDispatchEnabled() {
        return false;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    private OwnedRows requireOwnedRows(
            String effectId,
            String outboxId,
            String ownerFingerprint) {
        PendingEffectEntity effect = requireEffect(effectId);
        RuntimeTaskEntity task = requireTask(effect.taskId);
        if (!task.ownerFingerprint.equals(ownerFingerprint)) {
            throw new TaskNotEligibleException("effect is unavailable to owner");
        }
        OutboxEntity outbox = requireOutbox(effectId);
        if (!outbox.outboxId.equals(outboxId)) {
            throw new StateConflictException("outbox ID does not match effect");
        }
        return new OwnedRows(effect, outbox, task);
    }

    private void requireInFlightClaim(OwnedRows rows, int expectedAttempt) {
        if (!EFFECT_STATE_IN_FLIGHT.equals(rows.effect.state)
                || !OUTBOX_STATE_IN_FLIGHT.equals(rows.outbox.state)
                || rows.outbox.attemptCount != expectedAttempt) {
            throw new StateConflictException("effect claim state or attempt is stale");
        }
    }

    private MutationOutcome requireReplay(
            OwnedRows rows,
            int expectedAttempt,
            String eventType,
            String detailDigest) {
        AuditEventEntity audit = dao.findLatestAuditEvent(rows.effect.effectId, eventType);
        if (rows.outbox.attemptCount != expectedAttempt
                || audit == null
                || !audit.detailDigest.equals(detailDigest)) {
            throw new StateConflictException("terminal/retry replay does not match durable result");
        }
        return MutationOutcome.REPLAYED;
    }

    private static String outcomeDigest(
            String domain,
            OwnedRows rows,
            int expectedAttempt,
            String resultDigest) {
        return DurableDigest.sha256(
                domain,
                rows.effect.effectId,
                rows.outbox.outboxId,
                Integer.toString(expectedAttempt),
                resultDigest);
    }

    private static String retryDigest(
            OwnedRows rows,
            int expectedAttempt,
            long notBeforeWallMs,
            long retryDelayMs,
            String failureDigest) {
        return DurableDigest.sha256(
                "central-brain-effect-retry-v1",
                rows.effect.effectId,
                rows.outbox.outboxId,
                Integer.toString(expectedAttempt),
                Long.toString(notBeforeWallMs),
                Long.toString(retryDelayMs),
                failureDigest);
    }

    private static void validateClaimReference(
            String effectId,
            String outboxId,
            String ownerFingerprint,
            int expectedAttempt) {
        validateMetadata(effectId, "effectId");
        validateMetadata(outboxId, "outboxId");
        validateDigest(ownerFingerprint, "ownerFingerprint");
        if (expectedAttempt < 1) {
            throw new IllegalArgumentException("expectedAttempt must be positive");
        }
    }

    private RuntimeTaskEntity requireOwnedTask(String taskId, String ownerFingerprint) {
        RuntimeTaskEntity task = dao.findTask(taskId);
        if (task == null || !task.ownerFingerprint.equals(ownerFingerprint)) {
            throw new TaskNotEligibleException("task is unavailable to effect owner");
        }
        return task;
    }

    private RuntimeTaskEntity requireTask(String taskId) {
        RuntimeTaskEntity task = dao.findTask(taskId);
        if (task == null) {
            throw new StateConflictException("effect parent task is missing");
        }
        return task;
    }

    private PendingEffectEntity requireEffect(String effectId) {
        PendingEffectEntity effect = dao.findPendingEffect(effectId);
        if (effect == null) {
            throw new StateConflictException("outbox parent effect is missing");
        }
        return effect;
    }

    private OutboxEntity requireOutbox(String effectId) {
        OutboxEntity outbox = dao.findOutboxByEffect(effectId);
        if (outbox == null) {
            throw new StateConflictException("effect outbox row is missing");
        }
        return outbox;
    }

    private void updateEffectAndOutbox(PendingEffectEntity effect, OutboxEntity outbox) {
        if (dao.updatePendingEffect(effect) != 1 || dao.updateOutbox(outbox) != 1) {
            throw new IllegalStateException("effect/outbox update did not affect one row each");
        }
    }

    private void insertAudit(
            String effectId,
            String ownerFingerprint,
            String eventType,
            String outcome,
            String detailDigest,
            long now) {
        AuditEventEntity audit = new AuditEventEntity();
        audit.eventId = generatedId("audit-");
        audit.eventType = eventType;
        audit.subjectId = effectId;
        audit.ownerFingerprint = ownerFingerprint;
        audit.outcome = outcome;
        audit.detailDigest = detailDigest;
        audit.observedAtWallMs = now;
        dao.insertAuditEvent(audit);
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

    private static boolean samePreparation(
            PendingEffectEntity effect,
            OutboxEntity outbox,
            String taskId,
            String effectType,
            String actionId,
            String payloadDigest,
            String destination,
            String envelopeDigest) {
        return effect.taskId.equals(taskId)
                && effect.effectType.equals(effectType)
                && effect.actionId.equals(actionId)
                && effect.payloadDigest.equals(payloadDigest)
                && outbox.destination.equals(destination)
                && outbox.envelopeDigest.equals(envelopeDigest);
    }

    private static void validateRoute(String effectType, String destination) {
        boolean valid = EFFECT_TYPE_ACTION.equals(effectType)
                && DESTINATION_UIB_ACTION.equals(destination);
        valid = valid || EFFECT_TYPE_SOA.equals(effectType)
                && DESTINATION_SOA_OPERATION.equals(destination);
        valid = valid || EFFECT_TYPE_SKILL.equals(effectType)
                && DESTINATION_SKILL.equals(destination);
        if (!valid) {
            throw new IllegalArgumentException("effect type and destination do not match");
        }
    }

    private static void validateDestination(String destination) {
        if (!DESTINATION_UIB_ACTION.equals(destination)
                && !DESTINATION_SOA_OPERATION.equals(destination)
                && !DESTINATION_SKILL.equals(destination)) {
            throw new IllegalArgumentException("unsupported effect destination");
        }
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

    private static final class OwnedRows {
        final PendingEffectEntity effect;
        final OutboxEntity outbox;
        final RuntimeTaskEntity task;

        OwnedRows(
                PendingEffectEntity effect,
                OutboxEntity outbox,
                RuntimeTaskEntity task) {
            this.effect = effect;
            this.outbox = outbox;
            this.task = task;
        }
    }

    public enum PrepareOutcome {
        CREATED,
        REPLAYED
    }

    public enum MutationOutcome {
        APPLIED,
        REPLAYED
    }

    public static final class PrepareResult {
        private final Snapshot snapshot;
        private final PrepareOutcome outcome;

        private PrepareResult(Snapshot snapshot, PrepareOutcome outcome) {
            this.snapshot = snapshot;
            this.outcome = outcome;
        }

        public Snapshot getSnapshot() {
            return snapshot;
        }

        public PrepareOutcome getOutcome() {
            return outcome;
        }
    }

    public static final class Claim {
        private final Snapshot snapshot;
        private final int maxAttempts;

        private Claim(Snapshot snapshot, int maxAttempts) {
            this.snapshot = snapshot;
            this.maxAttempts = maxAttempts;
        }

        public Snapshot getSnapshot() {
            return snapshot;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public boolean canRetry() {
            return snapshot.getAttemptCount() < maxAttempts;
        }
    }

    public static final class Snapshot {
        private final String effectId;
        private final String taskId;
        private final String ownerFingerprint;
        private final String idempotencyToken;
        private final String effectType;
        private final String actionId;
        private final String payloadDigest;
        private final String effectState;
        private final String outboxId;
        private final String destination;
        private final String envelopeDigest;
        private final String outboxState;
        private final int attemptCount;
        private final long notBeforeWallMs;

        private Snapshot(
                String effectId,
                String taskId,
                String ownerFingerprint,
                String idempotencyToken,
                String effectType,
                String actionId,
                String payloadDigest,
                String effectState,
                String outboxId,
                String destination,
                String envelopeDigest,
                String outboxState,
                int attemptCount,
                long notBeforeWallMs) {
            this.effectId = effectId;
            this.taskId = taskId;
            this.ownerFingerprint = ownerFingerprint;
            this.idempotencyToken = idempotencyToken;
            this.effectType = effectType;
            this.actionId = actionId;
            this.payloadDigest = payloadDigest;
            this.effectState = effectState;
            this.outboxId = outboxId;
            this.destination = destination;
            this.envelopeDigest = envelopeDigest;
            this.outboxState = outboxState;
            this.attemptCount = attemptCount;
            this.notBeforeWallMs = notBeforeWallMs;
        }

        static Snapshot from(
                PendingEffectEntity effect,
                OutboxEntity outbox,
                String ownerFingerprint) {
            return new Snapshot(
                    effect.effectId,
                    effect.taskId,
                    ownerFingerprint,
                    effect.idempotencyKey,
                    effect.effectType,
                    effect.actionId,
                    effect.payloadDigest,
                    effect.state,
                    outbox.outboxId,
                    outbox.destination,
                    outbox.envelopeDigest,
                    outbox.state,
                    outbox.attemptCount,
                    outbox.notBeforeWallMs);
        }

        public String getEffectId() {
            return effectId;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public String getIdempotencyToken() {
            return idempotencyToken;
        }

        public String getEffectType() {
            return effectType;
        }

        public String getActionId() {
            return actionId;
        }

        public String getPayloadDigest() {
            return payloadDigest;
        }

        public String getEffectState() {
            return effectState;
        }

        public String getOutboxId() {
            return outboxId;
        }

        public String getDestination() {
            return destination;
        }

        public String getEnvelopeDigest() {
            return envelopeDigest;
        }

        public String getOutboxState() {
            return outboxState;
        }

        public int getAttemptCount() {
            return attemptCount;
        }

        public long getNotBeforeWallMs() {
            return notBeforeWallMs;
        }
    }

    public static final class ReconciliationReport {
        private final List<String> requeuedEffectIds;
        private final List<String> deadLetteredEffectIds;

        private ReconciliationReport(
                List<String> requeuedEffectIds,
                List<String> deadLetteredEffectIds) {
            this.requeuedEffectIds = java.util.Collections.unmodifiableList(
                    new ArrayList<>(requeuedEffectIds));
            this.deadLetteredEffectIds = java.util.Collections.unmodifiableList(
                    new ArrayList<>(deadLetteredEffectIds));
        }

        public int getRequeuedCount() {
            return requeuedEffectIds.size();
        }

        public int getDeadLetteredCount() {
            return deadLetteredEffectIds.size();
        }

        public List<String> getRequeuedEffectIds() {
            return requeuedEffectIds;
        }

        public List<String> getDeadLetteredEffectIds() {
            return deadLetteredEffectIds;
        }
    }

    public static final class IdempotencyConflictException extends IllegalStateException {
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }

    public static final class TaskNotEligibleException extends IllegalStateException {
        public TaskNotEligibleException(String message) {
            super(message);
        }
    }

    public static final class AttemptLimitException extends IllegalStateException {
        public AttemptLimitException(String message) {
            super(message);
        }
    }

    public static final class StateConflictException extends IllegalStateException {
        public StateConflictException(String message) {
            super(message);
        }
    }
}
