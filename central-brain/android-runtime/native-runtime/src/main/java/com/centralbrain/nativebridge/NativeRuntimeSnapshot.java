package com.centralbrain.nativebridge;

import java.util.Locale;

public final class NativeRuntimeSnapshot {
    static final int FIELD_COUNT = 10;

    private final int abiVersion;
    private final boolean initialized;
    private final int maxActiveSlots;
    private final int activeSlots;
    private final boolean softwareProviderAvailable;
    private final boolean vendorNpuProviderAvailable;
    private final boolean hardwareAccessed;
    private final long generation;
    private final int lastStatus;
    private final int implementationMaxSlots;

    private NativeRuntimeSnapshot(long[] fields) {
        if (fields == null || fields.length != FIELD_COUNT) {
            throw new IllegalArgumentException("native snapshot field count changed");
        }
        abiVersion = Math.toIntExact(fields[0]);
        initialized = requireBoolean(fields[1], "initialized");
        maxActiveSlots = Math.toIntExact(fields[2]);
        activeSlots = Math.toIntExact(fields[3]);
        softwareProviderAvailable = requireBoolean(fields[4], "softwareProviderAvailable");
        vendorNpuProviderAvailable = requireBoolean(fields[5], "vendorNpuProviderAvailable");
        hardwareAccessed = requireBoolean(fields[6], "hardwareAccessed");
        generation = fields[7];
        lastStatus = Math.toIntExact(fields[8]);
        implementationMaxSlots = Math.toIntExact(fields[9]);

        if (abiVersion != NativeRuntime.ABI_VERSION
                || !initialized
                || maxActiveSlots < 1
                || maxActiveSlots > implementationMaxSlots
                || activeSlots < 0
                || activeSlots > maxActiveSlots
                || generation < 1L
                || lastStatus < NativeRuntimeStatus.OK
                || lastStatus > NativeRuntimeStatus.INTERNAL_ERROR
                || implementationMaxSlots != NativeRuntime.MAX_SLOTS
                || softwareProviderAvailable
                || vendorNpuProviderAvailable
                || hardwareAccessed) {
            throw new IllegalStateException("unsafe native runtime snapshot");
        }
    }

    static NativeRuntimeSnapshot fromNative(long[] fields) {
        return new NativeRuntimeSnapshot(fields);
    }

    private static boolean requireBoolean(long value, String fieldName) {
        if (value == 0L) {
            return false;
        }
        if (value == 1L) {
            return true;
        }
        throw new IllegalStateException("invalid native boolean: " + fieldName);
    }

    public int getAbiVersion() {
        return abiVersion;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int getMaxActiveSlots() {
        return maxActiveSlots;
    }

    public int getActiveSlots() {
        return activeSlots;
    }

    public boolean isSoftwareProviderAvailable() {
        return softwareProviderAvailable;
    }

    public boolean isVendorNpuProviderAvailable() {
        return vendorNpuProviderAvailable;
    }

    public boolean isHardwareAccessed() {
        return hardwareAccessed;
    }

    public long getGeneration() {
        return generation;
    }

    public int getLastStatus() {
        return lastStatus;
    }

    public String toDiagnosticString() {
        return String.format(
                Locale.ROOT,
                "native_runtime_abi_version=%d native_runtime_initialized=%s "
                        + "native_runtime_max_slots=%d native_runtime_active_slots=%d "
                        + "native_software_provider_available=%s "
                        + "native_vendor_npu_provider_available=%s "
                        + "native_hardware_accessed=%s native_runtime_generation=%d "
                        + "native_runtime_last_status=%s",
                abiVersion,
                initialized,
                maxActiveSlots,
                activeSlots,
                softwareProviderAvailable,
                vendorNpuProviderAvailable,
                hardwareAccessed,
                generation,
                NativeRuntimeStatus.nameOf(lastStatus));
    }
}
