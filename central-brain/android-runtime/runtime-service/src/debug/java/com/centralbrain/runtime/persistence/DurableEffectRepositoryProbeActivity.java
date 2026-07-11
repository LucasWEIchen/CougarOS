package com.centralbrain.runtime.persistence;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Debug-only proof for R4C2A prepare, claim and interrupted-claim reconciliation. */
public final class DurableEffectRepositoryProbeActivity extends Activity {
    private static final String TAG = "CbEffectProbe";
    private static final String DATABASE_NAME = "central_brain_effect_probe.db";
    private static final String OWNER_A = repeat("7", 64);
    private static final String OWNER_B = repeat("8", 64);
    private static final String TASK_PAYLOAD = repeat("9", 64);
    private static final String CHECKPOINT = repeat("a", 64);
    private static final String EFFECT_PAYLOAD = repeat("b", 64);
    private static final String OTHER_PAYLOAD = repeat("c", 64);
    private static final String ENVELOPE = repeat("d", 64);
    private static final String RETRY_FAILURE = repeat("e", 64);
    private static final String OTHER_FAILURE = repeat("f", 64);
    private static final String SUCCESS_RESULT = repeat("1", 64);
    private static final String DEAD_FAILURE = repeat("2", 64);
    private static final String CANCEL_REASON = repeat("3", 64);
    private static final String OTHER_CANCEL_REASON = repeat("4", 64);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        executor.execute(() -> runProbe(nonce == null ? "" : nonce));
    }

    private void runProbe(String nonce) {
        CentralBrainDatabase database = null;
        try {
            getApplicationContext().deleteDatabase(DATABASE_NAME);
            AtomicLong clock = new AtomicLong(1_720_000_000_000L);
            AtomicInteger ids = new AtomicInteger();
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            DurableTaskRepository tasks = taskRepository(database, clock, ids);
            String taskA = runningTask(tasks, OWNER_A, "task-a");
            String taskB = runningTask(tasks, OWNER_B, "task-b");
            DurableTaskRepository.Admission acceptedOnly = tasks.admit(
                    OWNER_A,
                    "effect-probe-session",
                    "task-accepted-only",
                    "task-accepted-only",
                    TASK_PAYLOAD);
            DurableEffectRepository effects = effectRepository(database, clock, ids);
            boolean nonRunningTaskRejected = false;
            try {
                effects.prepare(
                        OWNER_A,
                        acceptedOnly.getTaskId(),
                        "accepted-only-effect",
                        DurableEffectRepository.EFFECT_TYPE_ACTION,
                        "climate.setTemperature",
                        EFFECT_PAYLOAD,
                        DurableEffectRepository.DESTINATION_UIB_ACTION,
                        ENVELOPE);
            } catch (DurableEffectRepository.TaskNotEligibleException expected) {
                nonRunningTaskRejected = true;
            }
            boolean routeMismatchRejected = false;
            try {
                effects.prepare(
                        OWNER_A,
                        taskA,
                        "route-mismatch-effect",
                        DurableEffectRepository.EFFECT_TYPE_ACTION,
                        "climate.setTemperature",
                        EFFECT_PAYLOAD,
                        DurableEffectRepository.DESTINATION_SOA_OPERATION,
                        ENVELOPE);
            } catch (IllegalArgumentException expected) {
                routeMismatchRejected = true;
            }
            DurableEffectRepository.PrepareResult created = effects.prepare(
                    OWNER_A,
                    taskA,
                    "shared-effect-key",
                    DurableEffectRepository.EFFECT_TYPE_ACTION,
                    "climate.setTemperature",
                    EFFECT_PAYLOAD,
                    DurableEffectRepository.DESTINATION_UIB_ACTION,
                    ENVELOPE);
            String effectId = created.getSnapshot().getEffectId();
            String outboxId = created.getSnapshot().getOutboxId();
            boolean prepareVerified =
                    created.getOutcome() == DurableEffectRepository.PrepareOutcome.CREATED
                            && DurableEffectRepository.EFFECT_STATE_PREPARED.equals(
                                    created.getSnapshot().getEffectState())
                            && DurableEffectRepository.OUTBOX_STATE_PENDING.equals(
                                    created.getSnapshot().getOutboxState())
                            && created.getSnapshot().getAttemptCount() == 0;
            boolean scopedTokenVerified = created.getSnapshot().getIdempotencyToken().matches(
                    "[0-9a-f]{64}")
                    && !"shared-effect-key".equals(
                            created.getSnapshot().getIdempotencyToken());
            database.close();

            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            effects = effectRepository(database, clock, ids);
            DurableEffectRepository.PrepareResult replayed = effects.prepare(
                    OWNER_A,
                    taskA,
                    "shared-effect-key",
                    DurableEffectRepository.EFFECT_TYPE_ACTION,
                    "climate.setTemperature",
                    EFFECT_PAYLOAD,
                    DurableEffectRepository.DESTINATION_UIB_ACTION,
                    ENVELOPE);
            boolean replayVerified =
                    replayed.getOutcome() == DurableEffectRepository.PrepareOutcome.REPLAYED
                            && effectId.equals(replayed.getSnapshot().getEffectId())
                            && outboxId.equals(replayed.getSnapshot().getOutboxId());
            boolean conflictVerified = false;
            try {
                effects.prepare(
                        OWNER_A,
                        taskA,
                        "shared-effect-key",
                        DurableEffectRepository.EFFECT_TYPE_ACTION,
                        "climate.setTemperature",
                        OTHER_PAYLOAD,
                        DurableEffectRepository.DESTINATION_UIB_ACTION,
                        ENVELOPE);
            } catch (DurableEffectRepository.IdempotencyConflictException expected) {
                conflictVerified = true;
            }

            clock.incrementAndGet();
            DurableEffectRepository.PrepareResult otherOwner = effects.prepare(
                    OWNER_B,
                    taskB,
                    "shared-effect-key",
                    DurableEffectRepository.EFFECT_TYPE_ACTION,
                    "climate.setTemperature",
                    EFFECT_PAYLOAD,
                    DurableEffectRepository.DESTINATION_UIB_ACTION,
                    ENVELOPE);
            boolean ownerScopeVerified =
                    effects.findOwned(effectId, OWNER_B) == null
                            && !effectId.equals(otherOwner.getSnapshot().getEffectId());

            DurableEffectRepository.Claim firstClaim = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            boolean claimVerified = firstClaim != null
                    && effectId.equals(firstClaim.getSnapshot().getEffectId())
                    && DurableEffectRepository.EFFECT_STATE_IN_FLIGHT.equals(
                            firstClaim.getSnapshot().getEffectState())
                    && DurableEffectRepository.OUTBOX_STATE_IN_FLIGHT.equals(
                            firstClaim.getSnapshot().getOutboxState())
                    && firstClaim.getSnapshot().getAttemptCount() == 1
                    && DurableEffectRepository.EFFECT_TYPE_ACTION.equals(
                            firstClaim.getSnapshot().getEffectType())
                    && "climate.setTemperature".equals(firstClaim.getSnapshot().getActionId())
                    && EFFECT_PAYLOAD.equals(firstClaim.getSnapshot().getPayloadDigest())
                    && DurableEffectRepository.DESTINATION_UIB_ACTION.equals(
                            firstClaim.getSnapshot().getDestination())
                    && ENVELOPE.equals(firstClaim.getSnapshot().getEnvelopeDigest());
            database.close();

            clock.addAndGet(100);
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            effects = effectRepository(database, clock, ids);
            DurableEffectRepository.ReconciliationReport firstReconciliation =
                    effects.reconcileInterruptedClaims();
            DurableEffectRepository.ReconciliationReport secondReconciliation =
                    effects.reconcileInterruptedClaims();
            DurableEffectRepository.Snapshot requeued = effects.findOwned(effectId, OWNER_A);
            boolean reopenRequeueVerified = firstReconciliation.getRequeuedCount() == 1
                    && requeued != null
                    && DurableEffectRepository.EFFECT_STATE_PREPARED.equals(
                            requeued.getEffectState())
                    && DurableEffectRepository.OUTBOX_STATE_PENDING.equals(
                            requeued.getOutboxState())
                    && requeued.getAttemptCount() == 1;
            boolean reconciliationIdempotent = secondReconciliation.getRequeuedCount() == 0;
            DurableEffectRepository.Claim fairClaim = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            boolean fairRequeueVerified = fairClaim != null
                    && otherOwner.getSnapshot().getEffectId().equals(
                            fairClaim.getSnapshot().getEffectId())
                    && fairClaim.getSnapshot().getAttemptCount() == 1;
            DurableEffectRepository.Claim secondClaim = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            int secondClaimAttempt = secondClaim == null
                    ? -1
                    : secondClaim.getSnapshot().getAttemptCount();
            boolean secondClaimVerified = secondClaim != null
                    && effectId.equals(secondClaim.getSnapshot().getEffectId())
                    && secondClaimAttempt == 2
                    && secondClaim.canRetry();

            DurableEffectRepository.MutationOutcome retryScheduled = effects.scheduleRetry(
                    effectId,
                    outboxId,
                    OWNER_A,
                    2,
                    500,
                    RETRY_FAILURE);
            DurableEffectRepository.MutationOutcome retryReplay = effects.scheduleRetry(
                    effectId,
                    outboxId,
                    OWNER_A,
                    2,
                    500,
                    RETRY_FAILURE);
            boolean retryVerified = retryScheduled
                    == DurableEffectRepository.MutationOutcome.APPLIED
                    && retryReplay == DurableEffectRepository.MutationOutcome.REPLAYED;
            boolean retryDelayConflictVerified = false;
            try {
                effects.scheduleRetry(
                        effectId,
                        outboxId,
                        OWNER_A,
                        2,
                        501,
                        RETRY_FAILURE);
            } catch (DurableEffectRepository.StateConflictException expected) {
                retryDelayConflictVerified = true;
            }
            boolean retryDigestConflictVerified = false;
            try {
                effects.scheduleRetry(
                        effectId,
                        outboxId,
                        OWNER_A,
                        2,
                        500,
                        OTHER_FAILURE);
            } catch (DurableEffectRepository.StateConflictException expected) {
                retryDigestConflictVerified = true;
            }
            boolean retryNotBeforeVerified = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION) == null;
            clock.addAndGet(499);
            retryNotBeforeVerified = retryNotBeforeVerified && effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION) == null;
            clock.incrementAndGet();
            DurableEffectRepository.Claim finalClaim = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            boolean finalClaimVerified = finalClaim != null
                    && effectId.equals(finalClaim.getSnapshot().getEffectId())
                    && finalClaim.getSnapshot().getAttemptCount() == 3
                    && !finalClaim.canRetry()
                    && finalClaim.getMaxAttempts() == 3;
            boolean attemptLimitVerified = false;
            try {
                effects.scheduleRetry(
                        effectId,
                        outboxId,
                        OWNER_A,
                        3,
                        0,
                        RETRY_FAILURE);
            } catch (DurableEffectRepository.AttemptLimitException expected) {
                attemptLimitVerified = true;
            }
            DurableEffectRepository.MutationOutcome deadLettered = effects.deadLetter(
                    effectId,
                    outboxId,
                    OWNER_A,
                    3,
                    DEAD_FAILURE);
            DurableEffectRepository.MutationOutcome deadLetterReplay = effects.deadLetter(
                    effectId,
                    outboxId,
                    OWNER_A,
                    3,
                    DEAD_FAILURE);
            boolean deadLetterVerified = deadLettered
                    == DurableEffectRepository.MutationOutcome.APPLIED
                    && deadLetterReplay == DurableEffectRepository.MutationOutcome.REPLAYED;
            boolean deadLetterConflictVerified = false;
            try {
                effects.deadLetter(
                        effectId,
                        outboxId,
                        OWNER_A,
                        3,
                        OTHER_FAILURE);
            } catch (DurableEffectRepository.StateConflictException expected) {
                deadLetterConflictVerified = true;
            }

            String successEffectId = fairClaim.getSnapshot().getEffectId();
            String successOutboxId = fairClaim.getSnapshot().getOutboxId();
            boolean staleAttemptRejected = false;
            try {
                effects.recordSuccess(
                        successEffectId,
                        successOutboxId,
                        OWNER_B,
                        2,
                        SUCCESS_RESULT);
            } catch (DurableEffectRepository.StateConflictException expected) {
                staleAttemptRejected = true;
            }
            DurableEffectRepository.MutationOutcome succeeded = effects.recordSuccess(
                    successEffectId,
                    successOutboxId,
                    OWNER_B,
                    1,
                    SUCCESS_RESULT);
            DurableEffectRepository.MutationOutcome successReplay = effects.recordSuccess(
                    successEffectId,
                    successOutboxId,
                    OWNER_B,
                    1,
                    SUCCESS_RESULT);
            boolean successVerified = succeeded == DurableEffectRepository.MutationOutcome.APPLIED
                    && successReplay == DurableEffectRepository.MutationOutcome.REPLAYED;
            boolean successConflictVerified = false;
            try {
                effects.recordSuccess(
                        successEffectId,
                        successOutboxId,
                        OWNER_B,
                        1,
                        OTHER_FAILURE);
            } catch (DurableEffectRepository.StateConflictException expected) {
                successConflictVerified = true;
            }

            DurableEffectRepository.PrepareResult cancellable = effects.prepare(
                    OWNER_A,
                    taskA,
                    "cancel-effect-key",
                    DurableEffectRepository.EFFECT_TYPE_ACTION,
                    "climate.setFanSpeed",
                    EFFECT_PAYLOAD,
                    DurableEffectRepository.DESTINATION_UIB_ACTION,
                    ENVELOPE);
            boolean cancelStaleAttemptRejected = false;
            try {
                effects.cancelPrepared(
                        cancellable.getSnapshot().getEffectId(),
                        cancellable.getSnapshot().getOutboxId(),
                        OWNER_A,
                        1,
                        CANCEL_REASON);
            } catch (DurableEffectRepository.StateConflictException expected) {
                cancelStaleAttemptRejected = true;
            }
            DurableEffectRepository.MutationOutcome cancelled = effects.cancelPrepared(
                    cancellable.getSnapshot().getEffectId(),
                    cancellable.getSnapshot().getOutboxId(),
                    OWNER_A,
                    0,
                    CANCEL_REASON);
            DurableEffectRepository.MutationOutcome cancelReplay = effects.cancelPrepared(
                    cancellable.getSnapshot().getEffectId(),
                    cancellable.getSnapshot().getOutboxId(),
                    OWNER_A,
                    0,
                    CANCEL_REASON);
            boolean cancelVerified = cancelled == DurableEffectRepository.MutationOutcome.APPLIED
                    && cancelReplay == DurableEffectRepository.MutationOutcome.REPLAYED;
            boolean cancelConflictVerified = false;
            try {
                effects.cancelPrepared(
                        cancellable.getSnapshot().getEffectId(),
                        cancellable.getSnapshot().getOutboxId(),
                        OWNER_A,
                        0,
                        OTHER_CANCEL_REASON);
            } catch (DurableEffectRepository.StateConflictException expected) {
                cancelConflictVerified = true;
            }

            DurableEffectRepository.PrepareResult crashAtLimit = effects.prepare(
                    OWNER_A,
                    taskA,
                    "max-attempt-crash-key",
                    DurableEffectRepository.EFFECT_TYPE_ACTION,
                    "climate.setSeatHeat",
                    EFFECT_PAYLOAD,
                    DurableEffectRepository.DESTINATION_UIB_ACTION,
                    ENVELOPE);
            DurableEffectRepository.Claim crashClaimOne = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            effects.scheduleRetry(
                    crashAtLimit.getSnapshot().getEffectId(),
                    crashAtLimit.getSnapshot().getOutboxId(),
                    OWNER_A,
                    1,
                    0,
                    RETRY_FAILURE);
            DurableEffectRepository.Claim crashClaimTwo = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            effects.scheduleRetry(
                    crashAtLimit.getSnapshot().getEffectId(),
                    crashAtLimit.getSnapshot().getOutboxId(),
                    OWNER_A,
                    2,
                    0,
                    RETRY_FAILURE);
            DurableEffectRepository.Claim crashClaimThree = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            boolean maxAttemptClaimsVerified = crashClaimOne != null
                    && crashClaimOne.getSnapshot().getAttemptCount() == 1
                    && crashClaimTwo != null
                    && crashClaimTwo.getSnapshot().getAttemptCount() == 2
                    && crashClaimThree != null
                    && crashClaimThree.getSnapshot().getAttemptCount() == 3
                    && !crashClaimThree.canRetry();
            database.close();

            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            effects = effectRepository(database, clock, ids);
            DurableEffectRepository.ReconciliationReport exhaustedReconciliation =
                    effects.reconcileInterruptedClaims();
            DurableEffectRepository.ReconciliationReport exhaustedReplay =
                    effects.reconcileInterruptedClaims();
            DurableEffectRepository.Snapshot exhaustedSnapshot = effects.findOwned(
                    crashAtLimit.getSnapshot().getEffectId(),
                    OWNER_A);
            boolean maxAttemptCrashDeadLettered = maxAttemptClaimsVerified
                    && exhaustedReconciliation.getRequeuedCount() == 0
                    && exhaustedReconciliation.getDeadLetteredCount() == 1
                    && exhaustedSnapshot != null
                    && DurableEffectRepository.EFFECT_STATE_FAILED.equals(
                            exhaustedSnapshot.getEffectState())
                    && DurableEffectRepository.OUTBOX_STATE_DEAD_LETTER.equals(
                            exhaustedSnapshot.getOutboxState());
            boolean exhaustedReconciliationIdempotent =
                    exhaustedReplay.getRequeuedCount() == 0
                            && exhaustedReplay.getDeadLetteredCount() == 0;

            RuntimeStateDao dao = database.runtimeStateDao();
            int effectCount = dao.countPendingEffects();
            int outboxCount = dao.countOutboxRows();
            int preparedAudits = dao.countAuditEventsByType(
                    DurableEffectRepository.AUDIT_EFFECT_PREPARED);
            int claimedAudits = dao.countAuditEventsByType(
                    DurableEffectRepository.AUDIT_EFFECT_CLAIMED);
            int recoveredAudits = dao.countAuditEventsByType(
                    DurableEffectRepository.AUDIT_EFFECT_CLAIM_RECOVERED);
            int retryAudits = dao.countAuditEventsByType(
                    DurableEffectRepository.AUDIT_EFFECT_RETRY_SCHEDULED);
            int appliedAudits = dao.countAuditEventsByType(
                    DurableEffectRepository.AUDIT_EFFECT_APPLIED);
            int deadLetterAudits = dao.countAuditEventsByType(
                    DurableEffectRepository.AUDIT_EFFECT_DEAD_LETTERED);
            int cancelledAudits = dao.countAuditEventsByType(
                    DurableEffectRepository.AUDIT_EFFECT_CANCELLED);
            int exhaustedAudits = dao.countAuditEventsByType(
                    DurableEffectRepository.AUDIT_EFFECT_CLAIM_EXHAUSTED);
            boolean auditVerified = effectCount == 4
                    && outboxCount == 4
                    && preparedAudits == 4
                    && claimedAudits == 7
                    && recoveredAudits == 1
                    && retryAudits == 3
                    && appliedAudits == 1
                    && deadLetterAudits == 1
                    && cancelledAudits == 1
                    && exhaustedAudits == 1;
            boolean terminalStatesVerified =
                    dao.countPendingEffectsInState(
                            DurableEffectRepository.EFFECT_STATE_APPLIED) == 1
                            && dao.countPendingEffectsInState(
                                    DurableEffectRepository.EFFECT_STATE_FAILED) == 2
                            && dao.countPendingEffectsInState(
                                    DurableEffectRepository.EFFECT_STATE_CANCELLED) == 1
                            && dao.countOutboxRowsInState(
                                    DurableEffectRepository.OUTBOX_STATE_DELIVERED) == 1
                            && dao.countOutboxRowsInState(
                                    DurableEffectRepository.OUTBOX_STATE_DEAD_LETTER) == 2
                            && dao.countOutboxRowsInState(
                                    DurableEffectRepository.OUTBOX_STATE_CANCELLED) == 1;

            Log.i(TAG, "nonce=" + nonce
                    + " effect_probe_complete=true"
                    + " effect_prepare_transaction_verified=" + prepareVerified
                    + " effect_non_running_task_rejected=" + nonRunningTaskRejected
                    + " effect_route_mismatch_rejected=" + routeMismatchRejected
                    + " effect_owner_scoped_token_verified=" + scopedTokenVerified
                    + " effect_reopen_replay_verified=" + replayVerified
                    + " effect_idempotency_conflict_verified=" + conflictVerified
                    + " effect_owner_scope_verified=" + ownerScopeVerified
                    + " outbox_claim_transaction_verified=" + claimVerified
                    + " outbox_reopen_requeue_verified=" + reopenRequeueVerified
                    + " outbox_reconciliation_idempotent=" + reconciliationIdempotent
                    + " outbox_fair_requeue_verified=" + fairRequeueVerified
                    + " outbox_second_claim_verified=" + secondClaimVerified
                    + " effect_retry_idempotent_verified=" + retryVerified
                    + " effect_retry_delay_conflict_verified="
                    + retryDelayConflictVerified
                    + " effect_retry_digest_conflict_verified="
                    + retryDigestConflictVerified
                    + " effect_retry_not_before_verified=" + retryNotBeforeVerified
                    + " effect_final_claim_verified=" + finalClaimVerified
                    + " effect_attempt_limit_verified=" + attemptLimitVerified
                    + " effect_dead_letter_idempotent_verified=" + deadLetterVerified
                    + " effect_dead_letter_digest_conflict_verified="
                    + deadLetterConflictVerified
                    + " effect_stale_attempt_rejected=" + staleAttemptRejected
                    + " effect_success_idempotent_verified=" + successVerified
                    + " effect_success_digest_conflict_verified=" + successConflictVerified
                    + " effect_cancel_idempotent_verified=" + cancelVerified
                    + " effect_cancel_stale_attempt_rejected="
                    + cancelStaleAttemptRejected
                    + " effect_cancel_digest_conflict_verified=" + cancelConflictVerified
                    + " effect_max_attempt_crash_dead_lettered="
                    + maxAttemptCrashDeadLettered
                    + " effect_exhausted_reconciliation_idempotent="
                    + exhaustedReconciliationIdempotent
                    + " effect_terminal_states_verified=" + terminalStatesVerified
                    + " effect_outbox_audit_verified=" + auditVerified
                    + " durable_effect_count=" + effectCount
                    + " durable_outbox_count=" + outboxCount
                    + " outbox_claim_attempt=" + secondClaimAttempt
                    + " effect_repository_wired=false"
                    + " outbox_dispatch_enabled=" + effects.isDispatchEnabled()
                    + " service_dispatch_triggered=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " effect_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " effect_repository_wired=false"
                    + " outbox_dispatch_enabled=false"
                    + " service_dispatch_triggered=false"
                    + " hardware_accessed=false", exception);
        } finally {
            if (database != null && database.isOpen()) {
                database.close();
            }
            getApplicationContext().deleteDatabase(DATABASE_NAME);
            runOnUiThread(this::finish);
            executor.shutdown();
        }
    }

    private static String runningTask(
            DurableTaskRepository repository,
            String owner,
            String suffix) {
        DurableTaskRepository.Admission admission = repository.admit(
                owner,
                "effect-probe-session",
                suffix,
                suffix,
                TASK_PAYLOAD);
        repository.transition(
                admission.getTaskId(),
                owner,
                DurableTaskRepository.STATE_ACCEPTED,
                DurableTaskRepository.STATE_RUNNING,
                50,
                2,
                CHECKPOINT);
        return admission.getTaskId();
    }

    private static DurableTaskRepository taskRepository(
            CentralBrainDatabase database,
            AtomicLong clock,
            AtomicInteger ids) {
        return new DurableTaskRepository(
                database,
                clock::get,
                () -> "probe-" + ids.incrementAndGet());
    }

    private static DurableEffectRepository effectRepository(
            CentralBrainDatabase database,
            AtomicLong clock,
            AtomicInteger ids) {
        return new DurableEffectRepository(
                database,
                clock::get,
                () -> "probe-" + ids.incrementAndGet());
    }

    private static String repeat(String value, int count) {
        return String.join("", java.util.Collections.nCopies(count, value));
    }
}
