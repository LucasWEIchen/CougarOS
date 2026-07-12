package com.centralbrain.nativebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NativeRuntimeSnapshotTest {
    @Test
    public void parsesFailClosedSnapshot() {
        NativeRuntimeSnapshot snapshot = NativeRuntimeSnapshot.fromNative(new long[] {
                1L, 1L, 4L, 0L, 0L, 0L, 0L, 7L, 0L, 64L
        });

        assertEquals(1, snapshot.getAbiVersion());
        assertTrue(snapshot.isInitialized());
        assertEquals(4, snapshot.getMaxActiveSlots());
        assertEquals(0, snapshot.getActiveSlots());
        assertFalse(snapshot.isSoftwareProviderAvailable());
        assertFalse(snapshot.isVendorNpuProviderAvailable());
        assertFalse(snapshot.isHardwareAccessed());
        assertTrue(snapshot.toDiagnosticString().contains("native_hardware_accessed=false"));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsHardwareClaim() {
        NativeRuntimeSnapshot.fromNative(new long[] {
                1L, 1L, 4L, 0L, 0L, 0L, 1L, 1L, 0L, 64L
        });
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsVendorProviderClaim() {
        NativeRuntimeSnapshot.fromNative(new long[] {
                1L, 1L, 4L, 0L, 0L, 1L, 0L, 1L, 0L, 64L
        });
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsNonBooleanProviderValue() {
        NativeRuntimeSnapshot.fromNative(new long[] {
                1L, 1L, 4L, 0L, 2L, 0L, 0L, 1L, 0L, 64L
        });
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnknownNativeStatus() {
        NativeRuntimeSnapshot.fromNative(new long[] {
                1L, 1L, 4L, 0L, 0L, 0L, 0L, 1L, 9L, 64L
        });
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsImplementationCapacityDrift() {
        NativeRuntimeSnapshot.fromNative(new long[] {
                1L, 1L, 4L, 0L, 0L, 0L, 0L, 1L, 0L, 65L
        });
    }
}
