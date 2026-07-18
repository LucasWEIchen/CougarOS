package com.centralbrain.runtime.scenario;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

/** DUMP-protected fixed probe for the same-signer debug Binder composition. */
public final class SimulatedScenarioCompositionProbeActivity extends Activity {
    public static final String TAG = "CbSimScenarioProbe";
    private static final long TIMEOUT_MS = 10_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean bound;
    private boolean completed;

    private final Runnable timeout = () -> complete(false, "BIND_TIMEOUT");

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            try {
                ISimulatedScenarioRuntime runtime =
                        ISimulatedScenarioRuntime.Stub.asInterface(binder);
                require(runtime.getProtocolVersion() == 2, "PROTOCOL_VERSION");

                SimulatedScenarioBinderSnapshot cold = runtime.startScenario(
                        ISimulatedScenarioRuntime.SCENARIO_COLD,
                        ISimulatedScenarioRuntime.DRIVING_PARKED);
                require(cold.sessionState == ISimulatedScenarioRuntime.SESSION_COMPLETED,
                        "COLD_STATE");
                require(cold.simulatedEffectDispatchCount == 3,
                        "COLD_EFFECT_COUNT");
                require(cold.simulatedReadbackMatchCount == 3,
                        "COLD_READBACK_COUNT");

                SimulatedScenarioBinderSnapshot fatigue = runtime.startScenario(
                        ISimulatedScenarioRuntime.SCENARIO_FATIGUE,
                        ISimulatedScenarioRuntime.DRIVING_PARKED);
                require(fatigue.sessionState
                                == ISimulatedScenarioRuntime.SESSION_WAITING_APPROVAL,
                        "FATIGUE_APPROVAL_STATE");
                SimulatedScenarioBinderSnapshot completedFatigue =
                        runtime.supplyPendingOutcome(
                                fatigue.runId,
                                ISimulatedScenarioRuntime.OUTCOME_SUCCEEDED);
                require(completedFatigue.sessionState
                                == ISimulatedScenarioRuntime.SESSION_COMPLETED,
                        "FATIGUE_COMPLETED_STATE");
                require(completedFatigue.simulatedEffectDispatchCount == 5,
                        "FATIGUE_EFFECT_COUNT");
                require(completedFatigue.simulatedReadbackMatchCount == 3,
                        "FATIGUE_READBACK_COUNT");
                require(completedFatigue.simulatedApprovalInputCount == 1,
                        "FATIGUE_APPROVAL_COUNT");
                require(completedFatigue.simulatedFailureCount == 0,
                        "FATIGUE_FAILURE_COUNT");
                require(!completedFatigue.approvalAuthorityAvailable
                                && !completedFatigue.hardwareAccessed
                                && !completedFatigue.productionReady
                                && !completedFatigue.targetHardwareValidated,
                        "FALSE_AUTHORITY_FLAGS");
                Log.i(TAG, "probe_result=PASS protocol_version=2 scenario_count=2"
                        + " effect_dispatch_count=8 readback_match_count=6"
                        + " approval_input_count=1 failure_count=0"
                        + " simulated_only=true hardware_accessed=false"
                        + " production_ready=false target_hardware_validated=false");
                complete(true, "PASS");
            } catch (RemoteException failure) {
                complete(false, "REMOTE_FAILURE");
            } catch (RuntimeException failure) {
                complete(false, "CONTRACT_FAILURE");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (!completed) {
                complete(false, "SERVICE_DISCONNECTED");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(SimulatedScenarioRuntimeService.ACTION)
                .setPackage(getPackageName());
        handler.postDelayed(timeout, TIMEOUT_MS);
        bound = bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!bound) {
            complete(false, "BIND_REJECTED");
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timeout);
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        super.onDestroy();
    }

    private void complete(boolean passed, String code) {
        if (completed) {
            return;
        }
        completed = true;
        handler.removeCallbacks(timeout);
        if (!passed) {
            Log.e(TAG, "probe_result=FAIL failure_code=" + code
                    + " hardware_accessed=false production_ready=false"
                    + " target_hardware_validated=false");
        }
        finish();
    }

    private static void require(boolean condition, String code) {
        if (!condition) {
            throw new IllegalStateException("CB_SIM_SCENARIO_PROBE: " + code);
        }
    }
}
