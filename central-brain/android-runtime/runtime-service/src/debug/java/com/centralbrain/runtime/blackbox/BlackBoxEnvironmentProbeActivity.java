package com.centralbrain.runtime.blackbox;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import com.centralbrain.runtime.BuildConfig;
import com.centralbrain.runtime.CentralBrainRuntimeApplication;
import com.centralbrain.runtime.nativebridge.NativeRuntimeProcessSnapshot;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** DUMP-protected public-API probe for an ordinary Android 13 application install. */
public final class BlackBoxEnvironmentProbeActivity extends Activity {
    private static final String TAG = "CbBlackBoxProbe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BuildConfig.DEBUG) {
            throw new IllegalStateException("Black-box environment probe must remain debug-only");
        }
        String requestedNonce = getIntent().getStringExtra("nonce");
        String nonce = requestedNonce == null ? "missing" : requestedNonce;
        try {
            BlackBoxEnvironmentSnapshot snapshot = collectSnapshot();
            Log.i(TAG, "nonce=" + nonce
                    + " blackbox_probe_complete=true "
                    + snapshot.logFields());
        } catch (PackageManager.NameNotFoundException
                | NoSuchAlgorithmException
                | RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " blackbox_probe_complete=false"
                    + " blackbox_runtime_probe_passed=false"
                    + " private_vendor_api_probed=false"
                    + " device_nodes_scanned=false"
                    + " target_hardware_validated=false"
                    + " hardware_accessed=false", exception);
        } finally {
            finish();
        }
    }

    private BlackBoxEnvironmentSnapshot collectSnapshot()
            throws PackageManager.NameNotFoundException, NoSuchAlgorithmException {
        PackageManager packageManager = getPackageManager();
        ApplicationInfo applicationInfo = getApplicationInfo();
        PackageInfo packageInfo = packageManager.getPackageInfo(
                getPackageName(),
                PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_SIGNING_CERTIFICATES));
        NativeRuntimeProcessSnapshot nativeSnapshot =
                ((CentralBrainRuntimeApplication) getApplication())
                        .getNativeRuntimeSnapshot();

        String supportedAbis = String.join(",", Build.SUPPORTED_ABIS);
        String supported64BitAbis = String.join(",", Build.SUPPORTED_64_BIT_ABIS);
        boolean targetAbiSupported = Arrays.asList(Build.SUPPORTED_64_BIT_ABIS)
                .stream()
                .anyMatch(abi -> "arm64-v8a".equals(abi) || "x86_64".equals(abi));
        boolean ordinaryDataApp = applicationInfo.sourceDir != null
                && applicationInfo.sourceDir.startsWith("/data/app/");
        boolean appPrivateDataDir = applicationInfo.dataDir != null
                && (applicationInfo.dataDir.startsWith("/data/user/")
                        || applicationInfo.dataDir.startsWith("/data/data/"));

        return BlackBoxEnvironmentSnapshot.create(
                Build.VERSION.SDK_INT,
                Process.is64Bit(),
                targetAbiSupported,
                ordinaryDataApp,
                appPrivateDataDir,
                packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE),
                applicationInfo.uid,
                packageInfo.getLongVersionCode(),
                packageInfo.versionName,
                supportedAbis,
                supported64BitAbis,
                applicationInfo.sourceDir,
                applicationInfo.nativeLibraryDir,
                applicationInfo.dataDir,
                signerDigests(packageInfo),
                Build.FINGERPRINT,
                nativeSnapshot.isRuntimeReady(),
                nativeSnapshot.isLibraryLoaded(),
                false,
                false,
                nativeSnapshot.isHardwareAccessed());
    }

    private static String signerDigests(PackageInfo packageInfo)
            throws NoSuchAlgorithmException {
        if (packageInfo.signingInfo == null) {
            throw new IllegalStateException("installed package signingInfo is unavailable");
        }
        Signature[] signers = packageInfo.signingInfo.getApkContentsSigners();
        if (signers == null || signers.length == 0) {
            throw new IllegalStateException("installed package signer set is empty");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        List<String> values = new ArrayList<>(signers.length);
        for (Signature signer : signers) {
            values.add(toHex(digest.digest(signer.toByteArray())));
            digest.reset();
        }
        Collections.sort(values);
        return String.join(",", values);
    }

    private static String toHex(byte[] value) {
        final char[] alphabet = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            int unsigned = item & 0xff;
            result.append(alphabet[unsigned >>> 4]);
            result.append(alphabet[unsigned & 0x0f]);
        }
        return result.toString();
    }
}
