package com.centralbrain.runtime.blackbox;

import java.util.Locale;

/** Immutable debug evidence for an Android 13 black-box target. */
public final class BlackBoxEnvironmentSnapshot {
    public static final int REQUIRED_API_LEVEL = 33;

    private final int apiLevel;
    private final boolean process64Bit;
    private final boolean targetAbiSupported;
    private final boolean ordinaryDataApp;
    private final boolean appPrivateDataDir;
    private final boolean automotiveFeatureAdvertised;
    private final int applicationUid;
    private final long versionCode;
    private final String versionName;
    private final String supportedAbis;
    private final String supported64BitAbis;
    private final String sourceDir;
    private final String nativeLibraryDir;
    private final String dataDir;
    private final String signerSha256;
    private final String buildFingerprint;
    private final boolean nativeRuntimeReady;
    private final boolean nativeLibraryLoaded;
    private final boolean vendorApiProbed;
    private final boolean deviceNodesScanned;
    private final boolean hardwareAccessed;

    public static BlackBoxEnvironmentSnapshot create(
            int apiLevel,
            boolean process64Bit,
            boolean targetAbiSupported,
            boolean ordinaryDataApp,
            boolean appPrivateDataDir,
            boolean automotiveFeatureAdvertised,
            int applicationUid,
            long versionCode,
            String versionName,
            String supportedAbis,
            String supported64BitAbis,
            String sourceDir,
            String nativeLibraryDir,
            String dataDir,
            String signerSha256,
            String buildFingerprint,
            boolean nativeRuntimeReady,
            boolean nativeLibraryLoaded,
            boolean vendorApiProbed,
            boolean deviceNodesScanned,
            boolean hardwareAccessed) {
        return new BlackBoxEnvironmentSnapshot(
                apiLevel,
                process64Bit,
                targetAbiSupported,
                ordinaryDataApp,
                appPrivateDataDir,
                automotiveFeatureAdvertised,
                applicationUid,
                versionCode,
                versionName,
                supportedAbis,
                supported64BitAbis,
                sourceDir,
                nativeLibraryDir,
                dataDir,
                signerSha256,
                buildFingerprint,
                nativeRuntimeReady,
                nativeLibraryLoaded,
                vendorApiProbed,
                deviceNodesScanned,
                hardwareAccessed);
    }

    private BlackBoxEnvironmentSnapshot(
            int apiLevel,
            boolean process64Bit,
            boolean targetAbiSupported,
            boolean ordinaryDataApp,
            boolean appPrivateDataDir,
            boolean automotiveFeatureAdvertised,
            int applicationUid,
            long versionCode,
            String versionName,
            String supportedAbis,
            String supported64BitAbis,
            String sourceDir,
            String nativeLibraryDir,
            String dataDir,
            String signerSha256,
            String buildFingerprint,
            boolean nativeRuntimeReady,
            boolean nativeLibraryLoaded,
            boolean vendorApiProbed,
            boolean deviceNodesScanned,
            boolean hardwareAccessed) {
        this.apiLevel = apiLevel;
        this.process64Bit = process64Bit;
        this.targetAbiSupported = targetAbiSupported;
        this.ordinaryDataApp = ordinaryDataApp;
        this.appPrivateDataDir = appPrivateDataDir;
        this.automotiveFeatureAdvertised = automotiveFeatureAdvertised;
        this.applicationUid = applicationUid;
        this.versionCode = versionCode;
        this.versionName = requireValue(versionName, "versionName");
        this.supportedAbis = requireValue(supportedAbis, "supportedAbis");
        this.supported64BitAbis = requireValue(supported64BitAbis, "supported64BitAbis");
        this.sourceDir = requireValue(sourceDir, "sourceDir");
        this.nativeLibraryDir = requireValue(nativeLibraryDir, "nativeLibraryDir");
        this.dataDir = requireValue(dataDir, "dataDir");
        this.signerSha256 = requireValue(signerSha256, "signerSha256");
        this.buildFingerprint = requireValue(buildFingerprint, "buildFingerprint");
        this.nativeRuntimeReady = nativeRuntimeReady;
        this.nativeLibraryLoaded = nativeLibraryLoaded;
        this.vendorApiProbed = vendorApiProbed;
        this.deviceNodesScanned = deviceNodesScanned;
        this.hardwareAccessed = hardwareAccessed;
        validate();
    }

    private void validate() {
        if (apiLevel < 1 || applicationUid < 10000 || versionCode < 1L) {
            throw new IllegalArgumentException("invalid Android package identity");
        }
        if (!signerSha256.matches("[0-9a-f]{64}(,[0-9a-f]{64})*")) {
            throw new IllegalArgumentException("signerSha256 must contain canonical digests");
        }
        if (vendorApiProbed || deviceNodesScanned || hardwareAccessed) {
            throw new IllegalStateException("black-box probe must remain public-API only");
        }
    }

    private static String requireValue(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    public boolean isAccepted() {
        return apiLevel == REQUIRED_API_LEVEL
                && process64Bit
                && targetAbiSupported
                && ordinaryDataApp
                && appPrivateDataDir
                && nativeRuntimeReady
                && nativeLibraryLoaded;
    }

    public String getSignerSha256() {
        return signerSha256;
    }

    public boolean isAutomotiveFeatureAdvertised() {
        return automotiveFeatureAdvertised;
    }

    public String logFields() {
        return String.format(
                Locale.ROOT,
                "blackbox_runtime_probe_passed=%s android_api=%d process_64_bit=%s "
                        + "target_abi_supported=%s ordinary_data_app=%s "
                        + "app_private_data_dir_verified=%s automotive_feature_advertised=%s "
                        + "application_uid=%d version_code=%d version_name=%s "
                        + "supported_abis=%s supported_64_bit_abis=%s source_dir=%s "
                        + "native_library_dir=%s data_dir=%s installed_signer_sha256=%s "
                        + "build_fingerprint=%s native_runtime_process_ready=%s "
                        + "native_library_loaded=%s private_vendor_api_probed=%s "
                        + "device_nodes_scanned=%s target_hardware_validated=false "
                        + "native_vendor_npu_provider_available=false "
                        + "native_runtime_dispatch_enabled=false hardware_accessed=%s",
                isAccepted(),
                apiLevel,
                process64Bit,
                targetAbiSupported,
                ordinaryDataApp,
                appPrivateDataDir,
                automotiveFeatureAdvertised,
                applicationUid,
                versionCode,
                sanitize(versionName),
                sanitize(supportedAbis),
                sanitize(supported64BitAbis),
                sanitize(sourceDir),
                sanitize(nativeLibraryDir),
                sanitize(dataDir),
                signerSha256,
                sanitize(buildFingerprint),
                nativeRuntimeReady,
                nativeLibraryLoaded,
                vendorApiProbed,
                deviceNodesScanned,
                hardwareAccessed);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[\\s\\r\\n]+", "_");
    }
}
