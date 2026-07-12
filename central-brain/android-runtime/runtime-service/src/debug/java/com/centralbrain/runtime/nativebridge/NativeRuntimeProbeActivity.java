package com.centralbrain.runtime.nativebridge;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.nativebridge.NativeRuntime;
import com.centralbrain.nativebridge.NativeRuntimeSnapshot;
import com.centralbrain.runtime.BuildConfig;
import com.centralbrain.runtime.CentralBrainRuntimeApplication;

/** ADB-only native load, lease and close probe. */
public final class NativeRuntimeProbeActivity extends Activity {
    private static final String TAG = "CbNativeRuntimeProbe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BuildConfig.DEBUG) {
            throw new IllegalStateException("Native Runtime probe must remain debug-only");
        }
        String requestedNonce = getIntent().getStringExtra("nonce");
        runProbe(requestedNonce == null ? "missing" : requestedNonce);
        finish();
    }

    private void runProbe(String nonce) {
        NativeRuntime runtime = null;
        long firstLease = 0L;
        long secondLease = 0L;
        try {
            NativeRuntimeProcessSnapshot processSnapshot =
                    ((CentralBrainRuntimeApplication) getApplication())
                            .getNativeRuntimeSnapshot();
            runtime = new NativeRuntime(2);
            NativeRuntimeSnapshot initial = runtime.snapshot();
            firstLease = runtime.acquireSlot();
            secondLease = runtime.acquireSlot();

            boolean capacityRejected = false;
            try {
                runtime.acquireSlot();
            } catch (IllegalStateException expected) {
                capacityRejected = expected.getMessage() != null
                        && expected.getMessage().contains("CAPACITY_EXHAUSTED");
            }
            boolean busyCloseRejected = false;
            try {
                runtime.close();
            } catch (IllegalStateException expected) {
                busyCloseRejected = expected.getMessage() != null
                        && expected.getMessage().contains("BUSY");
            }

            NativeRuntimeSnapshot full = runtime.snapshot();
            boolean firstReleased = runtime.releaseSlot(firstLease);
            boolean duplicateReleaseRejected = !runtime.releaseSlot(firstLease);
            firstLease = 0L;
            boolean secondReleased = runtime.releaseSlot(secondLease);
            secondLease = 0L;
            NativeRuntimeSnapshot drained = runtime.snapshot();
            runtime.close();
            boolean closeVerified = runtime.isClosed();

            boolean passed = processSnapshot.isRuntimeReady()
                    && processSnapshot.isLibraryLoaded()
                    && processSnapshot.isInitialized()
                    && processSnapshot.getGeneration() == 1L
                    && !processSnapshot.isSoftwareProviderAvailable()
                    && !processSnapshot.isVendorNpuProviderAvailable()
                    && !processSnapshot.isHardwareAccessed()
                    && initial.getActiveSlots() == 0
                    && full.getActiveSlots() == 2
                    && capacityRejected
                    && busyCloseRejected
                    && firstReleased
                    && duplicateReleaseRejected
                    && secondReleased
                    && drained.getActiveSlots() == 0
                    && drained.getGeneration() == 5L
                    && !drained.isSoftwareProviderAvailable()
                    && !drained.isVendorNpuProviderAvailable()
                    && !drained.isHardwareAccessed()
                    && closeVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " native_runtime_probe_complete=true"
                    + " native_runtime_probe_passed=" + passed
                    + " native_runtime_process_ready=" + processSnapshot.isRuntimeReady()
                    + " native_library_loaded=" + processSnapshot.isLibraryLoaded()
                    + " native_runtime_initialized=" + processSnapshot.isInitialized()
                    + " native_runtime_abi_version=" + processSnapshot.getAbiVersion()
                    + " native_runtime_process_generation="
                    + processSnapshot.getGeneration()
                    + " native_runtime_capacity_rejected=" + capacityRejected
                    + " native_runtime_busy_close_rejected=" + busyCloseRejected
                    + " native_runtime_duplicate_release_rejected="
                    + duplicateReleaseRejected
                    + " native_runtime_drained=true"
                    + " native_runtime_close_verified=" + closeVerified
                    + " native_software_provider_available=false"
                    + " native_vendor_npu_provider_available=false"
                    + " native_runtime_dispatch_enabled=false"
                    + " native_hardware_accessed=false"
                    + " hardware_accessed=false");
        } catch (RuntimeException | LinkageError error) {
            Log.e(TAG, "nonce=" + nonce
                    + " native_runtime_probe_complete=false"
                    + " native_runtime_probe_passed=false"
                    + " error=" + error.getClass().getSimpleName()
                    + " native_vendor_npu_provider_available=false"
                    + " native_runtime_dispatch_enabled=false"
                    + " hardware_accessed=false", error);
        } finally {
            if (runtime != null && !runtime.isClosed()) {
                try {
                    if (firstLease > 0L) {
                        runtime.releaseSlot(firstLease);
                    }
                    if (secondLease > 0L) {
                        runtime.releaseSlot(secondLease);
                    }
                    runtime.close();
                } catch (RuntimeException ignored) {
                    Log.e(TAG, "nonce=" + nonce + " native_runtime_probe_cleanup_failed=true");
                }
            }
        }
    }
}
