package com.centralbrain.runtime.simulation;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.effects.EffectAdapterContract;
import com.centralbrain.runtime.vehicle.schema.SignalSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SimulatedEffectAdapterProbeActivity extends Activity {
    private static final String TAG = "CbSimEffectBase";
    private static final String DESTINATION = "UIB_ACTION";
    private static final String TOKEN_A = "a".repeat(64);
    private static final String TOKEN_B = "b".repeat(64);
    private static final String TOKEN_C = "c".repeat(64);
    private static final String TOKEN_D = "d".repeat(64);
    private static final byte[] PAYLOAD = "payload".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENVELOPE = "envelope".getBytes(StandardCharsets.UTF_8);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private void runProbe(String nonce) {
        try {
            SimulationClock clock = new SimulationClock(10_000);
            TrackingAdapter adapter = new TrackingAdapter(
                    clock, FaultInjectionProfile.none());
            boolean descriptorVerified = EffectAdapterContract.requireSafe(
                            adapter, DESTINATION) == adapter.descriptor()
                    && adapter.simulationDescriptor().isSimulation()
                    && !adapter.simulationDescriptor().isProductionAuthorized()
                    && adapter.simulationDescriptor().getObservationSource()
                            == SignalSource.SIMULATED;

            EffectAdapter.Invocation immediate = invocation(
                    TOKEN_A, "climate.setPower");
            EffectAdapter.ApplyResult first = adapter.apply(immediate);
            EffectAdapter.ApplyResult replay = adapter.apply(immediate);
            boolean idempotencyVerified = first == replay
                    && first.getState() == EffectAdapter.ApplyState.APPLIED
                    && adapter.getRecordCount() == 1
                    && adapter.appliedCallbacks == 1;

            adapter.setFaultInjectionProfile(FaultInjectionProfile.delay(500));
            EffectAdapter.ApplyResult delayed = adapter.apply(
                    invocation(TOKEN_B, "climate.setFan"));
            boolean delayPending = delayed.getState() == EffectAdapter.ApplyState.UNKNOWN
                    && adapter.queryStatus(TOKEN_B).getState()
                            == EffectAdapter.DeliveryState.UNKNOWN
                    && adapter.querySimulationObservation(TOKEN_B).getState()
                            == SimulatedEffectAdapter.ReadbackState.PENDING;
            clock.advanceBy(500);
            boolean delayVerified = delayPending
                    && adapter.queryStatus(TOKEN_B).getState()
                            == EffectAdapter.DeliveryState.APPLIED
                    && adapter.querySimulationObservation(TOKEN_B).getState()
                            == SimulatedEffectAdapter.ReadbackState.MATCHED
                    && adapter.appliedCallbacks == 2;

            adapter.setFaultInjectionProfile(FaultInjectionProfile.timeout(1_000));
            adapter.apply(invocation(TOKEN_C, "seat.setHeating"));
            clock.advanceBy(1_000);
            boolean timeoutVerified = adapter.queryStatus(TOKEN_C).getState()
                            == EffectAdapter.DeliveryState.UNKNOWN
                    && adapter.querySimulationObservation(TOKEN_C).getState()
                            == SimulatedEffectAdapter.ReadbackState.TIMED_OUT;

            adapter.setFaultInjectionProfile(FaultInjectionProfile.retryableFailure());
            EffectAdapter.ApplyResult retryable = adapter.apply(
                    invocation(TOKEN_D, "media.pause"));
            adapter.setFaultInjectionProfile(FaultInjectionProfile.terminalFailure());
            String terminalToken = "e".repeat(64);
            EffectAdapter.ApplyResult terminal = adapter.apply(
                    invocation(terminalToken, "media.pause"));
            boolean failureVerified = retryable.getState()
                            == EffectAdapter.ApplyState.RETRYABLE_FAILURE
                    && adapter.querySimulationObservation(TOKEN_D).getState()
                            == SimulatedEffectAdapter.ReadbackState.RETRYABLE_FAILURE
                    && terminal.getState() == EffectAdapter.ApplyState.TERMINAL_FAILURE
                    && adapter.queryStatus(terminalToken).getState()
                            == EffectAdapter.DeliveryState.REJECTED;

            adapter.setFaultInjectionProfile(FaultInjectionProfile.readbackMismatch());
            String mismatchToken = "f".repeat(64);
            EffectAdapter.ApplyResult mismatch = adapter.apply(
                    invocation(mismatchToken, "seat.setRecline"));
            SimulatedEffectAdapter.SimulationObservation mismatchObservation =
                    adapter.querySimulationObservation(mismatchToken);
            boolean mismatchVerified = mismatch.getState()
                            == EffectAdapter.ApplyState.APPLIED
                    && adapter.queryStatus(mismatchToken).getState()
                            == EffectAdapter.DeliveryState.APPLIED
                    && mismatchObservation.getState()
                            == SimulatedEffectAdapter.ReadbackState.MISMATCH
                    && mismatchObservation.getSource() == SignalSource.SIMULATED
                    && !mismatchObservation.isProductionTrusted();

            boolean clockVerified = clock.nowElapsedRealtimeMs() == 11_500
                    && FaultInjectionProfile.none().getDigest().matches("[0-9a-f]{64}")
                    && !FaultInjectionProfile.none().isProductionAuthorized();
            boolean allVerified = descriptorVerified
                    && idempotencyVerified
                    && delayVerified
                    && timeoutVerified
                    && failureVerified
                    && mismatchVerified
                    && clockVerified;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");

            Log.i(TAG, "nonce=" + nonce
                    + " simulated_effect_adapter_probe_complete=true"
                    + " simulated_effect_adapter_base_defined=" + allVerified
                    + " simulation_descriptor_verified=" + descriptorVerified
                    + " simulation_clock_verified=" + clockVerified
                    + " simulation_delay_verified=" + delayVerified
                    + " simulation_timeout_verified=" + timeoutVerified
                    + " simulation_failure_verified=" + failureVerified
                    + " simulation_readback_mismatch_verified=" + mismatchVerified
                    + " simulation_idempotency_verified=" + idempotencyVerified
                    + " simulated_effect_adapter_android13_arm64_verified="
                    + android13Arm64Verified
                    + " simulated_effect_adapter_debug_only=true"
                    + " simulated_effect_adapter_production_registered=false"
                    + " simulated_effect_adapter_runtime_wired=false"
                    + " scenario_plan_runtime_published=false"
                    + " scenario_graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_signal_provider_wired=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " simulated_effect_adapter_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " simulated_effect_adapter_debug_only=true"
                    + " simulated_effect_adapter_production_registered=false"
                    + " simulated_effect_adapter_runtime_wired=false"
                    + " effect_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static EffectAdapter.Invocation invocation(String token, String actionId) {
        return new EffectAdapter.Invocation(
                "effect-1",
                "outbox-1",
                token,
                DESTINATION,
                actionId,
                sha256(PAYLOAD),
                sha256(ENVELOPE),
                1,
                3,
                PAYLOAD,
                ENVELOPE);
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

    private static final class TrackingAdapter extends SimulatedEffectAdapter {
        private int appliedCallbacks;

        private TrackingAdapter(
                SimulationClock clock, FaultInjectionProfile profile) {
            super("debug.simulated.base", DESTINATION, clock, profile);
        }

        @Override
        protected void onSimulationApplied(
                Invocation invocation, FaultInjectionProfile profile) {
            appliedCallbacks++;
        }
    }
}
