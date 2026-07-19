package com.centralbrain.runtime.persistence;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.orchestration.OrchestrationBackend;
import com.centralbrain.runtime.orchestration.OrchestrationBackend.Result;
import com.centralbrain.runtime.orchestration.OrchestrationBackend.SessionDescriptor;
import com.centralbrain.runtime.orchestration.OrchestrationBackendFactory;
import com.centralbrain.sdk.orchestration.ICentralBrainOrchestration;
import com.centralbrain.sdk.orchestration.OrchestrationContract;
import com.centralbrain.sdk.orchestration.OrchestrationSnapshot;
import com.centralbrain.sdk.orchestration.OrchestrationStartRequest;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** DUMP-protected Room/restart probe for the Orchestration V1 debug composition. */
public final class OrchestrationRuntimeProbeActivity extends Activity {
    private static final String TAG = "CbOrchestrationV1";
    private static final String DATABASE_NAME = "central_brain_orchestration_probe.db";
    private static final String OWNER = "a".repeat(64);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        executor.execute(() -> runProbe(nonce == null ? "" : nonce));
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runProbe(String nonce) {
        CentralBrainDatabase database = null;
        OrchestrationBackend backend = null;
        try {
            if (nonce.isEmpty()) {
                throw new IllegalArgumentException("probe nonce is required");
            }
            deleteDatabase(DATABASE_NAME);
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            DurableOrchestrationProjectionRepository repository =
                    new DurableOrchestrationProjectionRepository(database);
            backend = OrchestrationBackendFactory.create(getApplicationContext());

            ProbeSession cold = createSession(
                    database,
                    "scene.comfort.cold.v1",
                    "cold-" + nonce);
            Result coldResult = backend.start(cold.descriptor, request(cold));
            OrchestrationContract.validatePlanForSnapshot(
                    coldResult.getPlan(), coldResult.getSnapshot());
            DurableOrchestrationProjectionRepository.Commit coldCommit =
                    repository.commitOwned(cold.descriptor, coldResult);
            require(coldResult.getSnapshot().state
                            == ICentralBrainOrchestration.STATE_COMPLETED,
                    "COLD_NOT_COMPLETED");
            require(coldCommit.isChanged() && coldCommit.getEvent() != null,
                    "COLD_NOT_COMMITTED");

            ProbeSession fatigue = createSession(
                    database,
                    "scene.fatigue.assist.v1",
                    "fatigue-" + nonce);
            Result fatigueResult = backend.start(fatigue.descriptor, request(fatigue));
            OrchestrationSnapshot fatigueSnapshot = fatigueResult.getSnapshot();
            require(fatigueSnapshot.state
                            == ICentralBrainOrchestration.STATE_WAITING_APPROVAL,
                    "FATIGUE_NOT_WAITING");
            require(fatigueSnapshot.approvalResponseAvailable
                            && !fatigueSnapshot.approvalAuthorityTrusted,
                    "APPROVAL_BOUNDARY_INVALID");
            repository.commitOwned(fatigue.descriptor, fatigueResult);

            RuntimeStateDao dao = database.runtimeStateDao();
            require(dao.findPlan(coldResult.getPlan().planId) != null
                            && dao.countPlanNodes(coldResult.getPlan().planId)
                                    == coldResult.getPlan().nodes.length,
                    "COLD_PROJECTION_MISSING");
            require(dao.findPlan(fatigueResult.getPlan().planId) != null
                            && dao.countRuntimeEvents(fatigue.sessionId) == 1,
                    "FATIGUE_PROJECTION_MISSING");

            backend.close();
            backend = null;
            database.close();
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            repository = new DurableOrchestrationProjectionRepository(database);
            DurableOrchestrationProjectionRepository.RecoveryReport report =
                    repository.reconcileInterrupted();
            dao = database.runtimeStateDao();
            SessionEntity recovered = dao.findSession(fatigue.sessionId);
            boolean verified = report.getCandidateCount() == 1
                    && report.getReconciledCount() == 1
                    && report.getStuckCount() == 1
                    && recovered != null
                    && recovered.state == ICentralBrainSessionRuntime.SESSION_STATE_STUCK
                    && dao.countEffectObservationsForRecovery(fatigue.sessionId) == 0
                    && dao.countRuntimeEvents(fatigue.sessionId) == 1;
            require(verified, "RESTART_RECONCILIATION_INVALID");

            Log.i(TAG, "nonce=" + nonce
                    + " orchestration_v1_probe_complete=true"
                    + " orchestration_debug_simulation_verified=true"
                    + " orchestration_room_projection_verified=true"
                    + " orchestration_restart_stuck_verified=true"
                    + " orchestration_restart_effect_replay_count=0"
                    + " approval_authority_trusted=false"
                    + " undo_authority_available=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (Throwable failure) {
            Log.e(TAG, "nonce=" + nonce
                    + " orchestration_v1_probe_complete=false"
                    + " hardware_accessed=false production_ready=false"
                    + " target_hardware_validated=false", failure);
        } finally {
            if (backend != null) {
                backend.close();
            }
            if (database != null) {
                database.close();
            }
            runOnUiThread(this::finish);
        }
    }

    private ProbeSession createSession(
            CentralBrainDatabase database,
            String scenarioId,
            String clientRequestId) {
        long now = System.currentTimeMillis();
        SessionEntity row = new SessionEntity();
        row.sessionId = UUID.randomUUID().toString();
        row.ownerFingerprint = OWNER;
        row.clientRequestId = UUID.nameUUIDFromBytes(clientRequestId.getBytes()).toString();
        row.requestDigest = "b".repeat(64);
        row.scenarioId = scenarioId;
        row.state = ICentralBrainSessionRuntime.SESSION_STATE_CREATED;
        row.activePlanRevision = 0;
        row.lastEventSequence = 0L;
        row.createdAtWallMs = now;
        row.updatedAtWallMs = now;
        row.deadlineWallMs = now + 120_000L;
        row.summary = "PROBE_CREATED";
        row.revision = 1L;
        database.runtimeStateDao().insertSession(row);
        return new ProbeSession(row);
    }

    private static OrchestrationStartRequest request(ProbeSession session) {
        OrchestrationStartRequest request = new OrchestrationStartRequest();
        request.schemaVersion = OrchestrationContract.SCHEMA_VERSION;
        request.requestId = UUID.randomUUID().toString();
        request.sessionId = session.sessionId;
        request.scenarioId = session.scenarioId;
        request.executionProfile = ICentralBrainOrchestration.PROFILE_DEBUG_SIMULATION;
        request.simulationMotionState = ICentralBrainOrchestration.MOTION_PARKED;
        request.requestedAtEpochMs = System.currentTimeMillis();
        OrchestrationContract.validateStartRequest(request, request.requestedAtEpochMs);
        return request;
    }

    private static void require(boolean condition, String code) {
        if (!condition) {
            throw new IllegalStateException("CB_ORCHESTRATION_PROBE: " + code);
        }
    }

    private static final class ProbeSession {
        private final String sessionId;
        private final String scenarioId;
        private final SessionDescriptor descriptor;

        private ProbeSession(SessionEntity row) {
            sessionId = row.sessionId;
            scenarioId = row.scenarioId;
            descriptor = new SessionDescriptor(
                    row.ownerFingerprint,
                    row.sessionId,
                    row.scenarioId,
                    row.state,
                    row.activePlanRevision,
                    row.deadlineWallMs,
                    row.revision);
        }
    }
}
