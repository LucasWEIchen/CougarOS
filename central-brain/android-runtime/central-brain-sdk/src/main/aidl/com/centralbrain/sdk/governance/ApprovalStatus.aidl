package com.centralbrain.sdk.governance;

parcelable ApprovalStatus {
    int schemaVersion = 1;
    String approvalId = "";
    String actionId = "";
    int status = 0;
    int riskClass = 0;
    String reasonCode = "";
    long createdAtElapsedRealtimeMs = 0;
    long expiresAtElapsedRealtimeMs = 0;
    boolean grantSupported = false;
    boolean durable = false;
    boolean dispatchAllowed = false;
}
