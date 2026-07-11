package com.centralbrain.runtime.persistence;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.effects.EffectDeliveryActivationGate;
import com.centralbrain.runtime.effects.EffectMaterialSource;
import com.centralbrain.runtime.effects.EmptyEffectMaterialSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class EffectDeliveryActivationProbeActivity extends Activity {
    private static final String TAG = "CbMaterialProbe";
    private static final String DATABASE_NAME = "central-brain-material-probe.db";
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
            AtomicLong clock = new AtomicLong(1_740_000_000_000L);
            AtomicInteger ids = new AtomicInteger();
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            DurableTaskRepository tasks = taskRepository(database, clock, ids);
            String taskId = runningTask(tasks);
            DurableEffectRepository effects = effectRepository(database, clock, ids);
            DurableEffectRepository.PrepareResult prepared = effects.prepare(
                    OWNER,
                    taskId,
                    "material-probe-effect",
                    DurableEffectRepository.EFFECT_TYPE_ACTION,
                    "climate.setTemperature",
                    PAYLOAD_DIGEST,
                    DurableEffectRepository.DESTINATION_UIB_ACTION,
                    ENVELOPE_DIGEST);
            DurableEffectRepository.Claim claim = effects.claimNext(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);

            EffectDeliveryActivationGate gate = new EffectDeliveryActivationGate();
            EmptyEffectMaterialSource emptySource = new EmptyEffectMaterialSource();
            EffectDeliveryActivationGate.Result current = gate.evaluate(
                    null,
                    emptySource,
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            boolean currentEmptyGateVerified = !current.isAllowed()
                    && current.hasBlocker(
                            EffectDeliveryActivationGate.Blocker.ADAPTER_MISSING)
                    && current.hasBlocker(
                            EffectDeliveryActivationGate.Blocker.MATERIAL_SOURCE_EMPTY)
                    && current.hasBlocker(
                            EffectDeliveryActivationGate.Blocker
                                    .MATERIAL_SOURCE_NOT_PRODUCTION)
                    && current.hasBlocker(
                            EffectDeliveryActivationGate.Blocker.MATERIAL_NOT_DURABLE)
                    && current.hasBlocker(
                            EffectDeliveryActivationGate.Blocker.MATERIAL_NOT_ENCRYPTED)
                    && current.hasBlocker(
                            EffectDeliveryActivationGate.Blocker.MATERIAL_INTEGRITY_UNBOUND)
                    && current.hasBlocker(
                            EffectDeliveryActivationGate.Blocker
                                    .MATERIAL_DELETE_UNSUPPORTED)
                    && current.hasBlocker(
                            EffectDeliveryActivationGate.Blocker.MATERIAL_RETENTION_INVALID);

            DeterministicEffectAdapter adapter = new DeterministicEffectAdapter(
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            DeterministicEffectMaterialSource testOnlySource =
                    new DeterministicEffectMaterialSource(
                            EffectMaterialSource.Assurance.TEST_ONLY);
            testOnlySource.register(claim.getSnapshot(), EFFECT_PAYLOAD, EFFECT_ENVELOPE);
            EffectDeliveryActivationGate.Result testOnly = gate.evaluate(
                    adapter,
                    testOnlySource,
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            boolean testOnlyRejected = !testOnly.isAllowed()
                    && testOnly.hasBlocker(
                            EffectDeliveryActivationGate.Blocker
                                    .MATERIAL_SOURCE_NOT_PRODUCTION);

            DeterministicEffectMaterialSource syntheticProductionSource =
                    new DeterministicEffectMaterialSource(
                            EffectMaterialSource.Assurance.PRODUCTION);
            syntheticProductionSource.register(
                    claim.getSnapshot(),
                    EFFECT_PAYLOAD,
                    EFFECT_ENVELOPE);
            EffectDeliveryActivationGate.Result syntheticGate = gate.evaluate(
                    adapter,
                    syntheticProductionSource,
                    DurableEffectRepository.DESTINATION_UIB_ACTION);
            boolean syntheticPositiveGateVerified = syntheticGate.isAllowed();

            database.close();
            database = CentralBrainDatabase.open(getApplicationContext(), DATABASE_NAME);
            effects = effectRepository(database, clock, ids);
            DurableEffectRepository.Snapshot reopened = effects.findOwned(
                    prepared.getSnapshot().getEffectId(),
                    OWNER);
            EffectAdapter.Invocation invocation = gate.resolveInvocation(
                    reopened,
                    effects.getMaxAttempts(),
                    adapter,
                    syntheticProductionSource);
            boolean reopenResolutionVerified =
                    invocation.getEffectId().equals(reopened.getEffectId())
                            && invocation.getIdempotencyToken().equals(
                                    reopened.getIdempotencyToken())
                            && invocation.getAttempt() == 1
                            && java.util.Arrays.equals(
                                    EFFECT_PAYLOAD,
                                    invocation.getCanonicalPayload())
                            && java.util.Arrays.equals(
                                    EFFECT_ENVELOPE,
                                    invocation.getCanonicalEnvelope());

            EffectMaterialSource.Material resolved = syntheticProductionSource.resolve(reopened);
            byte[] mutableCopy = resolved.getCanonicalPayload();
            mutableCopy[0] = 0;
            boolean defensiveCopyVerified = java.util.Arrays.equals(
                    EFFECT_PAYLOAD,
                    syntheticProductionSource.resolve(reopened).getCanonicalPayload());

            DeterministicEffectMaterialSource mismatchSource =
                    new DeterministicEffectMaterialSource(
                            EffectMaterialSource.Assurance.PRODUCTION);
            mismatchSource.register(
                    reopened,
                    "wrong-payload".getBytes(StandardCharsets.UTF_8),
                    EFFECT_ENVELOPE);
            boolean mismatchRejected = false;
            try {
                gate.resolveInvocation(
                        reopened,
                        effects.getMaxAttempts(),
                        adapter,
                        mismatchSource);
            } catch (IllegalArgumentException expected) {
                mismatchRejected = true;
            }

            DeterministicEffectMaterialSource missingSource =
                    new DeterministicEffectMaterialSource(
                            EffectMaterialSource.Assurance.PRODUCTION);
            boolean missingRejected = false;
            try {
                gate.resolveInvocation(
                        reopened,
                        effects.getMaxAttempts(),
                        adapter,
                        missingSource);
            } catch (EffectMaterialSource.MaterialUnavailableException expected) {
                missingRejected = true;
            }
            boolean emptyResolveBlocked = false;
            try {
                gate.resolveInvocation(
                        reopened,
                        effects.getMaxAttempts(),
                        adapter,
                        emptySource);
            } catch (EffectDeliveryActivationGate.ActivationBlockedException expected) {
                emptyResolveBlocked = expected.getBlockers().contains(
                        EffectDeliveryActivationGate.Blocker.MATERIAL_SOURCE_EMPTY);
            }

            DurableEffectRepository.Snapshot unchanged = effects.findOwned(
                    reopened.getEffectId(),
                    OWNER);
            boolean noSideEffectVerified = adapter.getApplyCount(
                    reopened.getIdempotencyToken()) == 0
                    && DurableEffectRepository.EFFECT_STATE_IN_FLIGHT.equals(
                            unchanged.getEffectState())
                    && database.runtimeStateDao().countAuditEventsByType(
                            DurableEffectRepository.AUDIT_EFFECT_APPLIED) == 0
                    && database.runtimeStateDao().countAuditEventsByType(
                            DurableEffectRepository.AUDIT_EFFECT_RETRY_SCHEDULED) == 0
                    && database.runtimeStateDao().countAuditEventsByType(
                            DurableEffectRepository.AUDIT_EFFECT_DEAD_LETTERED) == 0;
            boolean activationContractVerified = currentEmptyGateVerified
                    && testOnlyRejected
                    && syntheticPositiveGateVerified
                    && reopenResolutionVerified
                    && defensiveCopyVerified
                    && mismatchRejected
                    && missingRejected
                    && emptyResolveBlocked
                    && noSideEffectVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " material_probe_complete=true"
                    + " current_empty_material_gate_verified=" + currentEmptyGateVerified
                    + " test_only_material_rejected=" + testOnlyRejected
                    + " synthetic_positive_gate_verified="
                    + syntheticPositiveGateVerified
                    + " material_reopen_resolution_verified=" + reopenResolutionVerified
                    + " material_defensive_copy_verified=" + defensiveCopyVerified
                    + " material_digest_mismatch_rejected=" + mismatchRejected
                    + " material_missing_rejected=" + missingRejected
                    + " empty_material_resolution_blocked=" + emptyResolveBlocked
                    + " activation_gate_no_side_effect_verified=" + noSideEffectVerified
                    + " material_activation_contract_verified="
                    + activationContractVerified
                    + " production_effect_delivery_activation_allowed=false"
                    + " production_effect_material_source=empty"
                    + " production_effect_material_durable=false"
                    + " synthetic_material_source_process_only=true"
                    + " raw_effect_material_persisted=false"
                    + " effect_adapter_production_wired=false"
                    + " real_adapter_dispatch_enabled=false"
                    + " service_dispatch_triggered=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " material_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " production_effect_delivery_activation_allowed=false"
                    + " production_effect_material_source=empty"
                    + " production_effect_material_durable=false"
                    + " raw_effect_material_persisted=false"
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

    private static String runningTask(DurableTaskRepository repository) {
        DurableTaskRepository.Admission admission = repository.admit(
                OWNER,
                "material-probe-session",
                "material-probe-task",
                "material-probe-task",
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
                () -> "material-probe-" + ids.incrementAndGet());
    }

    private static DurableEffectRepository effectRepository(
            CentralBrainDatabase database,
            AtomicLong clock,
            AtomicInteger ids) {
        return new DurableEffectRepository(
                database,
                clock::get,
                () -> "material-probe-" + ids.incrementAndGet());
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
