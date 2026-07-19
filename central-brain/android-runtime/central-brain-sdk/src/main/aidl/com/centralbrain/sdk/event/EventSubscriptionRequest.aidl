package com.centralbrain.sdk.event;

// Durable owner/session-scoped Event V2 subscription request.
// Caller identity and authority are always derived from Binder.
parcelable EventSubscriptionRequest {
    int schemaVersion = 2;
    String clientSubscriptionId = "";
    String sessionId = "";
    String resumeCursor = "";
    long resumeSequence = 0;
    int queueCapacity = 0;
}
