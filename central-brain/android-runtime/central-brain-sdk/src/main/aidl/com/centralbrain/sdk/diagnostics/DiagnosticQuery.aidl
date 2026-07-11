package com.centralbrain.sdk.diagnostics;

// Req IDs: XSC-005, XSC-006, NV-G-003, NV-G-007, NV-P-002.
parcelable DiagnosticQuery {
    int schemaVersion = 1;
    String category = "";
    String cursor = "";
    int pageSize = 50;
}
