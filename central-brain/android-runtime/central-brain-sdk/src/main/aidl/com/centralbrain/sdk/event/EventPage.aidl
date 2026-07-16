package com.centralbrain.sdk.event;

import com.centralbrain.sdk.event.RuntimeEvent;

// Stage 2 P1-W03 owner-scoped cursor page. Cursor tokens are opaque to clients.
// Req IDs: S2-SES-001, S2-EVT-001, FW-U-003, NV-F-009, NV-G-004, XSC-001, XSC-006.
parcelable EventPage {
    int schemaVersion = 1;
    String sessionId = "";
    String requestCursor = "";
    long afterSequence = 0;
    RuntimeEvent[] events = {};
    String nextCursor = "";
    long nextSequence = 0;
    boolean hasMore = false;
    boolean redactionApplied = false;
    long generatedAtEpochMs = 0;
}
