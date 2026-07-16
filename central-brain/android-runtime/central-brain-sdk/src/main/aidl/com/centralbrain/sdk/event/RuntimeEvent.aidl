package com.centralbrain.sdk.event;

import com.centralbrain.sdk.event.ActionEvent;
import com.centralbrain.sdk.event.MessageEvent;
import com.centralbrain.sdk.event.ObservationEvent;

// Stage 2 P1-W03 immutable event identity. Corrections are appended as new events.
// Req IDs: S2-SES-001, S2-EVT-001, FW-U-003, NV-F-009, NV-G-004, XSC-001, XSC-006.
parcelable RuntimeEvent {
    int schemaVersion = 1;
    String eventId = "";
    long sequence = 0;
    String sessionId = "";
    String parentEventId = "";
    long parentSequence = 0;
    String type = "";
    int source = 0;
    long occurredAtEpochMs = 0;
    int privacyClass = 0;
    String payloadDigest = "";
    String eventDigest = "";
    int payloadKind = 0;
    ActionEvent action;
    ObservationEvent observation;
    MessageEvent message;
}
