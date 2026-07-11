package com.centralbrain.sdk.diagnostics;

import com.centralbrain.sdk.diagnostics.DiagnosticRecord;

parcelable DiagnosticPage {
    int schemaVersion = 1;
    DiagnosticRecord[] records = {};
    String nextCursor = "";
    boolean hasMore = false;
    long generatedAtElapsedRealtimeMs = 0;
}
