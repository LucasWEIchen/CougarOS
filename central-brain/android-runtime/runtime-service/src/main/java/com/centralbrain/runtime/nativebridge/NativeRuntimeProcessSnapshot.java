package com.centralbrain.runtime.nativebridge;

import com.centralbrain.nativebridge.NativeRuntime;
import com.centralbrain.nativebridge.NativeRuntimeSnapshot;
import com.centralbrain.nativebridge.NativeRuntimeStatus;

import java.util.Locale;

/** Immutable process-level visibility for the native runtime lifecycle. */
public final class NativeRuntimeProcessSnapshot {
    public enum LifecycleState {
        READY,
        UNAVAILABLE,
        CLOSED
    }

    public enum DetailCode {
        READY,
        NATIVE_LINKAGE_ERROR,
        NATIVE_INITIALIZATION_ERROR,
        NATIVE_QUERY_ERROR,
        NATIVE_CLOSE_ERROR,
        PROCESS_CLOSED
    }

    private final LifecycleState lifecycleState;
    private final DetailCode detailCode;
    private final boolean libraryLoaded;
    private final boolean initialized;
    private final int abiVersion;
    private final int maxActiveSlots;
    private final int activeSlots;
    private final long generation;
    private final int lastStatus;
    private final boolean softwareProviderAvailable;
    private final boolean vendorNpuProviderAvailable;
    private final boolean hardwareAccessed;

    private NativeRuntimeProcessSnapshot(
            LifecycleState lifecycleState,
            DetailCode detailCode,
            boolean libraryLoaded,
            boolean initialized,
            int abiVersion,
            int maxActiveSlots,
            int activeSlots,
            long generation,
            int lastStatus,
            boolean softwareProviderAvailable,
            boolean vendorNpuProviderAvailable,
            boolean hardwareAccessed) {
        this.lifecycleState = lifecycleState;
        this.detailCode = detailCode;
        this.libraryLoaded = libraryLoaded;
        this.initialized = initialized;
        this.abiVersion = abiVersion;
        this.maxActiveSlots = maxActiveSlots;
        this.activeSlots = activeSlots;
        this.generation = generation;
        this.lastStatus = lastStatus;
        this.softwareProviderAvailable = softwareProviderAvailable;
        this.vendorNpuProviderAvailable = vendorNpuProviderAvailable;
        this.hardwareAccessed = hardwareAccessed;
        validate();
    }

    static NativeRuntimeProcessSnapshot ready(NativeRuntimeSnapshot nativeSnapshot) {
        if (nativeSnapshot == null) {
            throw new IllegalArgumentException("nativeSnapshot is required");
        }
        return ready(
                nativeSnapshot.getAbiVersion(),
                nativeSnapshot.isInitialized(),
                nativeSnapshot.getMaxActiveSlots(),
                nativeSnapshot.getActiveSlots(),
                nativeSnapshot.getGeneration(),
                nativeSnapshot.getLastStatus(),
                nativeSnapshot.isSoftwareProviderAvailable(),
                nativeSnapshot.isVendorNpuProviderAvailable(),
                nativeSnapshot.isHardwareAccessed());
    }

    static NativeRuntimeProcessSnapshot ready(
            int abiVersion,
            boolean initialized,
            int maxActiveSlots,
            int activeSlots,
            long generation,
            int lastStatus,
            boolean softwareProviderAvailable,
            boolean vendorNpuProviderAvailable,
            boolean hardwareAccessed) {
        return new NativeRuntimeProcessSnapshot(
                LifecycleState.READY,
                DetailCode.READY,
                true,
                initialized,
                abiVersion,
                maxActiveSlots,
                activeSlots,
                generation,
                lastStatus,
                softwareProviderAvailable,
                vendorNpuProviderAvailable,
                hardwareAccessed);
    }

    static NativeRuntimeProcessSnapshot unavailable(
            DetailCode detailCode,
            boolean libraryLoaded) {
        return new NativeRuntimeProcessSnapshot(
                LifecycleState.UNAVAILABLE,
                detailCode,
                libraryLoaded,
                false,
                libraryLoaded ? NativeRuntime.ABI_VERSION : 0,
                0,
                0,
                0L,
                NativeRuntimeStatus.INTERNAL_ERROR,
                false,
                false,
                false);
    }

    static NativeRuntimeProcessSnapshot closed() {
        return new NativeRuntimeProcessSnapshot(
                LifecycleState.CLOSED,
                DetailCode.PROCESS_CLOSED,
                true,
                false,
                NativeRuntime.ABI_VERSION,
                0,
                0,
                0L,
                NativeRuntimeStatus.CLOSED,
                false,
                false,
                false);
    }

    private void validate() {
        if (lifecycleState == null || detailCode == null) {
            throw new IllegalArgumentException("native process lifecycle fields are required");
        }
        if (softwareProviderAvailable || vendorNpuProviderAvailable || hardwareAccessed) {
            throw new IllegalStateException("native process must remain hardware/provider empty");
        }
        if (lifecycleState == LifecycleState.READY) {
            if (detailCode != DetailCode.READY
                    || !libraryLoaded
                    || !initialized
                    || abiVersion != NativeRuntime.ABI_VERSION
                    || maxActiveSlots < 1
                    || maxActiveSlots > NativeRuntime.MAX_SLOTS
                    || activeSlots < 0
                    || activeSlots > maxActiveSlots
                    || generation < 1L
                    || lastStatus < NativeRuntimeStatus.OK
                    || lastStatus > NativeRuntimeStatus.INTERNAL_ERROR) {
                throw new IllegalStateException("invalid ready native process snapshot");
            }
            return;
        }
        if (lifecycleState == LifecycleState.UNAVAILABLE) {
            boolean expectedLibraryLoaded = detailCode
                    != DetailCode.NATIVE_LINKAGE_ERROR;
            if (detailCode == DetailCode.READY
                    || detailCode == DetailCode.PROCESS_CLOSED
                    || libraryLoaded != expectedLibraryLoaded
                    || initialized
                    || abiVersion != (libraryLoaded ? NativeRuntime.ABI_VERSION : 0)
                    || maxActiveSlots != 0
                    || activeSlots != 0
                    || generation != 0L
                    || lastStatus != NativeRuntimeStatus.INTERNAL_ERROR) {
                throw new IllegalStateException("invalid unavailable native process snapshot");
            }
            return;
        }
        if (detailCode != DetailCode.PROCESS_CLOSED
                || !libraryLoaded
                || initialized
                || abiVersion != NativeRuntime.ABI_VERSION
                || maxActiveSlots != 0
                || activeSlots != 0
                || generation != 0L
                || lastStatus != NativeRuntimeStatus.CLOSED) {
            throw new IllegalStateException("invalid closed native process snapshot");
        }
    }

    public LifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public DetailCode getDetailCode() {
        return detailCode;
    }

    public boolean isRuntimeReady() {
        return lifecycleState == LifecycleState.READY;
    }

    public boolean isLibraryLoaded() {
        return libraryLoaded;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int getAbiVersion() {
        return abiVersion;
    }

    public int getMaxActiveSlots() {
        return maxActiveSlots;
    }

    public int getActiveSlots() {
        return activeSlots;
    }

    public long getGeneration() {
        return generation;
    }

    public int getLastStatus() {
        return lastStatus;
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

    public String diagnosticDetail() {
        return String.format(
                Locale.ROOT,
                "native_runtime_process_ready=%s;native_runtime_process_lifecycle=%s;"
                        + "native_runtime_detail_code=%s;native_library_loaded=%s;"
                        + "native_runtime_initialized=%s;native_runtime_abi_version=%d;"
                        + "native_runtime_max_slots=%d;native_runtime_active_slots=%d;"
                        + "native_runtime_generation=%d;native_runtime_last_status=%s;"
                        + "native_software_provider_available=%s;"
                        + "native_vendor_npu_provider_available=%s;"
                        + "native_runtime_dispatch_enabled=false;"
                        + "native_hardware_accessed=%s;hardware_accessed=false",
                isRuntimeReady(),
                lifecycleState,
                detailCode,
                libraryLoaded,
                initialized,
                abiVersion,
                maxActiveSlots,
                activeSlots,
                generation,
                NativeRuntimeStatus.nameOf(lastStatus),
                softwareProviderAvailable,
                vendorNpuProviderAvailable,
                hardwareAccessed);
    }

    public String logFields() {
        return diagnosticDetail().replace(';', ' ');
    }
}
