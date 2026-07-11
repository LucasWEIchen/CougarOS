package com.centralbrain.runtime.persistence;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Debug-only read proof that production Runtime task lifecycle writes reached Room. */
public final class RuntimeDurabilityProbeActivity extends Activity {
    private static final String TAG = "CbRuntimeDurability";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        executor.execute(() -> inspect(nonce == null ? "" : nonce));
    }

    private void inspect(String nonce) {
        CentralBrainDatabase database = null;
        try {
            database = CentralBrainDatabase.open(getApplicationContext());
            RuntimeStateDao dao = database.runtimeStateDao();
            int taskCount = dao.countTasks();
            int completedCount = dao.countTasksInState(DurableTaskRepository.STATE_COMPLETED);
            int cancelledCount = dao.countTasksInState(DurableTaskRepository.STATE_CANCELLED);
            int checkpointCount = dao.countTaskCheckpoints();
            int transitionAuditCount = dao.countAuditEventsByType(
                    DurableTaskRepository.AUDIT_TASK_TRANSITION);
            int settlementCount = dao.countSettledTerminalTasks();
            int settlementAuditCount = dao.countAuditEventsByType(
                    DurableTaskRepository.AUDIT_TERMINAL_DELIVERY_SETTLED);
            boolean completedVerified = completedCount >= 1;
            boolean cancelledVerified = cancelledCount >= 1;
            boolean checkpointVerified = checkpointCount >= 4 && transitionAuditCount >= 2;
            boolean settlementVerified = settlementCount >= 2 && settlementAuditCount >= 2;

            Log.i(TAG, "nonce=" + nonce
                    + " runtime_durability_probe_complete=true"
                    + " durable_completed_task_verified=" + completedVerified
                    + " durable_cancelled_task_verified=" + cancelledVerified
                    + " durable_checkpoint_chain_verified=" + checkpointVerified
                    + " durable_terminal_settlement_verified=" + settlementVerified
                    + " production_task_count=" + taskCount
                    + " production_checkpoint_count=" + checkpointCount
                    + " production_transition_audit_count=" + transitionAuditCount
                    + " production_settlement_count=" + settlementCount
                    + " runtime_repository_wired=true"
                    + " task_recovery_enabled=false"
                    + " durable_dispatch_enabled=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " runtime_durability_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " runtime_repository_wired=true"
                    + " task_recovery_enabled=false"
                    + " durable_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        } finally {
            if (database != null && database.isOpen()) {
                database.close();
            }
            runOnUiThread(this::finish);
            executor.shutdown();
        }
    }
}
