package com.centralbrain.sdk.governance;

parcelable ApprovalHandle {
    int schemaVersion = 1;
    String approvalId = "";
    String actionId = "";
    int status = 0;
    long createdAtElapsedRealtimeMs = 0;
    long expiresAtElapsedRealtimeMs = 0;
}
