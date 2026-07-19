package com.centralbrain.sdk.event;

// Monotonic ACK bound to a server subscription, session and opaque resume cursor.
parcelable EventAckRequest {
    int schemaVersion = 2;
    String subscriptionId = "";
    String sessionId = "";
    String resumeCursor = "";
    long acknowledgedSequence = 0;
}
