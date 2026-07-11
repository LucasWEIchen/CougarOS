package com.centralbrain.runtime.persistence;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Debug-only proof that restart reconciliation fails closed without task re-execution. */
public final class RestartReconciliationProbeActivity extends Activity {
    private static final String TAG = "CbRestartProbe";
    private static final String DATABASE_NAME = "central_brain_restart_probe.db";
    private static final String OWNER = repeat("3", 64);
    private static final String PAYLOAD_A = repeat("4", 64);
    private static final String PAYLOAD_B = repeat("5", 64);
    private static final String CHECKPOINT = repeat("6", 64);

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
            DurableTaskRepository beforeRestart = repository(database, ids);
            DurableTaskRepository.Admission active = beforeRestart.admit(
                    OWNER, "session", "active", "active-key", PAYLOAD_A);
            beforeRestart.transition(
                    active.getTaskId(),
                    OWNER,
                    DurableTaskRepository.STATE_ACCEPTED,
                    DurableTaskRepository.STATE_RUNNING,
                    50,
                    2,
                    CHECKPOINT);
            DurableTaskRepository.Admission incompleteCompletion = beforeRestart.admit(
                    OWNER, "session", "completed", "completed-key", PAYLOAD_B);
            beforeRestart.transition(
                    incompleteCompletion.getTaskId(),
                    OWNER,
                    DurableTaskRepository.STATE_ACCEPTED,
                    DurableTaskRepository.STATE_RUNNING,
                    50,
                    2,
                    CHECKPOINT);
            beforeRestart.transition(
                    incompleteCompletion.getTaskId(),
                    OWNER,
                    DurableTaskRepository.STATE_RUNNING,
                    DurableTaskRepository.STATE_COMPLETED,
                    100,
                    3,
                    CHECKPOINT);
            database.close();

            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            DurableTaskRepository afterRestart = repository(database, ids);
            DurableTaskRepository.ReconciliationReport first =
                    afterRestart.reconcileInterruptedTasks();
            DurableTaskRepository.Snapshot activeSnapshot = afterRestart.findOwned(
                    active.getTaskId(), OWNER);
            DurableTaskRepository.Snapshot completionSnapshot = afterRestart.findOwned(
                    incompleteCompletion.getTaskId(), OWNER);
            DurableTaskRepository.ReconciliationReport second =
                    afterRestart.reconcileInterruptedTasks();
            boolean statesFailed = DurableTaskRepository.STATE_FAILED.equals(
                    activeSnapshot.getState())
                    && DurableTaskRepository.STATE_FAILED.equals(completionSnapshot.getState())
                    && !activeSnapshot.isTerminalDeliverySettled()
                    && !completionSnapshot.isTerminalDeliverySettled();
            boolean reportVerified = first.getActiveTaskCount() == 1
                    && first.getIncompleteCompletionCount() == 1
                    && first.getTotalCount() == 2;
            boolean idempotent = second.getTotalCount() == 0;
            int reconciliationAuditCount = database.runtimeStateDao().countAuditEventsByType(
                    DurableTaskRepository.AUDIT_TASK_RESTART_RECONCILED);

            Log.i(TAG, "nonce=" + nonce
                    + " restart_probe_complete=true"
                    + " active_task_reconciled_failed=" + statesFailed
                    + " incomplete_completion_reconciled_failed=" + statesFailed
                    + " restart_reconciliation_report_verified=" + reportVerified
                    + " restart_reconciliation_idempotent=" + idempotent
                    + " restart_reconciliation_audit_count=" + reconciliationAuditCount
                    + " task_execution_resume_enabled=false"
                    + " durable_dispatch_enabled=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " restart_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " task_execution_resume_enabled=false"
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

    private static DurableTaskRepository repository(
            CentralBrainDatabase database,
            AtomicInteger ids) {
        return new DurableTaskRepository(
                database,
                () -> 1_720_000_000_000L,
                () -> "probe-" + ids.incrementAndGet());
    }

    private static String repeat(String value, int count) {
        return String.join("", java.util.Collections.nCopies(count, value));
    }
}
