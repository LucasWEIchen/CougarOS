package com.centralbrain.sdk.event;

import com.centralbrain.sdk.event.RuntimeEvent;

// Event V2 always returns a resumable cursor, including terminal pages.
// Req IDs: S2-EVT-001, FW-U-003, NV-G-004/006/007, XSC-001/006.
parcelable EventPageV2 {
    int schemaVersion = 2;
    String sessionId = "";
    String requestCursor = "";
    long afterSequence = 0;
    RuntimeEvent[] events = {};
    String resumeCursor = "";
    long resumeSequence = 0;
    boolean hasMore = false;
    boolean redactionApplied = false;
    long generatedAtEpochMs = 0;
}
