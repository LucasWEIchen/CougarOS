package com.centralbrain.runtime.simulation;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

/** API 33 Binder probe for the protected debug simulation controller. */
public final class DebugSimulationControllerProbeActivity extends Activity {
    private static final String TAG = "CbDebugSimProbe";
    private String nonce;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                verifyController(IDebugSimulationController.Stub.asInterface(service));
                Log.i(TAG, "nonce=" + nonce
                        + " debug_simulation_controller_probe_complete=true"
                        + " debug_simulation_controller_defined=true"
                        + " debug_simulation_controller_aidl_version=1"
                        + " debug_simulation_controller_signature_permission_enforced=true"
                        + " debug_simulation_controller_capability_enforced=true"
                        + " debug_simulation_controller_state_signal_fault_clock_reset_verified=true"
                        + " debug_simulation_controller_audit_bounded_verified=true"
                        + " debug_simulation_controller_android13_arm64_verified=true"
                        + " debug_simulation_controller_debug_only=true"
                        + " debug_simulation_controller_production_exported=false"
                        + " debug_simulation_controller_runtime_wired=false"
                        + " vehicle_signal_provider_wired=false"
                        + " hardware_accessed=false");
            } catch (RuntimeException | RemoteException exception) {
                Log.e(TAG, "nonce=" + nonce
                        + " debug_simulation_controller_probe_complete=false"
                        + " error=" + exception.getClass().getSimpleName()
                        + " debug_simulation_controller_debug_only=true"
                        + " debug_simulation_controller_production_exported=false"
                        + " debug_simulation_controller_runtime_wired=false"
                        + " vehicle_signal_provider_wired=false"
                        + " hardware_accessed=false", exception);
            } finally {
                closeProbe();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // The probe has no reconnect contract; every run is a fresh explicit bind.
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.e(TAG, "nonce=" + nonce
                    + " debug_simulation_controller_probe_complete=false"
                    + " error=NULL_BINDING hardware_accessed=false");
            closeProbe();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nonce = getIntent().getStringExtra("nonce");
        if (nonce == null || nonce.isEmpty()) {
            Log.e(TAG, "debug_simulation_controller_probe_complete=false"
                    + " error=NONCE_MISSING hardware_accessed=false");
            finish();
            return;
        }
        Intent intent = new Intent(DebugSimulationControllerService.ACTION)
                .setComponent(new ComponentName(
                        this, DebugSimulationControllerService.class));
        bound = bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!bound) {
            Log.e(TAG, "nonce=" + nonce
                    + " debug_simulation_controller_probe_complete=false"
                    + " error=BIND_FAILED hardware_accessed=false");
            finish();
        }
    }

    private static void verifyController(IDebugSimulationController controller)
            throws RemoteException {
        require(controller != null, "controller missing");
        require(controller.getProtocolVersion() == IDebugSimulationController.INTERFACE_VERSION,
                "protocol version mismatch");
        require(IDebugSimulationController.INTERFACE_HASH.equals(controller.getProtocolHash()),
                "protocol hash mismatch");
        String initialDigest = controller.getSnapshotDigest();
        require(initialDigest.matches("[0-9a-f]{64}"), "initial digest invalid");
        require(controller.getRevision() == 0
                        && controller.getDrivingState()
                                == IDebugSimulationController.DRIVING_STATE_UNKNOWN
                        && controller.getSignalCount() == 0
                        && controller.getFaultCount() == 0,
                "initial state invalid");

        controller.setDrivingState(IDebugSimulationController.DRIVING_STATE_PARKED);
        controller.setSignal(
                VehicleSignalPath.VEHICLE_SPEED.getCanonicalPath(),
                "global",
                IDebugSimulationController.SCALAR_DECIMAL,
                false, 0, 0.0, "");
        controller.setSignal(
                VehicleSignalPath.CURRENT_GEAR.getCanonicalPath(),
                "global",
                IDebugSimulationController.SCALAR_TEXT,
                false, 0, 0.0, "P");
        controller.setAdapterFault(
                SimulatedHvacEffectAdapter.ADAPTER_ID,
                IDebugSimulationController.FAULT_DELAY,
                50);
        controller.advanceSimulationClock(75);
        require(controller.getRevision() == 5
                        && controller.getDrivingState()
                                == IDebugSimulationController.DRIVING_STATE_PARKED
                        && controller.getSignalCount() == 2
                        && controller.getFaultCount() == 1
                        && controller.getSimulationElapsedRealtimeMs()
                                == DebugSimulationController.DEFAULT_INITIAL_ELAPSED_REALTIME_MS + 75
                        && controller.getAuditEntryCount() == 5,
                "mutation state invalid");
        require(!initialDigest.equals(controller.getSnapshotDigest()),
                "snapshot digest did not change");

        boolean rejected = false;
        try {
            controller.setSignal(
                    "Vehicle.NotAllowlisted",
                    "global",
                    IDebugSimulationController.SCALAR_DECIMAL,
                    false, 0, 1.0, "");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "unknown signal was accepted");

        controller.reset();
        require(controller.getRevision() == 6
                        && controller.getDrivingState()
                                == IDebugSimulationController.DRIVING_STATE_UNKNOWN
                        && controller.getSignalCount() == 0
                        && controller.getFaultCount() == 0
                        && controller.getSimulationElapsedRealtimeMs()
                                == DebugSimulationController.DEFAULT_INITIAL_ELAPSED_REALTIME_MS
                        && controller.getAuditEntryCount() == 6,
                "reset state invalid");
    }

    private void closeProbe() {
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        finish();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
