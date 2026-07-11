package com.centralbrain.runtime.persistence;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Debug-only API 33 proof for R4B1 task admission transactions and idempotency. */
public final class DurableRepositoryProbeActivity extends Activity {
    private static final String TAG = "CbRepositoryProbe";
    private static final String DATABASE_NAME = "central_brain_repository_probe.db";
    private static final String OWNER_A = repeat("a", 64);
    private static final String OWNER_B = repeat("b", 64);
    private static final String PAYLOAD_A = repeat("c", 64);
    private static final String PAYLOAD_B = repeat("d", 64);
    private static final String CHECKPOINT_RUNNING = repeat("e", 64);
    private static final String CHECKPOINT_COMPLETED = repeat("f", 64);

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
            AtomicInteger ids = new AtomicInteger();
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            DurableTaskRepository firstRepository = new DurableTaskRepository(
                    database,
                    () -> 1_720_000_000_000L,
                    () -> "probe-" + ids.incrementAndGet());
            DurableTaskRepository.Admission created = firstRepository.admit(
                    OWNER_A,
                    "session-a",
                    "client-a",
                    "shared-key",
                    PAYLOAD_A);
            boolean transactionVerified =
                    created.getOutcome() == DurableTaskRepository.AdmissionOutcome.CREATED
                            && DurableTaskRepository.STATE_ACCEPTED.equals(created.getState());
            String createdTaskId = created.getTaskId();
            database.close();

            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            DurableTaskRepository secondRepository = new DurableTaskRepository(
                    database,
                    () -> 1_720_000_000_100L,
                    () -> "probe-" + ids.incrementAndGet());
            DurableTaskRepository.Admission replayed = secondRepository.admit(
                    OWNER_A,
                    "session-a",
                    "client-a",
                    "shared-key",
                    PAYLOAD_A,
                    false);
            boolean replayVerified =
                    replayed.getOutcome() == DurableTaskRepository.AdmissionOutcome.REPLAYED
                            && createdTaskId.equals(replayed.getTaskId());

            boolean newExpiredDeadlineRejected = false;
            try {
                secondRepository.admit(
                        OWNER_A,
                        "session-a",
                        "expired-client",
                        "expired-key",
                        PAYLOAD_A,
                        false);
            } catch (DurableTaskRepository.AdmissionRejectedException expected) {
                newExpiredDeadlineRejected = true;
            }

            boolean conflictVerified = false;
            try {
                secondRepository.admit(
                        OWNER_A,
                        "session-a",
                        "client-a",
                        "shared-key",
                        PAYLOAD_B);
            } catch (DurableTaskRepository.IdempotencyConflictException expected) {
                conflictVerified = true;
            }

            DurableTaskRepository.Admission otherOwner = secondRepository.admit(
                    OWNER_B,
                    "session-b",
                    "client-b",
                    "shared-key",
                    PAYLOAD_B);
            DurableTaskRepository.Transition running = secondRepository.transition(
                    createdTaskId,
                    OWNER_A,
                    DurableTaskRepository.STATE_ACCEPTED,
                    DurableTaskRepository.STATE_RUNNING,
                    50,
                    2,
                    CHECKPOINT_RUNNING);
            DurableTaskRepository.Transition completed = secondRepository.transition(
                    createdTaskId,
                    OWNER_A,
                    DurableTaskRepository.STATE_RUNNING,
                    DurableTaskRepository.STATE_COMPLETED,
                    100,
                    3,
                    CHECKPOINT_COMPLETED);
            DurableTaskRepository.Transition transitionReplay = secondRepository.transition(
                    createdTaskId,
                    OWNER_A,
                    DurableTaskRepository.STATE_RUNNING,
                    DurableTaskRepository.STATE_COMPLETED,
                    100,
                    3,
                    CHECKPOINT_COMPLETED);
            DurableTaskRepository.Settlement settlement =
                    secondRepository.settleTerminalDelivery(
                            createdTaskId,
                            OWNER_A,
                            CHECKPOINT_COMPLETED);
            DurableTaskRepository.Settlement settlementReplay =
                    secondRepository.settleTerminalDelivery(
                            createdTaskId,
                            OWNER_A,
                            CHECKPOINT_COMPLETED);
            int taskCount = database.runtimeStateDao().countTasks();
            int auditCount = database.runtimeStateDao().countAuditEvents();
            int acceptanceAuditCount = database.runtimeStateDao().countAuditEventsByType(
                    DurableTaskRepository.AUDIT_TASK_ACCEPTED);
            int checkpointCount = database.runtimeStateDao().countTaskCheckpoints();
            boolean ownerIsolationVerified =
                    otherOwner.getOutcome() == DurableTaskRepository.AdmissionOutcome.CREATED
                            && !createdTaskId.equals(otherOwner.getTaskId())
                            && taskCount == 2
                            && acceptanceAuditCount == 2;
            boolean transitionVerified =
                    running.getOutcome() == DurableTaskRepository.TransitionOutcome.APPLIED
                            && completed.getOutcome()
                                    == DurableTaskRepository.TransitionOutcome.APPLIED
                            && transitionReplay.getOutcome()
                                    == DurableTaskRepository.TransitionOutcome.REPLAYED
                            && checkpointCount == 4;
            boolean settlementVerified =
                    settlement == DurableTaskRepository.Settlement.APPLIED
                            && settlementReplay == DurableTaskRepository.Settlement.REPLAYED;

            Log.i(TAG, "nonce=" + nonce
                    + " repository_probe_complete=true"
                    + " task_admission_transaction_verified=" + transactionVerified
                    + " task_idempotent_replay_verified=" + replayVerified
                    + " expired_deadline_replay_verified=" + replayVerified
                    + " new_expired_deadline_rejected=" + newExpiredDeadlineRejected
                    + " task_idempotency_conflict_verified=" + conflictVerified
                    + " task_owner_isolation_verified=" + ownerIsolationVerified
                    + " task_transition_transaction_verified=" + transitionVerified
                    + " task_transition_replay_verified="
                    + (transitionReplay.getOutcome()
                            == DurableTaskRepository.TransitionOutcome.REPLAYED)
                    + " terminal_settlement_transaction_verified=" + settlementVerified
                    + " durable_task_count=" + taskCount
                    + " acceptance_audit_count=" + acceptanceAuditCount
                    + " durable_audit_count=" + auditCount
                    + " durable_checkpoint_count=" + checkpointCount
                    + " repository_probe_isolated=true"
                    + " durable_dispatch_enabled=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " repository_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " repository_probe_isolated=true"
                    + " durable_dispatch_enabled=false"
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

    private static String repeat(String value, int count) {
        return String.join("", java.util.Collections.nCopies(count, value));
    }
}
