package com.centralbrain.sdk.event;

// Server-issued durable subscription identity. It carries no caller identity.
parcelable EventSubscriptionHandle {
    int schemaVersion = 2;
    String subscriptionId = "";
    String clientSubscriptionId = "";
    String sessionId = "";
    String resumeCursor = "";
    long acknowledgedSequence = 0;
    int state = 0;
    long updatedAtEpochMs = 0;
}
