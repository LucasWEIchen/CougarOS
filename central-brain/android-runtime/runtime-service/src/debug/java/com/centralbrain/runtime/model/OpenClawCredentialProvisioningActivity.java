package com.centralbrain.runtime.model;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;

/** DUMP-protected hardware-test credential injection; the token is never persisted. */
public final class OpenClawCredentialProvisioningActivity extends Activity {
    public static final String EXTRA_TOKEN = "openclaw_token";
    public static final String EXTRA_CLEAR = "clear";
    private static final String TAG = "CentralBrainOpenClaw";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BuildConfigGuard.isTargetProfile()) {
            Log.w(TAG, "openclaw_credential_provisioned=false reason=PROFILE_DISABLED");
            finish();
            return;
        }
        if (getIntent().getBooleanExtra(EXTRA_CLEAR, false)) {
            OpenClawCredentialStore.clear();
            Log.i(TAG, "openclaw_credential_cleared=true token_logged=false");
            finish();
            return;
        }
        String value = getIntent().getStringExtra(EXTRA_TOKEN);
        char[] candidate = value == null ? null : value.toCharArray();
        try {
            OpenClawCredentialStore.provision(candidate);
            Log.i(TAG, "openclaw_credential_provisioned=true token_logged=false persisted=false");
        } catch (RuntimeException failure) {
            Log.e(TAG, "openclaw_credential_provisioned=false reason=INVALID_CREDENTIAL");
        } finally {
            if (candidate != null) {
                Arrays.fill(candidate, '\0');
            }
        }
        finish();
    }

    static final class BuildConfigGuard {
        private BuildConfigGuard() {
        }

        static boolean isTargetProfile() {
            return com.centralbrain.runtime.BuildConfig.OPENCLAW_TARGET_ROUTING_ENABLED
                    && "target_openclaw_transitional".equals(
                            com.centralbrain.runtime.BuildConfig.MODEL_GATEWAY_PROFILE);
        }
    }
}
