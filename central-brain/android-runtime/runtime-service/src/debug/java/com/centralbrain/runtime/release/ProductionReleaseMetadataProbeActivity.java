package com.centralbrain.runtime.release;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.BuildConfig;

import java.util.ArrayList;
import java.util.List;

/** DUMP-protected debug probe that emits only bounded release counts and booleans. */
public final class ProductionReleaseMetadataProbeActivity extends Activity {
    private static final String TAG = "CbReleaseProbe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = sanitizeNonce(getIntent().getStringExtra("nonce"));
        try {
            if (!BuildConfig.DEBUG) {
                throw new IllegalStateException("release metadata probe is debug-only");
            }
            ProductionReleaseMetadataProjection.Snapshot snapshot =
                    ProductionReleaseMetadataProjection.evaluate(collectObservations());
            Log.i(TAG, "nonce=" + nonce + " " + snapshot.auditMetadata()
                    + " release_android_debug_probe_available=true"
                    + " release_android_debug_probe_executed=true");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " release_metadata_probe_complete=false"
                    + " release_android_debug_probe_available=true"
                    + " release_android_debug_probe_executed=true"
                    + " error_type=" + exception.getClass().getSimpleName()
                    + " production_signer_owner_approved=false"
                    + " production_release_candidate_admitted=false"
                    + " release_installer_wired=false"
                    + " release_install_executed=false"
                    + " release_uninstall_executed=false"
                    + " release_rollback_executor_wired=false"
                    + " release_rollback_executed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } finally {
            finish();
        }
    }

    private List<ProductionReleaseMetadataProjection.PackageObservation>
            collectObservations() {
        PackageManager packageManager = getPackageManager();
        String runtimePackage = ProductionReleaseMetadataProjection.requiredPackageName(0);
        List<ProductionReleaseMetadataProjection.PackageObservation> observations =
                new ArrayList<>(ProductionReleaseMetadataProjection.REQUIRED_PACKAGE_COUNT);

        for (int index = 0;
                index < ProductionReleaseMetadataProjection.REQUIRED_PACKAGE_COUNT;
                index++) {
            String packageName =
                    ProductionReleaseMetadataProjection.requiredPackageName(index);
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(
                        packageName, PackageManager.PackageInfoFlags.of(0));
                boolean versionMatched = packageInfo.getLongVersionCode()
                        == ProductionReleaseMetadataProjection.repositoryVersionCode(index);
                boolean signerMatchedRuntime = index == 0
                        || packageManager.checkSignatures(runtimePackage, packageName)
                        == PackageManager.SIGNATURE_MATCH;
                observations.add(
                        ProductionReleaseMetadataProjection.PackageObservation.installed(
                                versionMatched, signerMatchedRuntime));
            } catch (PackageManager.NameNotFoundException exception) {
                observations.add(
                        ProductionReleaseMetadataProjection.PackageObservation.notInstalled());
            }
        }
        return observations;
    }

    private static String sanitizeNonce(String nonce) {
        return nonce != null && nonce.matches("[0-9]{1,24}") ? nonce : "invalid";
    }
}
