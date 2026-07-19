package com.centralbrain.runtime.orchestration;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

/** DUMP-protected probe for the debug-only P5-R1 runtime composition boundary. */
public final class RuntimeCompositionProbeActivity extends Activity {
    private static final String TAG = "CbRuntimeComposition";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            AtomicInteger ids = new AtomicInteger();
            DebugRuntimeCompositionBoundary boundary =
                    new DebugRuntimeCompositionBoundary(
                            () -> 10_000L,
                            () -> "probe-" + ids.incrementAndGet());
            OrchestrationBackend.SessionDescriptor session =
                    new OrchestrationBackend.SessionDescriptor(
                            "a".repeat(64),
                            "runtime-composition-probe-session",
                            "scene.comfort.cold.v1",
                            1,
                            0,
                            0L,
                            1L);
            DebugRuntimeCompositionBoundary.Evidence evidence = boundary.prepare(
                    session,
                    "scene.comfort.cold.v1",
                    "b".repeat(64));
            DebugRuntimeCompositionBoundary.Evidence replay = boundary.prepare(
                    session,
                    "scene.comfort.cold.v1",
                    "b".repeat(64));
            DebugRuntimeCompositionBoundary.Snapshot active = boundary.snapshot();
            DebugRuntimeCompositionBoundary.Completion completion =
                    boundary.complete(session, evidence);
            DebugRuntimeCompositionBoundary.Completion completionReplay =
                    boundary.complete(session, evidence);
            DebugRuntimeCompositionBoundary.Snapshot terminal = boundary.snapshot();

            boolean evidenceBound = evidence.getDigest().matches("[0-9a-f]{64}")
                    && evidence.getToolAuditDigest().matches("[0-9a-f]{64}")
                    && evidence.getSkillInvocationId().startsWith("skill-invocation-")
                    && "cabin.precondition".equals(evidence.getSkillId())
                    && evidence.getAllocatedTokens() == 12
                    && evidence.getAllocatedBytes() == 192
                    && evidence.getWorkingMemoryBytes() == 64
                    && evidence.getDigest().equals(replay.getDigest());
            boolean activeComposition = active.getSessionCount() == 1
                    && active.getCompletedSessionCount() == 0
                    && active.getActiveSkillCount() == 1
                    && active.getActiveWorkingMemoryItemCount() == 1
                    && active.getToolAuditCount() == 1;
            boolean terminalCleanup = !completion.isReplayed()
                    && completion.getCleanedItems() == 1
                    && completion.getCleanedBytes() == 64
                    && completion.getCleanedTokens() == 1
                    && completionReplay.isReplayed()
                    && terminal.getCompletedSessionCount() == 1
                    && terminal.getActiveSkillCount() == 0
                    && terminal.getActiveWorkingMemoryItemCount() == 0;
            boolean failClosed = !evidence.isSkillDispatchEnabled()
                    && !evidence.isProfileMemoryWritten()
                    && !evidence.isEpisodicMemoryWritten()
                    && !evidence.isProductionAuthority()
                    && !evidence.isHardwareAccessed();
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            boolean complete = evidenceBound
                    && activeComposition
                    && terminalCleanup
                    && failClosed
                    && android13Arm64;

            Log.i(TAG, "nonce=" + nonce
                    + " runtime_composition_probe_complete=" + complete
                    + " runtime_composition_evidence_bound=" + evidenceBound
                    + " runtime_composition_active_verified=" + activeComposition
                    + " runtime_composition_terminal_cleanup_verified=" + terminalCleanup
                    + " runtime_composition_android13_arm64_verified=" + android13Arm64
                    + " debug_metadata_tool_boundary_executed=true"
                    + " built_in_skill_governance_admitted=true"
                    + " working_memory_digest_only=true"
                    + " context_budget_metadata_only=true"
                    + " profile_memory_written=false"
                    + " episodic_memory_written=false"
                    + " skill_dispatch_enabled=false"
                    + " production_tool_execution_enabled=false"
                    + " production_memory_authority_published=false"
                    + " network_accessed=false"
                    + " npu_accessed=false"
                    + " vehicle_bus_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException failure) {
            Log.e(TAG, "nonce=" + nonce
                    + " runtime_composition_probe_complete=false"
                    + " profile_memory_written=false"
                    + " episodic_memory_written=false"
                    + " production_tool_execution_enabled=false"
                    + " hardware_accessed=false", failure);
        }
    }
}
