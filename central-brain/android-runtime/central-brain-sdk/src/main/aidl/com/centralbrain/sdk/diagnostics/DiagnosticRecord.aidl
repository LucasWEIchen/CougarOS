package com.centralbrain.sdk.diagnostics;

parcelable DiagnosticRecord {
    int schemaVersion = 1;
    String recordType = "";
    String recordId = "";
    String summary = "";
    String detail = "";
    long sequence = 0;
    long observedAtElapsedRealtimeMs = 0;
}
