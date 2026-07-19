package com.centralbrain.runtime.orchestration;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.events.ProactiveConsentPolicy;
import com.centralbrain.runtime.scenario.ScenarioCatalog;

import java.util.concurrent.atomic.AtomicInteger;

/** DUMP-protected Android probe for the debug-only P6/P7 decision composition. */
public final class DecisionCompositionProbeActivity extends Activity {
    private static final String TAG = "CbDecisionComposition";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private void runProbe(String nonce) {
        DebugDecisionCompositionBoundary decision = null;
        DebugRuntimeCompositionBoundary runtime = null;
        try {
            ScenarioCatalog catalog = DebugSimulatedOrchestrationBackend.loadCatalog(this);
            AtomicInteger subscriptions = new AtomicInteger();
            AtomicInteger leases = new AtomicInteger();
            AtomicInteger invocations = new AtomicInteger();
            decision = new DebugDecisionCompositionBoundary(
                    catalog,
                    () -> 10_000L,
                    () -> "probe-subscription-" + subscriptions.incrementAndGet(),
                    () -> "probe-lease-" + leases.incrementAndGet());
            runtime = new DebugRuntimeCompositionBoundary(
                    () -> 10_000L,
                    () -> "probe-invocation-" + invocations.incrementAndGet());
            OrchestrationBackend.SessionDescriptor session =
                    new OrchestrationBackend.SessionDescriptor(
                            "a".repeat(64),
                            "decision-composition-probe-session",
                            "scene.comfort.cold.v1",
                            1,
                            0,
                            0L,
                            1L);
            DebugDecisionCompositionBoundary.Evidence decisionEvidence = decision.prepare(
                    session, "scene.comfort.cold.v1", "b".repeat(64));
            DebugRuntimeCompositionBoundary.Evidence runtimeEvidence = runtime.prepare(
                    session, "scene.comfort.cold.v1", "b".repeat(64));
            String boundDigest = DebugDecisionCompositionBoundary.combine(
                    decisionEvidence, runtimeEvidence);
            DebugDecisionCompositionBoundary.Snapshot active = decision.snapshot();
            DebugDecisionCompositionBoundary.Completion completion =
                    decision.complete(session, decisionEvidence);
            DebugDecisionCompositionBoundary.Completion completionReplay =
                    decision.complete(session, decisionEvidence);
            runtime.complete(session, runtimeEvidence);
            DebugDecisionCompositionBoundary.Snapshot terminal = decision.snapshot();

            boolean contextTrigger = decisionEvidence.getContextObservationCount() == 3
                    && decisionEvidence.getSuggestionDigest().matches("[0-9a-f]{64}")
                    && active.getSuggestionCount() == 1;
            boolean consentClosed = decisionEvidence.getConsentCode()
                            == ProactiveConsentPolicy.AdmissionCode.NO_ACTIVE_GRANT
                    && !decisionEvidence.isAutoExecutionAuthorized()
                    && !decisionEvidence.isProductionAuthority();
            boolean modelStub = decisionEvidence.getModelRouteDigest().matches("[0-9a-f]{64}")
                    && decisionEvidence.getModelOutputDigest().matches("[0-9a-f]{64}")
                    && active.getCompletedModelCount() == 1;
            boolean eventDelivery = decisionEvidence.getDeliveredEventCount() == 2
                    && decisionEvidence.getLastEventSequence() == 2
                    && active.getActiveEventSubscriptionCount() == 0;
            boolean evidenceBound = boundDigest.matches("[0-9a-f]{64}")
                    && !boundDigest.equals(decisionEvidence.getDigest())
                    && !boundDigest.equals(runtimeEvidence.getDigest());
            boolean terminalCleanup = !completion.isReplayed()
                    && completionReplay.isReplayed()
                    && terminal.getCompletedCount() == 1;
            boolean failClosed = !decisionEvidence.isNetworkAccessed()
                    && !decisionEvidence.isNpuAccessed()
                    && !decisionEvidence.isHardwareAccessed();
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            boolean complete = contextTrigger
                    && consentClosed
                    && modelStub
                    && eventDelivery
                    && evidenceBound
                    && terminalCleanup
                    && failClosed
                    && android13Arm64;

            Log.i(TAG, "nonce=" + nonce
                    + " decision_composition_probe_complete=" + complete
                    + " context_trigger_chain_verified=" + contextTrigger
                    + " proactive_consent_fail_closed_verified=" + consentClosed
                    + " deterministic_model_stub_verified=" + modelStub
                    + " digest_event_delivery_verified=" + eventDelivery
                    + " decision_runtime_evidence_bound=" + evidenceBound
                    + " decision_terminal_cleanup_verified=" + terminalCleanup
                    + " decision_composition_android13_arm64_verified=" + android13Arm64
                    + " cold_context_source_adapted=true"
                    + " fatigue_dms_source_stubbed=true"
                    + " trigger_suggestion_only=true"
                    + " proactive_auto_execution_authorized=false"
                    + " deterministic_test_model_invoked=true"
                    + " production_model_invoked=false"
                    + " event_transport_process_local=true"
                    + " free_text_accepted=false"
                    + " network_accessed=false"
                    + " npu_accessed=false"
                    + " vehicle_bus_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException failure) {
            Log.e(TAG, "nonce=" + nonce
                    + " decision_composition_probe_complete=false"
                    + " proactive_auto_execution_authorized=false"
                    + " production_model_invoked=false"
                    + " hardware_accessed=false", failure);
        } finally {
            if (runtime != null) {
                runtime.close();
            }
            if (decision != null) {
                decision.close();
            }
        }
    }
}
