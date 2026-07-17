package com.centralbrain.sdk.effect;

// Stage 2 P1-W04 compensation eligibility handle. Undo remains a new governed operation.
// Req IDs: S2-EFF-001, S2-SAF-001, S2-UX-003, FW-U-004, NV-F-009, NV-G-005, NV-G-006.
parcelable UndoHandle {
    int schemaVersion = 1;
    String undoId = "";
    String sessionId = "";
    String effectId = "";
    String sourceObservationId = "";
    String capabilityId = "";
    String planDigest = "";
    String verifiedObservationDigest = "";
    String compensationDigest = "";
    String handleDigest = "";
    long issuedContextVersion = 0;
    int state = 0;
    String reasonCode = "";
    long createdAtEpochMs = 0;
    long expiresAtEpochMs = 0;
}
