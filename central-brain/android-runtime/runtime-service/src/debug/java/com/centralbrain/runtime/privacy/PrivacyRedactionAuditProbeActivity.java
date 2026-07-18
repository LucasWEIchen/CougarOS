package com.centralbrain.runtime.privacy;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public final class PrivacyRedactionAuditProbeActivity extends Activity {
    private static final String TAG = "CbPrivacyProbe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(sanitizeNonce(nonce));
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            PrivacyRedactionAuditProjection.Snapshot snapshot =
                    PrivacyRedactionAuditProjection.evaluateCurrentDraft();
            Log.i(TAG, "nonce=" + nonce + " " + snapshot.auditMetadata()
                    + " privacy_android_debug_probe_available=true"
                    + " privacy_android_debug_probe_executed=true");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " privacy_redaction_probe_complete=false"
                    + " privacy_android_debug_probe_available=true"
                    + " privacy_android_debug_probe_executed=true"
                    + " error_type=" + exception.getClass().getSimpleName()
                    + " privacy_current_policy_admitted=false"
                    + " privacy_repository_mutation_wired=false"
                    + " privacy_runtime_lifecycle_wiring_complete=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        }
    }

    private static String sanitizeNonce(String nonce) {
        return nonce != null && nonce.matches("[0-9]{1,24}") ? nonce : "invalid";
    }
}
