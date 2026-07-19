package com.centralbrain.sdk.orchestration;

// Requests a governed compensation. It carries no compensation target or vehicle payload.
// Req IDs: S2-EFF-001, S2-SAF-001, S2-UX-003, NV-G-005/006/007.
parcelable UndoRequest {
    int schemaVersion = 1;
    String requestId = "";
    String sessionId = "";
    String undoId = "";
    String expectedProjectionDigest = "";
    long requestedAtEpochMs = 0;
}
