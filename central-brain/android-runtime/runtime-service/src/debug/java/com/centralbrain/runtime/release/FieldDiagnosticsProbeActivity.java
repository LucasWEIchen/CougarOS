package com.centralbrain.runtime.release;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.BuildConfig;
import com.centralbrain.runtime.CentralBrainDiagnosticService;
import com.centralbrain.runtime.CentralBrainRuntimeService;

/** DUMP-protected debug preflight for bounded field diagnostics. */
public final class FieldDiagnosticsProbeActivity extends Activity {
    private static final String TAG = "CbFieldDiag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = sanitizeNonce(getIntent().getStringExtra("nonce"));
        try {
            if (!BuildConfig.DEBUG) {
                throw new IllegalStateException("field diagnostics probe is debug-only");
            }
            FieldDiagnosticsProjection.Snapshot snapshot =
                    FieldDiagnosticsProjection.evaluate(collectObservation());
            Log.i(TAG, "nonce=" + nonce + " " + snapshot.auditMetadata()
                    + " field_diagnostics_android_debug_probe_available=true"
                    + " field_diagnostics_android_debug_probe_executed=true");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " field_diagnostics_probe_complete=false"
                    + " field_diagnostics_android_debug_probe_available=true"
                    + " field_diagnostics_android_debug_probe_executed=true"
                    + " field_diagnostics_raw_log_persisted=false"
                    + " field_diagnostics_package_name_logged=false"
                    + " field_diagnostics_device_identity_logged=false"
                    + " field_diagnostics_signer_material_logged=false"
                    + " field_diagnostics_target_input_logged=false"
                    + " field_diagnostics_user_model_vehicle_payload_logged=false"
                    + " field_diagnostics_automatic_upload_enabled=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } finally {
            finish();
        }
    }

    private FieldDiagnosticsProjection.ProbeObservation collectObservation() {
        PackageManager packageManager = getPackageManager();
        String runtimePackage = ProductionReleaseMetadataProjection.requiredPackageName(0);
        int installedCount = 0;
        int versionMatchCount = 0;
        int signerMatchCount = 0;

        for (int index = 0;
                index < ProductionReleaseMetadataProjection.REQUIRED_PACKAGE_COUNT;
                index++) {
            String packageName = ProductionReleaseMetadataProjection.requiredPackageName(index);
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(
                        packageName, PackageManager.PackageInfoFlags.of(0));
                installedCount++;
                if (packageInfo.getLongVersionCode()
                        == ProductionReleaseMetadataProjection.repositoryVersionCode(index)) {
                    versionMatchCount++;
                }
                if (index > 0
                        && packageManager.checkSignatures(runtimePackage, packageName)
                        == PackageManager.SIGNATURE_MATCH) {
                    signerMatchCount++;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // Missing packages are represented only by the aggregate counts.
            }
        }

        boolean demoLaunchable = packageManager.getLaunchIntentForPackage(
                ProductionReleaseMetadataProjection.requiredPackageName(1)) != null;
        boolean client2Launchable = packageManager.getLaunchIntentForPackage(
                ProductionReleaseMetadataProjection.requiredPackageName(2)) != null;
        return new FieldDiagnosticsProjection.ProbeObservation(
                installedCount,
                versionMatchCount,
                signerMatchCount,
                demoLaunchable,
                client2Launchable,
                isServiceDeclared(packageManager, CentralBrainRuntimeService.class),
                isServiceDeclared(packageManager, CentralBrainDiagnosticService.class));
    }

    private boolean isServiceDeclared(
            PackageManager packageManager,
            Class<?> serviceClass) {
        try {
            ServiceInfo serviceInfo = packageManager.getServiceInfo(
                    new ComponentName(this, serviceClass),
                    PackageManager.ComponentInfoFlags.of(0));
            return serviceInfo.enabled;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    private static String sanitizeNonce(String nonce) {
        return nonce != null && nonce.matches("[0-9]{1,24}") ? nonce : "invalid";
    }
}
