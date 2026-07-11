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
                    && secondClaimAttempt == 2;

            RuntimeStateDao dao = database.runtimeStateDao();
            int effectCount = dao.countPendingEffects();
            int outboxCount = dao.countOutboxRows();
            int preparedAudits = dao.countAuditEventsByType(
                    DurableEffectRepository.AUDIT_EFFECT_PREPARED);
            int claimedAudits = dao.countAuditEventsByType(
                    DurableEffectRepository.AUDIT_EFFECT_CLAIMED);
            int recoveredAudits = dao.countAuditEventsByType(
                    DurableEffectRepository.AUDIT_EFFECT_CLAIM_RECOVERED);
            boolean auditVerified = effectCount == 2
                    && outboxCount == 2
                    && preparedAudits == 2
                    && claimedAudits == 3
                    && recoveredAudits == 1;

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
