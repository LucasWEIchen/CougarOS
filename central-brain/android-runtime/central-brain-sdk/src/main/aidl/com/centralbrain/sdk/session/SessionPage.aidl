package com.centralbrain.sdk.session;

import com.centralbrain.sdk.session.SessionSnapshot;

// Req IDs: S2-SES-001, S2-UX-001, APP-004, XSC-001, XSC-006, NV-G-003.
parcelable SessionPage {
    int schemaVersion = 1;
    SessionSnapshot[] sessions = {};
    String nextCursor = "";
    boolean hasMore = false;
    long generatedAtEpochMs = 0;
}
