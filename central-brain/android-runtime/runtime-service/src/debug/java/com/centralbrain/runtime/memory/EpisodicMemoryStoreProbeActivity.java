package com.centralbrain.runtime.memory;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class EpisodicMemoryStoreProbeActivity extends Activity {
    private static final String TAG = "CbEpisodicMemory";
    private static final String OWNER_A = "a".repeat(64);
    private static final String OWNER_B = "b".repeat(64);
    private static final String CATALOG_DIGEST = "c".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            AtomicLong clock = new AtomicLong(1_000L);
            AtomicBoolean eraseAllowed = new AtomicBoolean(false);
            EpisodicMemoryStore store = store(clock, eraseAllowed, 2, 1, 20, 60);
            EpisodicMemoryStore.StoreResult first = store.store(
                    request(OWNER_A, "episode-a", 10, 2, 2, 900, 950));
            boolean summaryOnly = first.getOutcome()
                    == EpisodicMemoryStore.StoreOutcome.STORED
                    && first.getRecord().getResultKind()
                    == EpisodicMemoryStore.ResultKind.SUCCEEDED
                    && first.getRecord().getCompletedActionCount() == 2
                    && !store.isRawContinuousSignalAccepted()
                    && !store.isRawContinuousSignalRetained()
                    && !store.isArbitraryPayloadAccepted();
            boolean ownerIsolation = store.readOwner(
                    OWNER_A, 1, readEvidence(OWNER_A)).getRecords().size() == 1
                    && store.readOwner(
                            OWNER_B, 1, readEvidence(OWNER_B)).getRecords().isEmpty();
            boolean readFailClosed = store.readOwner(
                    OWNER_A,
                    1,
                    new EpisodicMemoryStore.ReadEvidence(
                            OWNER_B, "read-wrong-owner", 900, 2_000)).getOutcome()
                    == EpisodicMemoryStore.ReadOutcome.AUTHORIZATION_DENIED;
            boolean ownerCapacity = store.store(
                    request(OWNER_A, "episode-b", 10, 1, 1, 900, 950)).getOutcome()
                    == EpisodicMemoryStore.StoreOutcome.OWNER_CAPACITY;
            boolean policyFailClosed = store.store(requestWithEvidence(
                    OWNER_B,
                    "episode-policy",
                    10,
                    new EpisodicMemoryStore.StoragePolicyEvidence(
                            OWNER_B, "episode-policy", "expired", 800, 1_000)))
                    .getOutcome() == EpisodicMemoryStore.StoreOutcome.POLICY_DENIED;
            boolean eraseFailClosed = store.eraseEpisode(
                    OWNER_A,
                    "episode-a",
                    eraseEpisode(OWNER_A, "episode-a")).getOutcome()
                    == EpisodicMemoryStore.EraseOutcome.AUTHORIZATION_DENIED;
            eraseAllowed.set(true);
            boolean erase = store.eraseEpisode(
                    OWNER_A,
                    "episode-a",
                    eraseEpisode(OWNER_A, "episode-a")).getOutcome()
                    == EpisodicMemoryStore.EraseOutcome.ERASED
                    && store.snapshot().getErasedCount() == 1;

            AtomicLong retentionClock = new AtomicLong(2_000L);
            EpisodicMemoryStore retentionStore = store(
                    retentionClock, new AtomicBoolean(true), 2, 2, 20, 60);
            retentionStore.store(requestAt(
                    OWNER_A, "episode-retention", 10, 1_900, 1_950, 1_900, 3_000));
            retentionClock.set(2_010L);
            boolean retention = retentionStore.snapshot().getActiveRecordCount() == 0
                    && retentionStore.snapshot().getExpiredCount() == 1;
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            boolean complete = summaryOnly
                    && ownerIsolation
                    && readFailClosed
                    && ownerCapacity
                    && policyFailClosed
                    && eraseFailClosed
                    && erase
                    && retention
                    && !store.isProductionStoragePolicyAuthorityWired()
                    && !store.isProductionReadAuthorityWired()
                    && !store.isProductionEraseAuthorityWired()
                    && !store.isPersistentStorageWired()
                    && !store.isRuntimeWired()
                    && !store.isModelContextPublicationEnabled()
                    && !store.isContentLoggingEnabled()
                    && !store.isHardwareAccessed()
                    && android13Arm64;

            Log.i(TAG, "nonce=" + nonce
                    + " episodic_memory_store_probe_complete=" + complete
                    + " episodic_memory_summary_result_only_verified=" + summaryOnly
                    + " episodic_memory_owner_isolation_verified=" + ownerIsolation
                    + " episodic_memory_read_fail_closed=" + readFailClosed
                    + " episodic_memory_policy_fail_closed=" + policyFailClosed
                    + " episodic_memory_retention_verified=" + retention
                    + " episodic_memory_capacity_verified=" + ownerCapacity
                    + " episodic_memory_erase_verified=" + erase
                    + " episodic_memory_erase_fail_closed=" + eraseFailClosed
                    + " episodic_memory_android13_arm64_verified=" + android13Arm64
                    + " episodic_memory_process_local=true"
                    + " episodic_memory_raw_continuous_signal_stored=false"
                    + " episodic_memory_arbitrary_payload_stored=false"
                    + " episodic_memory_persistence_wired=false"
                    + " episodic_memory_runtime_wired=false"
                    + " episodic_memory_model_context_published=false"
                    + " episodic_memory_production_policy_authority_wired=false"
                    + " episodic_memory_production_read_authority_wired=false"
                    + " episodic_memory_production_erase_authority_wired=false"
                    + " episodic_memory_content_logged=false"
                    + " graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_readback_accessed=false"
                    + " model_invoked=false"
                    + " npu_accessed=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " episodic_memory_store_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " episodic_memory_runtime_wired=false"
                    + " model_invoked=false"
                    + " hardware_accessed=false");
        }
    }

    private static EpisodicMemoryStore store(
            AtomicLong clock,
            AtomicBoolean eraseAllowed,
            int records,
            int ownerRecords,
            long retentionMs,
            long durationMs) {
        return EpisodicMemoryStore.createForContractTest(
                new EpisodicMemoryStore.Limits(
                        records, ownerRecords, ownerRecords, retentionMs, durationMs),
                clock::get,
                reference -> reference.getCatalogDigest().equals(CATALOG_DIGEST),
                (evidence, reference, now) -> true,
                (evidence, now) -> true,
                (evidence, now) -> eraseAllowed.get());
    }

    private static EpisodicMemoryStore.RecordRequest request(
            String owner,
            String episode,
            long retentionMs,
            int plannedActions,
            int completedActions,
            long startedAt,
            long finishedAt) {
        return requestAt(
                owner,
                episode,
                retentionMs,
                startedAt,
                finishedAt,
                900,
                2_000,
                plannedActions,
                completedActions);
    }

    private static EpisodicMemoryStore.RecordRequest requestAt(
            String owner,
            String episode,
            long retentionMs,
            long startedAt,
            long finishedAt,
            long policyIssuedAt,
            long policyExpiresAt) {
        return requestAt(
                owner,
                episode,
                retentionMs,
                startedAt,
                finishedAt,
                policyIssuedAt,
                policyExpiresAt,
                1,
                1);
    }

    private static EpisodicMemoryStore.RecordRequest requestAt(
            String owner,
            String episode,
            long retentionMs,
            long startedAt,
            long finishedAt,
            long policyIssuedAt,
            long policyExpiresAt,
            int plannedActions,
            int completedActions) {
        return requestWithEvidence(
                owner,
                episode,
                retentionMs,
                new EpisodicMemoryStore.StoragePolicyEvidence(
                        owner, episode, "policy-" + episode, policyIssuedAt, policyExpiresAt),
                startedAt,
                finishedAt,
                plannedActions,
                completedActions);
    }

    private static EpisodicMemoryStore.RecordRequest requestWithEvidence(
            String owner,
            String episode,
            long retentionMs,
            EpisodicMemoryStore.StoragePolicyEvidence evidence) {
        return requestWithEvidence(owner, episode, retentionMs, evidence, 900, 950, 1, 1);
    }

    private static EpisodicMemoryStore.RecordRequest requestWithEvidence(
            String owner,
            String episode,
            long retentionMs,
            EpisodicMemoryStore.StoragePolicyEvidence evidence,
            long startedAt,
            long finishedAt,
            int plannedActions,
            int completedActions) {
        return EpisodicMemoryStore.RecordRequest.fromScenarioResult(
                owner,
                episode,
                new EpisodicMemoryStore.ScenarioReference("fatigue-care", CATALOG_DIGEST),
                EpisodicMemoryStore.TriggerKind.EXPLICIT_USER_INTENT,
                EpisodicMemoryStore.ResultKind.SUCCEEDED,
                EpisodicMemoryStore.OutcomeCode.COMPLETED,
                plannedActions,
                completedActions,
                startedAt,
                finishedAt,
                retentionMs,
                evidence);
    }

    private static EpisodicMemoryStore.EraseEvidence eraseEpisode(
            String owner,
            String episode) {
        return new EpisodicMemoryStore.EraseEvidence(
                EpisodicMemoryStore.EraseOperation.EPISODE,
                owner,
                episode,
                "erase-" + episode,
                900,
                2_000);
    }

    private static EpisodicMemoryStore.ReadEvidence readEvidence(String owner) {
        return new EpisodicMemoryStore.ReadEvidence(
                owner,
                "read-owner",
                900,
                3_000);
    }
}
