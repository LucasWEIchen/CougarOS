package com.centralbrain.policyprobe;

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
import android.os.SystemClock;
import android.util.Log;

import com.centralbrain.sdk.diagnostics.DiagnosticQuery;
import com.centralbrain.sdk.diagnostics.ICentralBrainDiagnostics;
import com.centralbrain.sdk.production.AgentTaskRequest;
import com.centralbrain.sdk.production.ICentralBrainRuntime;
import com.centralbrain.sdk.production.ICentralBrainTaskCallback;
import com.centralbrain.sdk.production.TaskFailure;
import com.centralbrain.sdk.production.TaskHandle;
import com.centralbrain.sdk.production.TaskResult;
import com.centralbrain.sdk.production.TaskUpdate;

/** DUMP-protected second-package probe for R3 default-deny device evidence. */
public final class CapabilityPolicyProbeActivity extends Activity {
    private static final String TAG = "CentralBrainPolicyProbe";
    private static final ComponentName PRODUCTION_COMPONENT = new ComponentName(
            "com.centralbrain.runtime",
            "com.centralbrain.runtime.CentralBrainRuntimeService");
    private static final ComponentName DIAGNOSTIC_COMPONENT = new ComponentName(
            "com.centralbrain.runtime",
            "com.centralbrain.runtime.CentralBrainDiagnosticService");
    private static final long TIMEOUT_MS = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean productionBound;
    private boolean diagnosticBound;
    private boolean finished;
    private boolean protocolVersionDenied;
    private boolean protocolHashDenied;
    private boolean submitDenied;
    private boolean statusDenied;
    private boolean cancelDenied;

    private final ServiceConnection productionConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ICentralBrainRuntime runtime = ICentralBrainRuntime.Stub.asInterface(service);
            protocolVersionDenied = denied(runtime::getProtocolVersion);
            protocolHashDenied = denied(runtime::getProtocolHash);
            submitDenied = denied(() -> runtime.submitAgentTask(
                    request(),
                    callback));
            TaskHandle foreignHandle = new TaskHandle();
            foreignHandle.taskId = "policy-probe-foreign-task";
            statusDenied = denied(() -> runtime.getTaskStatus(foreignHandle));
            cancelDenied = denied(() -> runtime.cancelTask(
                    foreignHandle,
                    ICentralBrainRuntime.CANCEL_REASON_USER));
            unbindProduction();
            bindDiagnostic();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (!finished) {
                Log.e(TAG, "capability_probe_complete=false production_disconnected=true");
                finishProbe();
            }
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.e(TAG, "capability_probe_complete=false production_null_binding=true");
            finishProbe();
        }
    };

    private final ServiceConnection diagnosticConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ICentralBrainDiagnostics diagnostics = ICentralBrainDiagnostics.Stub.asInterface(service);
            boolean diagnosticVersionDenied = denied(diagnostics::getProtocolVersion);
            boolean diagnosticHashDenied = denied(diagnostics::getProtocolHash);
            DiagnosticQuery query = new DiagnosticQuery();
            query.schemaVersion = 1;
            query.pageSize = 1;
            boolean diagnosticPageDenied = denied(() -> diagnostics.getPage(query));

            Log.i(TAG, "capability_probe_complete=true"
                    + " production_bind_succeeded=true"
                    + " diagnostic_bind_succeeded=true"
                    + " protocol_version_denied=" + protocolVersionDenied
                    + " protocol_hash_denied=" + protocolHashDenied
                    + " submit_denied=" + submitDenied
                    + " status_denied=" + statusDenied
                    + " cancel_denied=" + cancelDenied
                    + " diagnostic_version_denied=" + diagnosticVersionDenied
                    + " diagnostic_hash_denied=" + diagnosticHashDenied
                    + " diagnostic_page_denied=" + diagnosticPageDenied
                    + " hardware_accessed=false");
            finishProbe();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (!finished) {
                Log.e(TAG, "capability_probe_complete=false diagnostic_disconnected=true");
                finishProbe();
            }
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.e(TAG, "capability_probe_complete=false diagnostic_null_binding=true");
            finishProbe();
        }
    };

    private final ICentralBrainTaskCallback callback = new ICentralBrainTaskCallback.Stub() {
        @Override
        public void onTaskUpdate(TaskUpdate update) {
        }

        @Override
        public void onTaskCompleted(TaskResult result) {
        }

        @Override
        public void onTaskFailed(TaskFailure failure) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent().setComponent(PRODUCTION_COMPONENT);
        productionBound = bindService(intent, productionConnection, Context.BIND_AUTO_CREATE);
        Log.i(TAG, "production_bind_requested=true bind_returned=" + productionBound);
        if (!productionBound) {
            Log.e(TAG, "capability_probe_complete=false production_bind_succeeded=false");
            finishProbe();
            return;
        }
        handler.postDelayed(() -> {
            if (!finished) {
                Log.e(TAG, "capability_probe_complete=false timeout=true");
                finishProbe();
            }
        }, TIMEOUT_MS);
    }

    @Override
    protected void onDestroy() {
        unbindProduction();
        if (diagnosticBound) {
            unbindService(diagnosticConnection);
            diagnosticBound = false;
        }
        super.onDestroy();
    }

    private void bindDiagnostic() {
        Intent intent = new Intent().setComponent(DIAGNOSTIC_COMPONENT);
        diagnosticBound = bindService(intent, diagnosticConnection, Context.BIND_AUTO_CREATE);
        Log.i(TAG, "diagnostic_bind_requested=true bind_returned=" + diagnosticBound);
        if (!diagnosticBound) {
            Log.e(TAG, "capability_probe_complete=false diagnostic_bind_succeeded=false");
            finishProbe();
        }
    }

    private void unbindProduction() {
        if (productionBound) {
            unbindService(productionConnection);
            productionBound = false;
        }
    }

    private static AgentTaskRequest request() {
        AgentTaskRequest request = new AgentTaskRequest();
        request.clientRequestId = "policy-probe-request";
        request.sessionId = "policy-probe-session";
        request.utterance = "this request must be denied";
        request.locale = "en-US";
        request.deadlineElapsedRealtimeMs = SystemClock.elapsedRealtime() + 10000;
        request.priority = 1;
        request.idempotencyKey = "policy-probe-idempotency";
        return request;
    }

    private static boolean denied(RemoteCall call) {
        try {
            call.run();
            return false;
        } catch (SecurityException expected) {
            return true;
        } catch (RemoteException exception) {
            Log.e(TAG, "unexpected RemoteException", exception);
            return false;
        }
    }

    private void finishProbe() {
        if (finished) {
            return;
        }
        finished = true;
        handler.removeCallbacksAndMessages(null);
        finish();
    }

    private interface RemoteCall {
        void run() throws RemoteException;
    }
}
