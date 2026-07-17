package com.centralbrain.runtime.persistence;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.centralbrain.runtime.graph.GraphRestartReconciler;
import com.centralbrain.runtime.graph.GraphRestartReconciler.CheckpointStatus;
import com.centralbrain.runtime.graph.GraphRestartReconciler.DirectiveType;
import com.centralbrain.runtime.graph.GraphRestartReconciler.Evidence;
import com.centralbrain.runtime.graph.GraphRestartReconciler.EffectDeliveryStatus;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentCompensation;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentEffect;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentNode;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentRun;
import com.centralbrain.runtime.graph.GraphRestartReconciler.Result;
import com.centralbrain.runtime.graph.GraphRunState;
import com.centralbrain.runtime.graph.NodeRunState;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** DUMP-protected three-phase Room/process-death probe for P3-W09. */
public final class GraphRestartRecoveryProbeActivity extends Activity {
    private static final String TAG = "CbGraphRestart";
    private static final String DATABASE_NAME = "central_brain_graph_restart_probe.db";
    private static final String PHASE_SEED = "seed";
    private static final String PHASE_FIRST = "recover-first";
    private static final String PHASE_REPLAY = "recover-replay";
    private static final long BASE_EPOCH_MS = 1_800_000_000_000L;
    private static final long PROCESS_BIRTH_ELAPSED_MS = SystemClock.elapsedRealtime();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = stringExtra("nonce");
        String phase = stringExtra("phase");
        executor.execute(() -> runPhase(nonce, phase));
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private String stringExtra(String name) {
        String value = getIntent().getStringExtra(name);
        return value == null ? "" : value;
    }

    private void runPhase(String nonce, String phase) {
        CentralBrainDatabase database = null;
        try {
            if (nonce.isEmpty()) {
                throw new IllegalArgumentException("probe nonce is required");
            }
            if (PHASE_SEED.equals(phase)) {
                deleteDatabase(DATABASE_NAME);
            } else if (!PHASE_FIRST.equals(phase) && !PHASE_REPLAY.equals(phase)) {
                throw new IllegalArgumentException("unknown probe phase");
            }
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            DurableGraphRecoveryRepository repository =
                    new DurableGraphRecoveryRepository(database);
            if (PHASE_SEED.equals(phase)) {
                seed(database, repository, nonce);
            } else {
                recover(database, repository, nonce, phase);
            }
        } catch (Throwable failure) {
            Log.e(TAG, "nonce=" + nonce
                    + " phase=" + phase
                    + " graph_restart_probe_complete=false", failure);
        } finally {
            if (database != null) {
                database.close();
            }
            runOnUiThread(this::finish);
        }
    }

    private void seed(
            CentralBrainDatabase database,
            DurableGraphRecoveryRepository repository,
            String nonce) {
        ProbeIds ids = new ProbeIds(nonce);
        SessionEntity session = new SessionEntity();
        session.sessionId = ids.sessionId;
        session.ownerFingerprint = sha("owner", nonce);
        session.clientRequestId = ids.requestId;
        session.requestDigest = sha("request", nonce);
        session.scenarioId = "scene.restart.probe.v1";
        session.state = ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING;
        session.activePlanRevision = 1;
        session.lastEventSequence = 0L;
        session.createdAtWallMs = BASE_EPOCH_MS - 10_000L;
        session.updatedAtWallMs = BASE_EPOCH_MS - 1_000L;
        session.deadlineWallMs = BASE_EPOCH_MS + 60_000L;
        session.summary = processToken(nonce);
        session.revision = 1L;
        database.runtimeStateDao().insertSession(session);

        PersistentRun run = durableRun(ids, GraphRunState.EXECUTING,
                NodeRunState.EXECUTING);
        repository.persistInitial(run);
        PersistentRun loaded = repository.loadRequired(ids.planId);
        boolean seedVerified = loaded.getGraphState() == GraphRunState.EXECUTING
                && nodeState(loaded, "effect_node") == NodeRunState.EXECUTING
                && nodeState(loaded, "approval_node") == NodeRunState.WAITING
                && loaded.getEffects().size() == 1
                && loaded.getEffects().get(0).getState() == EffectContract.STATE_UNKNOWN
                && loaded.getCompensations().size() == 1;
        if (!seedVerified) {
            throw new IllegalStateException("durable recovery seed did not round-trip");
        }
        Log.i(TAG, "nonce=" + nonce
                + " graph_restart_seed_complete=true"
                + " graph_restart_room_v4_seed_verified=true"
                + " graph_restart_side_effect_count=0");
    }

    private void recover(
            CentralBrainDatabase database,
            DurableGraphRecoveryRepository repository,
            String nonce,
            String phase) {
        ProbeIds ids = new ProbeIds(nonce);
        RuntimeStateDao dao = database.runtimeStateDao();
        SessionEntity session = dao.findSession(ids.sessionId);
        if (session == null) {
            throw new IllegalStateException("seed Session is missing after process death");
        }
        boolean processChanged = !session.summary.equals(processToken(nonce));
        if (!processChanged) {
            throw new IllegalStateException("probe process did not change across phase");
        }

        PersistentRun loaded = repository.loadRequired(ids.planId);
        Evidence evidence = new Evidence(
                false,
                Map.of(ids.effectCheckpoint, CheckpointStatus.VALID,
                        ids.approvalCheckpoint, CheckpointStatus.VALID),
                Map.of());
        GraphRestartReconciler reconciler = new GraphRestartReconciler();
        Result result = reconciler.reconcile(
                loaded,
                evidence,
                BASE_EPOCH_MS + (PHASE_FIRST.equals(phase) ? 1_000L : 2_000L));
        DurableGraphRecoveryRepository.ApplyReport report = repository.applyRecovery(
                result,
                BASE_EPOCH_MS + (PHASE_FIRST.equals(phase) ? 1_100L : 2_100L));
        PersistentRun reopened = repository.loadRequired(ids.planId);

        boolean waitingRecovered = result.getTargetGraphState() == GraphRunState.WAITING
                && reopened.getGraphState() == GraphRunState.WAITING
                && nodeState(reopened, "approval_node") == NodeRunState.WAITING;
        boolean executingReconciled = nodeState(reopened, "effect_node")
                        == NodeRunState.WAITING
                && hasDirective(result, DirectiveType.RECONCILE_EFFECT_NODE, "effect_node");
        boolean unknownEffectReconciled = reopened.getEffects().size() == 1
                && reopened.getEffects().get(0).getState() == EffectContract.STATE_UNKNOWN
                && hasDirective(
                        result,
                        DirectiveType.RECONCILE_EFFECT_STATUS,
                        ids.effectId);
        boolean undoRevalidated = reopened.getCompensations().size() == 1
                && reopened.getCompensations().get(0).getState()
                        == EffectContract.UNDO_REQUESTED
                && hasDirective(
                        result,
                        DirectiveType.REVALIDATE_UNDO,
                        ids.compensationId);
        boolean firstApplyVerified = PHASE_FIRST.equals(phase)
                && report.getChangedRowCount() == 2
                && report.isAuditInserted()
                && !report.isAuditReplayed();
        boolean replayVerified = PHASE_REPLAY.equals(phase)
                && report.getChangedRowCount() == 0
                && !report.isAuditInserted()
                && report.isAuditReplayed()
                && dao.countAuditEventsByType(
                        DurableGraphRecoveryRepository.AUDIT_GRAPH_RESTART_RECONCILED) == 1;
        boolean historicalDigestReplayVerified = !PHASE_REPLAY.equals(phase)
                || historicalDigestReplayVerified(
                        repository, dao, reconciler, reopened, ids, result);
        boolean checkpointMismatchStuck = checkpointMismatchStuck(ids, reconciler);
        boolean continueAfterRevalidate = continuationAfterRevalidate(ids, reconciler);
        boolean noDispatch = !result.isExecutorDispatchEnabled()
                && !report.isExecutorDispatchEnabled()
                && !result.isProductionAuthorized();

        session.summary = processToken(nonce);
        session.updatedAtWallMs = BASE_EPOCH_MS
                + (PHASE_FIRST.equals(phase) ? 1_200L : 2_200L);
        session.revision++;
        if (dao.updateSession(session) != 1) {
            throw new IllegalStateException("process-generation update conflict");
        }

        if (PHASE_FIRST.equals(phase)) {
            if (!waitingRecovered
                    || !executingReconciled
                    || !unknownEffectReconciled
                    || !undoRevalidated
                    || !firstApplyVerified
                    || !checkpointMismatchStuck
                    || !continueAfterRevalidate
                    || !noDispatch) {
                throw new IllegalStateException("first recovery phase did not pass");
            }
            Log.i(TAG, "nonce=" + nonce
                    + " graph_restart_first_recovery_complete=true"
                    + " graph_restart_process_generation_changed=true"
                    + " graph_restart_side_effect_count=0");
            return;
        }

        boolean api33Arm64 = Build.VERSION.SDK_INT == 33
                && Build.SUPPORTED_ABIS.length > 0
                && Build.SUPPORTED_ABIS[0].startsWith("arm64");
        boolean allVerified = waitingRecovered
                && executingReconciled
                && unknownEffectReconciled
                && undoRevalidated
                && replayVerified
                && historicalDigestReplayVerified
                && checkpointMismatchStuck
                && continueAfterRevalidate
                && processChanged
                && noDispatch
                && api33Arm64;
        if (!allVerified) {
            throw new IllegalStateException("replay recovery phase did not pass");
        }
        Log.i(TAG, "nonce=" + nonce
                + " graph_restart_probe_complete=true"
                + " graph_restart_reconciler_defined=true"
                + " graph_restart_room_v4_repository_verified=true"
                + " graph_restart_waiting_recovered=true"
                + " graph_restart_executing_reconciled=true"
                + " graph_restart_unknown_effect_reconciled=true"
                + " graph_restart_approval_undo_revalidation_verified=true"
                + " graph_restart_checkpoint_mismatch_stuck=true"
                + " graph_restart_continue_after_revalidate_verified=true"
                + " graph_restart_process_death_verified=true"
                + " graph_restart_idempotent_reopen_verified=true"
                + " graph_restart_audit_exactly_once_verified=true"
                + " graph_restart_historical_digest_replay_verified=true"
                + " graph_restart_side_effect_count=0"
                + " graph_restart_android13_arm64_verified=true"
                + " graph_restart_repository_implementation_available=true"
                + " graph_restart_runtime_wired=false"
                + " graph_restart_binder_published=false"
                + " graph_restart_executor_dispatch_enabled=false"
                + " graph_restart_effect_dispatch_enabled=false"
                + " graph_restart_production_wired=false"
                + " agent_graph_runtime_persistence_wired=false"
                + " production_effect_dispatch_enabled=false"
                + " hardware_accessed=false");
    }

    private static boolean historicalDigestReplayVerified(
            DurableGraphRecoveryRepository repository,
            RuntimeStateDao dao,
            GraphRestartReconciler reconciler,
            PersistentRun reopened,
            ProbeIds ids,
            Result originalResult) {
        Result alternate = reconciler.reconcile(
                reopened,
                new Evidence(
                        false,
                        Map.of(ids.effectCheckpoint, CheckpointStatus.VALID,
                                ids.approvalCheckpoint, CheckpointStatus.VALID),
                        Map.of(ids.effectId, EffectDeliveryStatus.CONFIRMED_APPLIED)),
                BASE_EPOCH_MS + 2_200L);
        DurableGraphRecoveryRepository.ApplyReport alternateReport =
                repository.applyRecovery(alternate, BASE_EPOCH_MS + 2_300L);
        DurableGraphRecoveryRepository.ApplyReport historicalReplay =
                repository.applyRecovery(originalResult, BASE_EPOCH_MS + 2_400L);
        return !alternate.getResultDigest().equals(originalResult.getResultDigest())
                && alternateReport.getChangedRowCount() == 0
                && alternateReport.isAuditInserted()
                && !alternateReport.isAuditReplayed()
                && historicalReplay.getChangedRowCount() == 0
                && !historicalReplay.isAuditInserted()
                && historicalReplay.isAuditReplayed()
                && dao.countAuditEventsByType(
                        DurableGraphRecoveryRepository.AUDIT_GRAPH_RESTART_RECONCILED) == 2;
    }

    private static boolean checkpointMismatchStuck(
            ProbeIds ids,
            GraphRestartReconciler reconciler) {
        Result result = reconciler.reconcile(
                durableRun(ids, GraphRunState.WAITING, NodeRunState.WAITING),
                new Evidence(
                        true,
                        Map.of(ids.effectCheckpoint, CheckpointStatus.MISMATCH,
                                ids.approvalCheckpoint, CheckpointStatus.VALID),
                        Map.of()),
                BASE_EPOCH_MS + 3_000L);
        return result.getTargetGraphState() == GraphRunState.STUCK
                && hasDirective(
                        result, DirectiveType.CHECKPOINT_MISMATCH, "effect_node")
                && !result.isExecutorDispatchEnabled();
    }

    private static boolean continuationAfterRevalidate(
            ProbeIds ids,
            GraphRestartReconciler reconciler) {
        PersistentRun control = new PersistentRun(
                ids.controlPlanId,
                ids.sessionId,
                2,
                GraphRunState.EXECUTING,
                sha("control-plan", ids.nonce),
                sha("context", ids.nonce),
                sha("manifest", ids.nonce),
                BASE_EPOCH_MS - 10_000L,
                BASE_EPOCH_MS - 1_000L,
                BASE_EPOCH_MS + 60_000L,
                List.of(new PersistentNode(
                        "context_node",
                        "context.capture",
                        NodeRunState.EXECUTING,
                        1,
                        BASE_EPOCH_MS + 20_000L,
                        "restart:context",
                        ids.controlCheckpoint,
                        sha("control-payload", ids.nonce),
                        BASE_EPOCH_MS - 1_000L)),
                List.of(),
                List.of());
        Result result = reconciler.reconcile(
                control,
                new Evidence(
                        true,
                        Map.of(ids.controlCheckpoint, CheckpointStatus.VALID),
                        Map.of()),
                BASE_EPOCH_MS + 3_000L);
        return result.getTargetGraphState() == GraphRunState.WAITING
                && result.getNodes().get(0).getTargetState() == NodeRunState.READY
                && result.getDirectives().isEmpty()
                && result.isContinuationAllowed()
                && !result.isExecutorDispatchEnabled();
    }

    private static PersistentRun durableRun(
            ProbeIds ids,
            GraphRunState graphState,
            NodeRunState effectState) {
        return new PersistentRun(
                ids.planId,
                ids.sessionId,
                1,
                graphState,
                sha("plan", ids.nonce),
                sha("context", ids.nonce),
                sha("manifest", ids.nonce),
                BASE_EPOCH_MS - 10_000L,
                BASE_EPOCH_MS - 1_000L,
                BASE_EPOCH_MS + 60_000L,
                List.of(
                        new PersistentNode(
                                "effect_node",
                                "effect.execute",
                                effectState,
                                1,
                                BASE_EPOCH_MS + 20_000L,
                                "restart:effect",
                                ids.effectCheckpoint,
                                sha("effect-payload", ids.nonce),
                                BASE_EPOCH_MS - 1_000L),
                        new PersistentNode(
                                "approval_node",
                                "approval.interrupt",
                                NodeRunState.WAITING,
                                1,
                                BASE_EPOCH_MS + 20_000L,
                                "restart:approval",
                                ids.approvalCheckpoint,
                                sha("approval-payload", ids.nonce),
                                BASE_EPOCH_MS - 1_000L)),
                List.of(new PersistentEffect(
                        ids.effectId,
                        1L,
                        ids.observationId,
                        ids.sessionId,
                        EffectContract.STATE_UNKNOWN,
                        EffectContract.SOURCE_SIMULATION,
                        1,
                        sha("target", ids.nonce),
                        sha("reported", ids.nonce),
                        sha("evidence", ids.nonce),
                        sha("observation", ids.nonce),
                        false,
                        BASE_EPOCH_MS - 2_000L)),
                List.of(new PersistentCompensation(
                        ids.compensationId,
                        ids.sessionId,
                        ids.effectId,
                        "restart:undo",
                        sha("before", ids.nonce),
                        sha("compensation", ids.nonce),
                        EffectContract.UNDO_REQUESTED,
                        BASE_EPOCH_MS + 30_000L,
                        BASE_EPOCH_MS - 9_000L,
                        BASE_EPOCH_MS - 1_000L)));
    }

    private static NodeRunState nodeState(PersistentRun run, String nodeId) {
        for (PersistentNode node : run.getNodes()) {
            if (nodeId.equals(node.getNodeId())) {
                return node.getState();
            }
        }
        throw new IllegalStateException("probe node is missing");
    }

    private static boolean hasDirective(
            Result result,
            DirectiveType type,
            String subjectId) {
        return result.getDirectives().stream().anyMatch(
                directive -> directive.getType() == type
                        && subjectId.equals(directive.getSubjectId()));
    }

    private static String processToken(String nonce) {
        return sha(
                "process",
                nonce,
                Integer.toString(Process.myPid()),
                Long.toString(PROCESS_BIRTH_ELAPSED_MS));
    }

    private static String sha(String domain, String... parts) {
        return DurableDigest.sha256(domain, parts);
    }

    private static String uuid(String domain, String nonce) {
        return UUID.nameUUIDFromBytes((domain + "\u0000" + nonce)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static final class ProbeIds {
        private final String nonce;
        private final String sessionId;
        private final String requestId;
        private final String planId;
        private final String controlPlanId;
        private final String effectId;
        private final String observationId;
        private final String compensationId;
        private final String effectCheckpoint;
        private final String approvalCheckpoint;
        private final String controlCheckpoint;

        private ProbeIds(String nonce) {
            this.nonce = nonce;
            this.sessionId = uuid("session", nonce);
            this.requestId = uuid("request", nonce);
            this.planId = uuid("plan", nonce);
            this.controlPlanId = uuid("control-plan", nonce);
            this.effectId = uuid("effect", nonce);
            this.observationId = uuid("observation", nonce);
            this.compensationId = uuid("compensation", nonce);
            this.effectCheckpoint = sha("effect-checkpoint", nonce);
            this.approvalCheckpoint = sha("approval-checkpoint", nonce);
            this.controlCheckpoint = sha("control-checkpoint", nonce);
        }
    }
}
