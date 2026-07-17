package com.centralbrain.runtime.simulation;

// Debug-only control plane. This source set and its Service are absent from release builds.
// Req IDs: S2-CTX-001, S2-ADP-001, DEL-001, DEL-003, DEL-004, DEL-005.
interface IDebugSimulationController {
    const int INTERFACE_VERSION = 1;
    const String INTERFACE_HASH = "1d1a3e58226118ce884110792af4d888f9d206a1340ea4e78dca6596447a74c2";

    const int DRIVING_STATE_UNKNOWN = 0;
    const int DRIVING_STATE_PARKED = 1;
    const int DRIVING_STATE_MOVING = 2;

    const int SCALAR_BOOLEAN = 1;
    const int SCALAR_INTEGER = 2;
    const int SCALAR_DECIMAL = 3;
    const int SCALAR_TEXT = 4;

    const int FAULT_NONE = 0;
    const int FAULT_DELAY = 1;
    const int FAULT_TIMEOUT = 2;
    const int FAULT_RETRYABLE_FAILURE = 3;
    const int FAULT_TERMINAL_FAILURE = 4;
    const int FAULT_READBACK_MISMATCH = 5;

    int getProtocolVersion();
    String getProtocolHash();
    long setDrivingState(int drivingState);
    long setSignal(String canonicalPath, String area, int scalarType,
            boolean booleanValue, long integerValue, double decimalValue, String textValue);
    long setAdapterFault(String adapterId, int faultMode, long durationMs);
    long advanceSimulationClock(long durationMs);
    long reset();
    long getRevision();
    int getDrivingState();
    int getSignalCount();
    int getFaultCount();
    long getSimulationElapsedRealtimeMs();
    int getAuditEntryCount();
    String getSnapshotDigest();
}
