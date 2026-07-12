package com.centralbrain.runtime.nativebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.centralbrain.nativebridge.NativeRuntimeStatus;

import org.junit.Test;

public final class NativeRuntimeProcessSnapshotTest {
    @Test
    public void readySnapshotIsStrictAndHardwareEmpty() {
        NativeRuntimeProcessSnapshot snapshot = NativeRuntimeProcessSnapshot.ready(
                1, true, 4, 0, 1L, NativeRuntimeStatus.OK, false, false, false);

        assertTrue(snapshot.isRuntimeReady());
        assertTrue(snapshot.isLibraryLoaded());
        assertTrue(snapshot.isInitialized());
        assertEquals(4, snapshot.getMaxActiveSlots());
        assertEquals(0, snapshot.getActiveSlots());
        assertFalse(snapshot.isSoftwareProviderAvailable());
        assertFalse(snapshot.isVendorNpuProviderAvailable());
        assertFalse(snapshot.isHardwareAccessed());
        assertTrue(snapshot.diagnosticDetail().contains("native_runtime_process_ready=true"));
        assertTrue(snapshot.diagnosticDetail().contains("native_runtime_dispatch_enabled=false"));
    }

    @Test
    public void unavailableSnapshotIsFailClosed() {
        NativeRuntimeProcessSnapshot snapshot = NativeRuntimeProcessSnapshot.unavailable(
                NativeRuntimeProcessSnapshot.DetailCode.NATIVE_LINKAGE_ERROR,
                false);

        assertFalse(snapshot.isRuntimeReady());
        assertFalse(snapshot.isLibraryLoaded());
        assertEquals(
                NativeRuntimeProcessSnapshot.LifecycleState.UNAVAILABLE,
                snapshot.getLifecycleState());
        assertTrue(snapshot.diagnosticDetail().contains("NATIVE_LINKAGE_ERROR"));
        assertTrue(snapshot.diagnosticDetail().contains(
                "native_vendor_npu_provider_available=false"));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsHardwareClaim() {
        NativeRuntimeProcessSnapshot.ready(
                1, true, 4, 0, 1L, NativeRuntimeStatus.OK, false, false, true);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsCapacityDrift() {
        NativeRuntimeProcessSnapshot.ready(
                1, true, 4, 5, 1L, NativeRuntimeStatus.OK, false, false, false);
    }
}
