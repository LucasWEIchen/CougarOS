package com.centralbrain.sdk.session;

// Req IDs: S2-SES-001, APP-004, XSC-001, XSC-006, NV-G-003.
parcelable SessionHandle {
    int schemaVersion = 1;
    String sessionId = "";
    long acceptedAtEpochMs = 0;
    long expiresAtEpochMs = 0;
}
