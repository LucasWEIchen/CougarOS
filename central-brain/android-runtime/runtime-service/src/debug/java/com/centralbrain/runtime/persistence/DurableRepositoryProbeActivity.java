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
                    PAYLOAD_A);
            boolean replayVerified =
                    replayed.getOutcome() == DurableTaskRepository.AdmissionOutcome.REPLAYED
                            && createdTaskId.equals(replayed.getTaskId());

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
            int taskCount = database.runtimeStateDao().countTasks();
            int auditCount = database.runtimeStateDao().countAuditEvents();
            boolean ownerIsolationVerified =
                    otherOwner.getOutcome() == DurableTaskRepository.AdmissionOutcome.CREATED
                            && !createdTaskId.equals(otherOwner.getTaskId())
                            && taskCount == 2
                            && auditCount == 2;

            Log.i(TAG, "nonce=" + nonce
                    + " repository_probe_complete=true"
                    + " task_admission_transaction_verified=" + transactionVerified
                    + " task_idempotent_replay_verified=" + replayVerified
                    + " task_idempotency_conflict_verified=" + conflictVerified
                    + " task_owner_isolation_verified=" + ownerIsolationVerified
                    + " durable_task_count=" + taskCount
                    + " acceptance_audit_count=" + auditCount
                    + " runtime_repository_wired=false"
                    + " durable_dispatch_enabled=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " repository_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " runtime_repository_wired=false"
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
