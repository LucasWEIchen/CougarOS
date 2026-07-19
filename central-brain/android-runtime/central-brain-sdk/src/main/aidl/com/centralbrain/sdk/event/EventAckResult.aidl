package com.centralbrain.sdk.event;

// Stable acknowledgement result. Unknown/stale/future inputs fail closed.
parcelable EventAckResult {
    int schemaVersion = 2;
    int outcome = 0;
    String subscriptionId = "";
    long acknowledgedSequence = 0;
    String resumeCursor = "";
}
