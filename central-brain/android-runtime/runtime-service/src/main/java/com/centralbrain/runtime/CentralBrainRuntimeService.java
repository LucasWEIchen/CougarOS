package com.centralbrain.runtime;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.centralbrain.sdk.CentralBrainSdk;

/**
 * R1 process/lifecycle placeholder for the future Central Brain Binder runtime.
 *
 * Req IDs: XSC-004, XSC-005, XSC-006, NV-F-001, NV-P-002, DEL-001.
 */
public final class CentralBrainRuntimeService extends Service {
    public static final String RUNTIME_STAGE = CentralBrainSdk.EVOLUTION_STAGE;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // R2 introduces the typed production and diagnostic Binder surfaces.
        return null;
    }
}
