package com.centralbrain.sdk.session;

// Req IDs: S2-SES-001, S2-UX-001, APP-004, XSC-001, XSC-006, NV-G-003.
parcelable SessionQuery {
    int schemaVersion = 1;
    int stateFilter = -1;
    boolean includeTerminal = true;
    String cursor = "";
    int pageSize = 20;
}
