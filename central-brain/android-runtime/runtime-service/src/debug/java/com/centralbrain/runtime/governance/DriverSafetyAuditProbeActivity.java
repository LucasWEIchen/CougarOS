package com.centralbrain.runtime.governance;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public final class DriverSafetyAuditProbeActivity extends Activity {
    private static final String TAG = "CbSafetyProbe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(sanitizeNonce(nonce));
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            DriverSafetyAuditProjection.Snapshot snapshot =
                    DriverSafetyAuditProjection.evaluateCurrentRepository();
            Log.i(TAG, "nonce=" + nonce + " " + snapshot.auditMetadata()
                    + " driver_safety_android_debug_probe_available=true"
                    + " driver_safety_android_debug_probe_executed=true");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " driver_safety_probe_complete=false"
                    + " driver_safety_probe_error=true"
                    + " driver_safety_android_debug_probe_available=true"
                    + " driver_safety_android_debug_probe_executed=true"
                    + " driver_safety_current_owner_policy_approved=false"
                    + " driver_safety_vehicle_state_provider_wired=false"
                    + " driver_safety_effect_runtime_wired=false"
                    + " driver_safety_effect_dispatch_authorized=false"
                    + " driver_safety_hardware_operation_executed=false"
                    + " driver_safety_vehicle_scalar_read=false"
                    + " driver_safety_android13_arm64_verified=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        }
    }

    private static String sanitizeNonce(String nonce) {
        return nonce != null && nonce.matches("[0-9]{1,24}") ? nonce : "invalid";
    }
}
