package com.centralbrain.runtime.persistence;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.effects.EffectAdapterContract;
import com.centralbrain.runtime.effects.EffectStatusReconciler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class EffectAdapterContractProbeActivity extends Activity {
    private static final String TAG = "CbAdapterProbe";
    private static final String DATABASE_NAME = "central-brain-adapter-probe.db";
    private static final String OWNER = repeat("a", 64);
    private static final String TASK_PAYLOAD = repeat("b", 64);
    private static final String CHECKPOINT = repeat("c", 64);
    private static final byte[] EFFECT_PAYLOAD =
            "temperature=22".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EFFECT_ENVELOPE =
            "{action:climate.setTemperature,value:22}".getBytes(StandardCharsets.UTF_8);
    private static final String PAYLOAD_DIGEST = sha256(EFFECT_PAYLOAD);
    private static final String ENVELOPE_DIGEST = sha256(EFFECT_ENVELOPE);

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
            AtomicLong clock = new AtomicLong(1_730_000_000_000L);
            AtomicInteger ids = new AtomicInteger();
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            DurableTaskRepository tasks = taskRepository(database, clock, ids);
            String taskId = runningTask(tasks);
            DurableEffectRepository effects = effectRepository(database, clock, ids);
            DeterministicEffectAdapter adapter = new DeterministicEffectAdapter(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);

            boolean contractVerified = EffectAdapterContract.requireSafe(
                    adapter,
                    DurableEffectRepository.DESTINATION_UIB_ACTION) != null;
            boolean unsafeAdapterRejected = false;
            try {
                EffectAdapterContract.requireSafe(
                        unsafeAdapter(),
                        DurableEffectRepository.DESTINATION_UIB_ACTION);
            } catch (EffectAdapterContract.UnsafeAdapterException expected) {
                unsafeAdapterRejected = true;
            }
            boolean destinationMismatchRejected = false;
            try {
                EffectAdapterContract.requireSafe(
                        adapter,
                        DurableEffectRepository.DESTINATION_SOA_OPERATION);
            } catch (EffectAdapterContract.UnsafeAdapterException expected) {
                destinationMismatchRejected = true;
            }

            DurableEffectRepository.PrepareResult afterApply = prepare(
                    effects,
                    taskId,
                    "crash-after-apply");
            DurableEffectRepository.Claim afterApplyClaim = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            EffectAdapter.Invocation afterInvocation = invocation(afterApplyClaim, effects);
            EffectAdapter.ApplyResult firstApply = EffectAdapterContract.requireApplyResultMatches(
                    afterInvocation,
                    adapter.apply(afterInvocation));
            EffectAdapter.ApplyResult duplicateApply =
                    EffectAdapterContract.requireApplyResultMatches(
                            afterInvocation,
                            adapter.apply(afterInvocation));
            boolean duplicateApplyIdempotent =
                    firstApply.getState() == EffectAdapter.ApplyState.APPLIED
                    && duplicateApply.getState() == EffectAdapter.ApplyState.APPLIED
                    && firstApply.getEvidenceDigest().equals(
                            duplicateApply.getEvidenceDigest())
                    && adapter.getApplyCount(afterInvocation.getIdempotencyToken()) == 1;
            EffectAdapter.StatusResult appliedStatus =
                    EffectAdapterContract.requireStatusResultMatches(
                            afterInvocation.getIdempotencyToken(),
                            adapter.queryStatus(afterInvocation.getIdempotencyToken()));
            boolean statusMatchesApplyResult =
                    appliedStatus.getState() == EffectAdapter.DeliveryState.APPLIED
                            && firstApply.getEvidenceDigest().equals(
                                    appliedStatus.getEvidenceDigest());

            database.close();
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            effects = effectRepository(database, clock, ids);
            DurableEffectRepository.Snapshot afterReopen = effects.findOwned(
                    afterApply.getSnapshot().getEffectId(),
                    OWNER);
            EffectStatusReconciler reconciler = new EffectStatusReconciler(effects);
            EffectStatusReconciler.Result afterResult = reconciler.reconcile(
                    afterReopen,
                    adapter,
                    0);
            EffectStatusReconciler.Result afterReplay = reconciler.reconcile(
                    afterReopen,
                    adapter,
                    0);
            DurableEffectRepository.Snapshot afterTerminal = effects.findOwned(
                    afterApply.getSnapshot().getEffectId(),
                    OWNER);
            boolean crashAfterApplyReconciled =
                    afterResult.getDecision() == EffectStatusReconciler.Decision.APPLIED
                            && afterResult.getMutationOutcome()
                                    == DurableEffectRepository.MutationOutcome.APPLIED
                            && afterReplay.isReplay()
                            && DurableEffectRepository.EFFECT_STATE_APPLIED.equals(
                                    afterTerminal.getEffectState())
                            && DurableEffectRepository.OUTBOX_STATE_DELIVERED.equals(
                                    afterTerminal.getOutboxState())
                            && adapter.getApplyCount(afterInvocation.getIdempotencyToken()) == 1;

            DurableEffectRepository.PrepareResult beforeApply = prepare(
                    effects,
                    taskId,
                    "crash-before-apply");
            DurableEffectRepository.Claim beforeClaim = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            database.close();
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            effects = effectRepository(database, clock, ids);
            DurableEffectRepository.Snapshot beforeReopen = effects.findOwned(
                    beforeApply.getSnapshot().getEffectId(),
                    OWNER);
            reconciler = new EffectStatusReconciler(effects);
            EffectStatusReconciler.Result beforeRetry = reconciler.reconcile(
                    beforeReopen,
                    adapter,
                    0);
            EffectStatusReconciler.Result beforeRetryReplay = reconciler.reconcile(
                    beforeReopen,
                    adapter,
                    0);
            DurableEffectRepository.Claim beforeSecondClaim = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            EffectAdapter.Invocation beforeInvocation = invocation(beforeSecondClaim, effects);
            EffectAdapter.ApplyResult beforeApplied =
                    EffectAdapterContract.requireApplyResultMatches(
                            beforeInvocation,
                            adapter.apply(beforeInvocation));
            if (beforeApplied.getState() != EffectAdapter.ApplyState.APPLIED) {
                throw new IllegalStateException("debug adapter did not apply retry invocation");
            }
            effects.recordSuccess(
                    beforeInvocation.getEffectId(),
                    beforeInvocation.getOutboxId(),
                    OWNER,
                    beforeInvocation.getAttempt(),
                    beforeApplied.getEvidenceDigest());
            DurableEffectRepository.Snapshot beforeTerminal = effects.findOwned(
                    beforeApply.getSnapshot().getEffectId(),
                    OWNER);
            boolean crashBeforeApplyRetried = beforeClaim != null
                    && beforeRetry.getDecision()
                            == EffectStatusReconciler.Decision.RETRY_SCHEDULED
                    && beforeRetry.getMutationOutcome()
                            == DurableEffectRepository.MutationOutcome.APPLIED
                    && beforeRetryReplay.isReplay()
                    && beforeSecondClaim != null
                    && beforeSecondClaim.getSnapshot().getAttemptCount() == 2
                    && adapter.getApplyCount(beforeInvocation.getIdempotencyToken()) == 1
                    && DurableEffectRepository.EFFECT_STATE_APPLIED.equals(
                            beforeTerminal.getEffectState());

            DurableEffectRepository.PrepareResult unknown = prepare(
                    effects,
                    taskId,
                    "unknown-status");
            DurableEffectRepository.Claim unknownClaim = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            String unknownToken = unknownClaim.getSnapshot().getIdempotencyToken();
            adapter.setStatusUnavailable(unknownToken, true);
            reconciler = new EffectStatusReconciler(effects);
            EffectStatusReconciler.Result deferred = reconciler.reconcile(
                    unknownClaim.getSnapshot(),
                    adapter,
                    0);
            DurableEffectRepository.Snapshot deferredSnapshot = effects.findOwned(
                    unknown.getSnapshot().getEffectId(),
                    OWNER);
            boolean unavailableDeferred =
                    deferred.getDecision() == EffectStatusReconciler.Decision.DEFERRED
                            && deferred.getMutationOutcome() == null
                            && DurableEffectRepository.EFFECT_STATE_IN_FLIGHT.equals(
                                    deferredSnapshot.getEffectState());
            adapter.setStatusUnavailable(unknownToken, false);
            adapter.setStatus(unknownToken, EffectAdapter.DeliveryState.UNKNOWN);
            EffectStatusReconciler.Result unknownDeadLetter = reconciler.reconcile(
                    unknownClaim.getSnapshot(),
                    adapter,
                    0);
            EffectStatusReconciler.Result unknownReplay = reconciler.reconcile(
                    unknownClaim.getSnapshot(),
                    adapter,
                    0);
            DurableEffectRepository.Snapshot unknownTerminal = effects.findOwned(
                    unknown.getSnapshot().getEffectId(),
                    OWNER);
            boolean unknownStatusDeadLettered = unavailableDeferred
                    && unknownDeadLetter.getDecision()
                            == EffectStatusReconciler.Decision.DEAD_LETTERED
                    && unknownDeadLetter.getMutationOutcome()
                            == DurableEffectRepository.MutationOutcome.APPLIED
                    && unknownReplay.isReplay()
                    && DurableEffectRepository.EFFECT_STATE_FAILED.equals(
                            unknownTerminal.getEffectState())
                    && DurableEffectRepository.OUTBOX_STATE_DEAD_LETTER.equals(
                            unknownTerminal.getOutboxState());

            DurableEffectRepository.PrepareResult finalNotApplied = prepare(
                    effects,
                    taskId,
                    "final-not-applied");
            DurableEffectRepository.Claim finalOne = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            String finalToken = finalOne.getSnapshot().getIdempotencyToken();
            String notAppliedDigest = adapter.queryStatus(finalToken).getEvidenceDigest();
            effects.scheduleRetry(
                    finalOne.getSnapshot().getEffectId(),
                    finalOne.getSnapshot().getOutboxId(),
                    OWNER,
                    1,
                    0,
                    notAppliedDigest);
            DurableEffectRepository.Claim finalTwo = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            effects.scheduleRetry(
                    finalTwo.getSnapshot().getEffectId(),
                    finalTwo.getSnapshot().getOutboxId(),
                    OWNER,
                    2,
                    0,
                    notAppliedDigest);
            DurableEffectRepository.Claim finalThree = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            database.close();
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            effects = effectRepository(database, clock, ids);
            DurableEffectRepository.Snapshot finalReopen = effects.findOwned(
                    finalNotApplied.getSnapshot().getEffectId(),
                    OWNER);
            reconciler = new EffectStatusReconciler(effects);
            EffectStatusReconciler.Result finalDeadLetter = reconciler.reconcile(
                    finalReopen,
                    adapter,
                    0);
            EffectStatusReconciler.Result finalReplay = reconciler.reconcile(
                    finalReopen,
                    adapter,
                    0);
            DurableEffectRepository.Snapshot finalTerminal = effects.findOwned(
                    finalNotApplied.getSnapshot().getEffectId(),
                    OWNER);
            boolean finalNotAppliedDeadLettered = finalThree != null
                    && finalThree.getSnapshot().getAttemptCount() == 3
                    && finalDeadLetter.getDecision()
                            == EffectStatusReconciler.Decision.DEAD_LETTERED
                    && finalDeadLetter.getMutationOutcome()
                            == DurableEffectRepository.MutationOutcome.APPLIED
                    && finalReplay.isReplay()
                    && DurableEffectRepository.EFFECT_STATE_FAILED.equals(
                            finalTerminal.getEffectState());

            RuntimeStateDao dao = database.runtimeStateDao();
            boolean terminalCountsVerified = dao.countPendingEffects() == 4
                    && dao.countOutboxRows() == 4
                    && dao.countPendingEffectsInState(
                            DurableEffectRepository.EFFECT_STATE_APPLIED) == 2
                    && dao.countPendingEffectsInState(
                            DurableEffectRepository.EFFECT_STATE_FAILED) == 2
                    && dao.countOutboxRowsInState(
                            DurableEffectRepository.OUTBOX_STATE_DELIVERED) == 2
                    && dao.countOutboxRowsInState(
                            DurableEffectRepository.OUTBOX_STATE_DEAD_LETTER) == 2
                    && dao.countAuditEventsByType(
                            DurableEffectRepository.AUDIT_EFFECT_PREPARED) == 4
                    && dao.countAuditEventsByType(
                            DurableEffectRepository.AUDIT_EFFECT_CLAIMED) == 7
                    && dao.countAuditEventsByType(
                            DurableEffectRepository.AUDIT_EFFECT_RETRY_SCHEDULED) == 3
                    && dao.countAuditEventsByType(
                            DurableEffectRepository.AUDIT_EFFECT_APPLIED) == 2
                    && dao.countAuditEventsByType(
                            DurableEffectRepository.AUDIT_EFFECT_DEAD_LETTERED) == 2;
            boolean faultMatrixVerified = contractVerified
                    && unsafeAdapterRejected
                    && destinationMismatchRejected
                    && duplicateApplyIdempotent
                    && statusMatchesApplyResult
                    && crashAfterApplyReconciled
                    && crashBeforeApplyRetried
                    && unknownStatusDeadLettered
                    && finalNotAppliedDeadLettered
                    && terminalCountsVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " adapter_probe_complete=true"
                    + " effect_adapter_contract_verified=" + contractVerified
                    + " unsafe_adapter_rejected=" + unsafeAdapterRejected
                    + " adapter_destination_mismatch_rejected="
                    + destinationMismatchRejected
                    + " adapter_duplicate_apply_idempotent=" + duplicateApplyIdempotent
                    + " adapter_status_matches_apply_result=" + statusMatchesApplyResult
                    + " adapter_crash_after_apply_reconciled="
                    + crashAfterApplyReconciled
                    + " adapter_crash_before_apply_retried="
                    + crashBeforeApplyRetried
                    + " adapter_status_unavailable_deferred=" + unavailableDeferred
                    + " adapter_unknown_status_dead_lettered="
                    + unknownStatusDeadLettered
                    + " adapter_final_not_applied_dead_lettered="
                    + finalNotAppliedDeadLettered
                    + " adapter_terminal_counts_verified=" + terminalCountsVerified
                    + " adapter_fault_matrix_verified=" + faultMatrixVerified
                    + " transient_effect_material_durable=false"
                    + " effect_adapter_production_wired=false"
                    + " real_adapter_dispatch_enabled=false"
                    + " service_dispatch_triggered=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " adapter_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " transient_effect_material_durable=false"
                    + " effect_adapter_production_wired=false"
                    + " real_adapter_dispatch_enabled=false"
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

    private static DurableEffectRepository.PrepareResult prepare(
            DurableEffectRepository repository,
            String taskId,
            String key) {
        return repository.prepare(
                OWNER,
                taskId,
                key,
                DurableEffectRepository.EFFECT_TYPE_ACTION,
                "climate.setTemperature",
                PAYLOAD_DIGEST,
                DurableEffectRepository.DESTINATION_UIB_ACTION,
                ENVELOPE_DIGEST);
    }

    private static EffectAdapter.Invocation invocation(
            DurableEffectRepository.Claim claim,
            DurableEffectRepository repository) {
        EffectAdapter.Invocation invocation = new EffectAdapter.Invocation(
                claim.getSnapshot().getEffectId(),
                claim.getSnapshot().getOutboxId(),
                claim.getSnapshot().getIdempotencyToken(),
                claim.getSnapshot().getDestination(),
                claim.getSnapshot().getActionId(),
                claim.getSnapshot().getPayloadDigest(),
                claim.getSnapshot().getEnvelopeDigest(),
                claim.getSnapshot().getAttemptCount(),
                repository.getMaxAttempts(),
                EFFECT_PAYLOAD,
                EFFECT_ENVELOPE);
        return EffectAdapterContract.requireInvocationMatches(
                claim.getSnapshot(),
                invocation,
                repository.getMaxAttempts());
    }

    private static String runningTask(DurableTaskRepository repository) {
        DurableTaskRepository.Admission admission = repository.admit(
                OWNER,
                "adapter-probe-session",
                "adapter-probe-task",
                "adapter-probe-task",
                TASK_PAYLOAD);
        repository.transition(
                admission.getTaskId(),
                OWNER,
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
                () -> "adapter-probe-" + ids.incrementAndGet());
    }

    private static DurableEffectRepository effectRepository(
            CentralBrainDatabase database,
            AtomicLong clock,
            AtomicInteger ids) {
        return new DurableEffectRepository(
                database,
                clock::get,
                () -> "adapter-probe-" + ids.incrementAndGet());
    }

    private static EffectAdapter unsafeAdapter() {
        return new EffectAdapter() {
            @Override
            public Descriptor descriptor() {
                return new Descriptor(
                        "debug.unsafe.effect",
                        DurableEffectRepository.DESTINATION_UIB_ACTION,
                        IdempotencyMode.TOKEN_DEDUPLICATED,
                        true,
                        true,
                        StatusConsistency.EVENTUAL,
                        1_000);
            }

            @Override
            public ApplyResult apply(Invocation invocation) {
                throw new AssertionError("unsafe adapter must not be invoked");
            }

            @Override
            public StatusResult queryStatus(String idempotencyToken) {
                throw new AssertionError("unsafe adapter must not be queried");
            }
        };
    }

    private static String sha256(byte[] value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder output = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                output.append(String.format("%02x", current & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String repeat(String value, int count) {
        return String.join("", java.util.Collections.nCopies(count, value));
    }
}
