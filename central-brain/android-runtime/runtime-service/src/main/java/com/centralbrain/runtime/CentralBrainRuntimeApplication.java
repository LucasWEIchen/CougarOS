package com.centralbrain.runtime;

import android.app.Application;
import android.util.Log;

import com.centralbrain.runtime.nativebridge.NativeRuntimeProcess;
import com.centralbrain.runtime.nativebridge.NativeRuntimeProcessSnapshot;

/** Owns the native handle for the lifetime of the ordinary Android app process. */
public final class CentralBrainRuntimeApplication extends Application {
    private static final String TAG = "CentralBrainNative";
    private static final int NATIVE_MAX_ACTIVE_SLOTS = 4;

    private NativeRuntimeProcess nativeRuntimeProcess;

    @Override
    public void onCreate() {
        super.onCreate();
        nativeRuntimeProcess = NativeRuntimeProcess.start(NATIVE_MAX_ACTIVE_SLOTS);
        Log.i(TAG, "native_runtime_process_created=true "
                + getNativeRuntimeSnapshot().logFields());
    }

    public NativeRuntimeProcessSnapshot getNativeRuntimeSnapshot() {
        if (nativeRuntimeProcess == null) {
            throw new IllegalStateException("native runtime process is not initialized");
        }
        return nativeRuntimeProcess.snapshot();
    }

    @Override
    public void onTerminate() {
        if (nativeRuntimeProcess != null) {
            nativeRuntimeProcess.close();
            Log.i(TAG, "native_runtime_process_terminated=true "
                    + nativeRuntimeProcess.snapshot().logFields());
        }
        super.onTerminate();
    }
}
