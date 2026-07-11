package com.centralbrain.runtime;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * ADB-only debug entrypoint for R1 lifecycle validation.
 * Req IDs: XSC-004, XSC-005, XSC-006, NV-F-001, NV-P-002, DEL-001.
 */
public final class RuntimeProbeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BuildConfig.DEBUG) {
            throw new IllegalStateException("Runtime probe must never run in a release build");
        }

        startService(new Intent(this, CentralBrainRuntimeService.class));
        setResult(RESULT_OK);
        finish();
    }
}
