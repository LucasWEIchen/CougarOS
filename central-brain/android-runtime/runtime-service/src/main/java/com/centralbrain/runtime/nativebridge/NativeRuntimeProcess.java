package com.centralbrain.runtime.nativebridge;

import com.centralbrain.nativebridge.NativeRuntime;

/** Process-owned native handle. Java remains the lifecycle and policy owner. */
public final class NativeRuntimeProcess implements AutoCloseable {
    private NativeRuntime runtime;
    private NativeRuntimeProcessSnapshot terminalSnapshot;

    private NativeRuntimeProcess(
            NativeRuntime runtime,
            NativeRuntimeProcessSnapshot terminalSnapshot) {
        this.runtime = runtime;
        this.terminalSnapshot = terminalSnapshot;
    }

    public static NativeRuntimeProcess start(int maxActiveSlots) {
        NativeRuntime runtime = null;
        try {
            runtime = new NativeRuntime(maxActiveSlots);
            NativeRuntimeProcessSnapshot snapshot =
                    NativeRuntimeProcessSnapshot.ready(runtime.snapshot());
            return new NativeRuntimeProcess(runtime, snapshot);
        } catch (LinkageError error) {
            return new NativeRuntimeProcess(
                    null,
                    NativeRuntimeProcessSnapshot.unavailable(
                            NativeRuntimeProcessSnapshot.DetailCode.NATIVE_LINKAGE_ERROR,
                            false));
        } catch (RuntimeException exception) {
            closeAfterFailedStart(runtime);
            return new NativeRuntimeProcess(
                    null,
                    NativeRuntimeProcessSnapshot.unavailable(
                            NativeRuntimeProcessSnapshot.DetailCode.NATIVE_INITIALIZATION_ERROR,
                            true));
        }
    }

    public synchronized NativeRuntimeProcessSnapshot snapshot() {
        if (runtime == null) {
            return terminalSnapshot;
        }
        try {
            return NativeRuntimeProcessSnapshot.ready(runtime.snapshot());
        } catch (RuntimeException exception) {
            bestEffortClose();
            terminalSnapshot = NativeRuntimeProcessSnapshot.unavailable(
                    NativeRuntimeProcessSnapshot.DetailCode.NATIVE_QUERY_ERROR,
                    true);
            return terminalSnapshot;
        }
    }

    @Override
    public synchronized void close() {
        if (runtime == null) {
            return;
        }
        try {
            runtime.close();
            runtime = null;
            terminalSnapshot = NativeRuntimeProcessSnapshot.closed();
        } catch (RuntimeException exception) {
            runtime = null;
            terminalSnapshot = NativeRuntimeProcessSnapshot.unavailable(
                    NativeRuntimeProcessSnapshot.DetailCode.NATIVE_CLOSE_ERROR,
                    true);
        }
    }

    private static void closeAfterFailedStart(NativeRuntime runtime) {
        if (runtime == null) {
            return;
        }
        try {
            runtime.close();
        } catch (RuntimeException ignored) {
            // Process teardown remains the final reclaim path after failed initialization.
        }
    }

    private void bestEffortClose() {
        try {
            runtime.close();
        } catch (RuntimeException ignored) {
            // The Android process owns the handle; process teardown is the final reclaim path.
        } finally {
            runtime = null;
        }
    }
}
