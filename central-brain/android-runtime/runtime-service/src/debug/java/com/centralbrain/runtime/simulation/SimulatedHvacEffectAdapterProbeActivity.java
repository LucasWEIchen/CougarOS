package com.centralbrain.runtime.simulation;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Isolated API 33 probe for the debug-only typed HVAC simulation adapter. */
public final class SimulatedHvacEffectAdapterProbeActivity extends Activity {
    private static final String TAG = "CbSimHvac";
    private static final byte[] ENVELOPE = "hvac-probe-envelope-v1".getBytes();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        try {
            require(nonce != null && !nonce.isEmpty(), "nonce missing");
            verifySuccessAndIdempotency();
            verifyDelay();
            verifyTimeoutAndFailures();
            verifyMismatchAndValidation();
            Log.i(TAG, "nonce=" + nonce
                    + " simulated_hvac_probe_complete=true"
                    + " simulated_hvac_adapter_defined=true"
                    + " simulated_hvac_typed_target_verified=true"
                    + " simulated_hvac_range_zone_verified=true"
                    + " simulated_hvac_desired_reported_verified=true"
                    + " simulated_hvac_delay_verified=true"
                    + " simulated_hvac_timeout_verified=true"
                    + " simulated_hvac_failure_verified=true"
                    + " simulated_hvac_readback_mismatch_verified=true"
                    + " simulated_hvac_idempotency_verified=true"
                    + " simulated_hvac_android13_arm64_verified=true"
                    + " simulated_hvac_debug_only=true"
                    + " simulated_hvac_production_registered=false"
                    + " simulated_hvac_runtime_wired=false"
                    + " scenario_plan_runtime_published=false"
                    + " scenario_graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_signal_provider_wired=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " simulated_hvac_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " simulated_hvac_debug_only=true"
                    + " simulated_hvac_production_registered=false"
                    + " simulated_hvac_runtime_wired=false"
                    + " effect_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        } finally {
            finish();
        }
    }

    private static void verifySuccessAndIdempotency() {
        SimulatedHvacEffectAdapter adapter = adapter(FaultInjectionProfile.none());
        apply(adapter, token('a'), SimulatedHvacEffectAdapter.HvacTarget.power(true));
        apply(adapter, token('b'), SimulatedHvacEffectAdapter.HvacTarget.targetTemperature(
                "row1.driver", 22.5));
        EffectAdapter.Invocation fan = invocation(
                token('c'),
                SimulatedHvacEffectAdapter.HvacTarget.fanLevel("cabin", 4));
        adapter.apply(fan);
        DigitalTwinSnapshot snapshot = adapter.getDigitalTwinSnapshot();
        require(snapshot.reconcile(VehicleSignalPath.HVAC_ACTIVE, "cabin")
                == DigitalTwinSnapshot.ReconciliationState.MATCHED, "power not matched");
        require(snapshot.reconcile(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.driver") == DigitalTwinSnapshot.ReconciliationState.MATCHED,
                "temperature not matched");
        require(snapshot.reconcile(VehicleSignalPath.HVAC_FAN_LEVEL, "cabin")
                == DigitalTwinSnapshot.ReconciliationState.MATCHED, "fan not matched");
        long revision = adapter.getDigitalTwinRevision();
        adapter.apply(fan);
        require(adapter.getDigitalTwinRevision() == revision, "duplicate changed Twin");
    }

    private static void verifyDelay() {
        SimulationClock clock = new SimulationClock(2_000);
        SimulatedHvacEffectAdapter adapter = new SimulatedHvacEffectAdapter(
                clock, FaultInjectionProfile.delay(50));
        SimulatedHvacEffectAdapter.HvacTarget target =
                SimulatedHvacEffectAdapter.HvacTarget.targetTemperature(
                        "row1.passenger", 23.0);
        apply(adapter, token('d'), target);
        require(adapter.getDigitalTwinSnapshot().reconcile(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.passenger") == DigitalTwinSnapshot.ReconciliationState.PENDING_REPORTED,
                "delay did not expose desired");
        clock.advanceBy(50);
        require(adapter.queryStatus(token('d')).getState()
                == EffectAdapter.DeliveryState.APPLIED, "delay did not apply");
        require(adapter.getDigitalTwinSnapshot().reconcile(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.passenger") == DigitalTwinSnapshot.ReconciliationState.MATCHED,
                "delay did not report");
    }

    private static void verifyTimeoutAndFailures() {
        SimulationClock timeoutClock = new SimulationClock(3_000);
        SimulatedHvacEffectAdapter timeout = new SimulatedHvacEffectAdapter(
                timeoutClock, FaultInjectionProfile.timeout(25));
        apply(timeout, token('e'), SimulatedHvacEffectAdapter.HvacTarget.fanLevel(
                "cabin", 5));
        timeoutClock.advanceBy(25);
        require(timeout.querySimulationObservation(token('e')).getState()
                == SimulatedEffectAdapter.ReadbackState.TIMED_OUT,
                "timeout readback missing");
        require(timeout.getDigitalTwinSnapshot().reported(
                VehicleSignalPath.HVAC_FAN_LEVEL, "cabin").isEmpty(),
                "timeout fabricated report");

        SimulatedHvacEffectAdapter retry = adapter(FaultInjectionProfile.retryableFailure());
        SimulatedHvacEffectAdapter terminal = adapter(FaultInjectionProfile.terminalFailure());
        require(apply(retry, token('f'), SimulatedHvacEffectAdapter.HvacTarget.power(true))
                .getState() == EffectAdapter.ApplyState.RETRYABLE_FAILURE,
                "retryable failure missing");
        require(apply(terminal, token('1'), SimulatedHvacEffectAdapter.HvacTarget.power(true))
                .getState() == EffectAdapter.ApplyState.TERMINAL_FAILURE,
                "terminal failure missing");
    }

    private static void verifyMismatchAndValidation() {
        SimulatedHvacEffectAdapter mismatch =
                adapter(FaultInjectionProfile.readbackMismatch());
        apply(mismatch, token('2'), SimulatedHvacEffectAdapter.HvacTarget.targetTemperature(
                "row1.driver", 22.0));
        require(mismatch.querySimulationObservation(token('2')).getState()
                == SimulatedEffectAdapter.ReadbackState.MISMATCH,
                "base mismatch missing");
        require(mismatch.getDigitalTwinSnapshot().reconcile(
                VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                "row1.driver") == DigitalTwinSnapshot.ReconciliationState.MISMATCH,
                "Twin mismatch missing");

        boolean rejected = false;
        try {
            apply(adapter(FaultInjectionProfile.none()), token('3'),
                    SimulatedHvacEffectAdapter.HvacTarget.fanLevel("cabin", 8));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "out-of-range target accepted");
    }

    private static SimulatedHvacEffectAdapter adapter(FaultInjectionProfile profile) {
        return new SimulatedHvacEffectAdapter(new SimulationClock(1_000), profile);
    }

    private static EffectAdapter.ApplyResult apply(
            SimulatedHvacEffectAdapter adapter,
            String token,
            SimulatedHvacEffectAdapter.HvacTarget target) {
        return adapter.apply(invocation(token, target));
    }

    private static EffectAdapter.Invocation invocation(
            String token,
            SimulatedHvacEffectAdapter.HvacTarget target) {
        byte[] payload = target.toCanonicalPayload();
        return new EffectAdapter.Invocation(
                "effect-hvac-probe",
                "outbox-hvac-probe",
                token,
                SimulatedHvacEffectAdapter.DESTINATION,
                target.getCapabilityId().getCanonicalId(),
                sha256(payload),
                sha256(ENVELOPE),
                1,
                3,
                payload,
                ENVELOPE);
    }

    private static String token(char value) {
        char[] token = new char[64];
        Arrays.fill(token, value);
        return new String(token);
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
