package com.centralbrain.runtime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

/** DUMP-protected debug-only process-death injector for R7C Binder recovery evidence. */
public final class RuntimeFaultProbeReceiver extends BroadcastReceiver {
    public static final String ACTION_KILL_PROCESS =
            "com.centralbrain.runtime.DEBUG_KILL_PROCESS";
    private static final String TAG = "CentralBrainFaultProbe";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!BuildConfig.DEBUG) {
            throw new IllegalStateException("Runtime fault probe must never run in release");
        }
        if (intent == null || !ACTION_KILL_PROCESS.equals(intent.getAction())) {
            throw new IllegalArgumentException("unsupported Runtime fault action");
        }
        Log.w(TAG, "runtime_fault_injection_requested=true"
                + " fault=PROCESS_DEATH"
                + " hardware_accessed=false");
        Process.killProcess(Process.myPid());
    }
}
