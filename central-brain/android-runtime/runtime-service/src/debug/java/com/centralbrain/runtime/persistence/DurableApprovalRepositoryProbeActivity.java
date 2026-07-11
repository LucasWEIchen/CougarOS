package com.centralbrain.runtime.persistence;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Debug-only API 33 proof for durable owner-scoped approval semantics. */
public final class DurableApprovalRepositoryProbeActivity extends Activity {
    private static final String TAG = "CbApprovalProbe";
    private static final String DATABASE_NAME = "central_brain_approval_probe.db";
    private static final String OWNER_A = repeat("1", 64);
    private static final String OWNER_B = repeat("2", 64);

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
            DurableApprovalRepository firstRepository = new DurableApprovalRepository(
                    database,
                    2,
                    1_000,
                    clock::get,
                    () -> "probe-" + ids.incrementAndGet());
            DurableApprovalRepository.RequestResult created = firstRepository.request(
                    OWNER_A,
                    "shared-key",
                    "ota.install",
                    "OTA",
                    "HIGH_RISK_REQUIRES_APPROVAL");
            String approvalId = created.getSnapshot().getApprovalId();
            database.close();

            clock.addAndGet(100);
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            DurableApprovalRepository secondRepository = new DurableApprovalRepository(
                    database,
                    2,
                    1_000,
                    clock::get,
                    () -> "probe-" + ids.incrementAndGet());
            DurableApprovalRepository.RequestResult replayed = secondRepository.request(
                    OWNER_A,
                    "shared-key",
                    "ota.install",
                    "OTA",
                    "STATE_CHANGED_BUT_EXISTING_OPERATION_WINS");
            boolean replayVerified =
                    replayed.getOutcome() == DurableApprovalRepository.RequestOutcome.REPLAYED
                            && approvalId.equals(replayed.getSnapshot().getApprovalId());

            boolean conflictVerified = false;
            try {
                secondRepository.request(
                        OWNER_A,
                        "shared-key",
                        "diagnostic.write",
                        "DIAGNOSTIC_WRITE",
                        "HIGH_RISK_REQUIRES_APPROVAL");
            } catch (DurableApprovalRepository.IdempotencyConflictException expected) {
                conflictVerified = true;
            }

            DurableApprovalRepository.RequestResult otherOwner = secondRepository.request(
                    OWNER_B,
                    "shared-key",
                    "ota.install",
                    "OTA",
                    "HIGH_RISK_REQUIRES_APPROVAL");
            boolean ownerIsolationVerified =
                    secondRepository.findOwned(approvalId, OWNER_B) == null
                            && !approvalId.equals(otherOwner.getSnapshot().getApprovalId());
            DurableApprovalRepository.CancelOutcome cancelled = secondRepository.cancelOwned(
                    approvalId,
                    OWNER_A);
            DurableApprovalRepository.CancelOutcome cancelReplay = secondRepository.cancelOwned(
                    approvalId,
                    OWNER_A);
            boolean cancelVerified =
                    cancelled == DurableApprovalRepository.CancelOutcome.APPLIED
                            && cancelReplay == DurableApprovalRepository.CancelOutcome.REPLAYED
                            && DurableApprovalRepository.STATE_CANCELLED.equals(
                                    secondRepository.findOwned(approvalId, OWNER_A).getState());

            clock.addAndGet(2_000);
            boolean expiryVerified = DurableApprovalRepository.STATE_EXPIRED.equals(
                    secondRepository.findOwned(
                            otherOwner.getSnapshot().getApprovalId(),
                            OWNER_B).getState());
            RuntimeStateDao dao = database.runtimeStateDao();
            int approvalCount = dao.countApprovals();
            int auditCount = dao.countAuditEvents();
            boolean durableVerified = secondRepository.isDurable()
                    && !secondRepository.supportsApprovalGrant()
                    && approvalCount == 2
                    && auditCount == 4;

            Log.i(TAG, "nonce=" + nonce
                    + " approval_probe_complete=true"
                    + " approval_reopen_replay_verified=" + replayVerified
                    + " approval_idempotency_conflict_verified=" + conflictVerified
                    + " approval_owner_isolation_verified=" + ownerIsolationVerified
                    + " approval_cancel_idempotency_verified=" + cancelVerified
                    + " approval_expiry_verified=" + expiryVerified
                    + " approval_durable=" + durableVerified
                    + " approval_grant_supported=false"
                    + " durable_approval_count=" + approvalCount
                    + " durable_approval_audit_count=" + auditCount
                    + " service_dispatch_triggered=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " approval_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " approval_grant_supported=false"
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

    private static String repeat(String value, int count) {
        return String.join("", java.util.Collections.nCopies(count, value));
    }
}
