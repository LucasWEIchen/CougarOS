package com.centralbrain.runtime.memory;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.atomic.AtomicLong;

public final class WorkingMemoryStoreProbeActivity extends Activity {
    private static final String TAG = "CbWorkingMemory";
    private static final String OWNER_A = "a".repeat(64);
    private static final String OWNER_B = "b".repeat(64);

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
            WorkingMemoryStore store = store(clock, 1, 2, 6, 5, 4, 4, 2, 20);
            WorkingMemoryStore.PutResult first = store.put(request(
                    OWNER_A, "session-a", "one", bytes(1, 2, 3, 4), 2, 10));
            WorkingMemoryStore.PutResult second = store.put(request(
                    OWNER_A, "session-a", "two", bytes(5, 6), 3, 10));
            boolean sessionScope = first.getOutcome() == WorkingMemoryStore.PutOutcome.CREATED
                    && second.getOutcome() == WorkingMemoryStore.PutOutcome.CREATED
                    && store.readSessionOwned(OWNER_A, "session-a", 2).size() == 2
                    && store.readSessionOwned(OWNER_B, "session-a", 2).isEmpty();
            boolean itemLimit = store.put(request(
                    OWNER_A, "session-a", "three", bytes(7), 1, 10)).getOutcome()
                    == WorkingMemoryStore.PutOutcome.ITEM_LIMIT;
            boolean byteLimit = store.put(request(
                    OWNER_A, "session-a", "two", bytes(5, 6, 7), 3, 10)).getOutcome()
                    == WorkingMemoryStore.PutOutcome.SESSION_BYTE_LIMIT;
            boolean tokenLimit = store.put(request(
                    OWNER_A, "session-a", "two", bytes(5, 6), 4, 10)).getOutcome()
                    == WorkingMemoryStore.PutOutcome.SESSION_TOKEN_LIMIT;
            WorkingMemoryStore.TerminalResult terminal =
                    store.terminateSessionOwned(OWNER_A, "session-a");
            WorkingMemoryStore.Snapshot terminalSnapshot = store.snapshot();
            boolean terminalCleanup = terminal.getOutcome()
                    == WorkingMemoryStore.TerminalOutcome.APPLIED
                    && terminal.getCleanedItemCount() == 2
                    && terminal.getCleanedByteCount() == 6
                    && terminal.getCleanedTokenCount() == 5
                    && terminalSnapshot.getActiveItemCount() == 0
                    && !terminalSnapshot.isRawPayloadRetained()
                    && store.put(request(
                            OWNER_A, "session-a", "blocked", bytes(8), 1, 10))
                            .getOutcome() == WorkingMemoryStore.PutOutcome.SESSION_TERMINAL;
            boolean zeroized = terminalSnapshot.getWipedByteCount() == 6;

            AtomicLong ttlClock = new AtomicLong(2_000L);
            WorkingMemoryStore ttlStore = store(
                    ttlClock, 1, 1, 4, 4, 4, 4, 1, 20);
            ttlStore.put(request(
                    OWNER_A, "session-ttl", "short", bytes(9, 10), 1, 10));
            ttlClock.set(2_010L);
            WorkingMemoryStore.Snapshot expired = ttlStore.snapshot();
            boolean ttl = expired.getExpiredItemCount() == 1
                    && expired.getActiveItemCount() == 0
                    && expired.getWipedByteCount() == 2
                    && !expired.isRawPayloadRetained();
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            boolean complete = sessionScope
                    && itemLimit
                    && byteLimit
                    && tokenLimit
                    && ttl
                    && terminalCleanup
                    && zeroized
                    && !store.isPersistentStorageWired()
                    && !store.isRuntimeWired()
                    && !store.isModelContextPublicationEnabled()
                    && !store.isTokenCountVerifiedByModelTokenizer()
                    && !store.isContentLoggingEnabled()
                    && !store.isHardwareAccessed()
                    && android13Arm64;

            Log.i(TAG, "nonce=" + nonce
                    + " working_memory_store_probe_complete=" + complete
                    + " working_memory_session_scope_verified=" + sessionScope
                    + " working_memory_ttl_verified=" + ttl
                    + " working_memory_item_limit_verified=" + itemLimit
                    + " working_memory_byte_limit_verified=" + byteLimit
                    + " working_memory_token_limit_verified=" + tokenLimit
                    + " working_memory_terminal_cleanup_verified=" + terminalCleanup
                    + " working_memory_payload_zeroized_on_cleanup=" + zeroized
                    + " working_memory_android13_arm64_verified=" + android13Arm64
                    + " working_memory_process_local=true"
                    + " working_memory_persistence_wired=false"
                    + " working_memory_runtime_wired=false"
                    + " working_memory_model_context_published=false"
                    + " working_memory_tokenizer_verified=false"
                    + " working_memory_content_logged=false"
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
                    + " working_memory_store_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " working_memory_runtime_wired=false"
                    + " model_invoked=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static WorkingMemoryStore store(
            AtomicLong clock,
            int sessions,
            int items,
            int sessionBytes,
            int sessionTokens,
            int itemBytes,
            int itemTokens,
            int readItems,
            long ttlMs) {
        return new WorkingMemoryStore(
                new WorkingMemoryStore.Limits(
                        sessions,
                        items,
                        sessionBytes,
                        sessionTokens,
                        itemBytes,
                        itemTokens,
                        2,
                        readItems,
                        ttlMs),
                clock::get);
    }

    private static WorkingMemoryStore.PutRequest request(
            String owner,
            String session,
            String item,
            byte[] payload,
            int tokenCount,
            long ttlMs) {
        return WorkingMemoryStore.PutRequest.fromRuntimePolicy(
                owner,
                session,
                item,
                "central.working-memory.v1",
                payload,
                tokenCount,
                ttlMs);
    }

    private static byte[] bytes(int... values) {
        byte[] output = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            output[index] = (byte) values[index];
        }
        return output;
    }
}
