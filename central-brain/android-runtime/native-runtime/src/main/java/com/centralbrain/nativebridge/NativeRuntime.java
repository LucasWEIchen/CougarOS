package com.centralbrain.nativebridge;

public final class NativeRuntime implements AutoCloseable {
    public static final int ABI_VERSION = 1;
    public static final int MAX_SLOTS = 64;

    static {
        System.loadLibrary("central_brain_native");
    }

    private long handle;

    public NativeRuntime(int maxActiveSlots) {
        if (maxActiveSlots < 1 || maxActiveSlots > MAX_SLOTS) {
            throw new IllegalArgumentException("maxActiveSlots must be in [1, 64]");
        }
        handle = nativeCreate(ABI_VERSION, maxActiveSlots);
        if (handle == 0L) {
            throw new IllegalStateException("native runtime returned a null handle");
        }
    }

    public synchronized NativeRuntimeSnapshot snapshot() {
        return NativeRuntimeSnapshot.fromNative(nativeSnapshot(requireOpen()));
    }

    public synchronized long acquireSlot() {
        long result = nativeAcquireSlot(requireOpen());
        if (result <= 0L) {
            int status = Math.toIntExact(-result);
            throw new IllegalStateException("native acquire failed: " + NativeRuntimeStatus.nameOf(status));
        }
        return result;
    }

    public synchronized boolean releaseSlot(long leaseId) {
        if (leaseId <= 0L) {
            throw new IllegalArgumentException("leaseId must be positive");
        }
        int status = nativeReleaseSlot(requireOpen(), leaseId);
        if (status == NativeRuntimeStatus.OK) {
            return true;
        }
        if (status == NativeRuntimeStatus.NOT_FOUND) {
            return false;
        }
        throw new IllegalStateException("native release failed: " + NativeRuntimeStatus.nameOf(status));
    }

    public synchronized boolean isClosed() {
        return handle == 0L;
    }

    @Override
    public synchronized void close() {
        if (handle == 0L) {
            return;
        }
        int status = nativeDestroy(handle);
        if (status != NativeRuntimeStatus.OK) {
            throw new IllegalStateException("native destroy failed: " + NativeRuntimeStatus.nameOf(status));
        }
        handle = 0L;
    }

    private long requireOpen() {
        if (handle == 0L) {
            throw new IllegalStateException("native runtime is closed");
        }
        return handle;
    }

    private static native long nativeCreate(int abiVersion, int maxSlots);

    private static native long[] nativeSnapshot(long handle);

    private static native long nativeAcquireSlot(long handle);

    private static native int nativeReleaseSlot(long handle, long leaseId);

    private static native int nativeDestroy(long handle);
}
