package com.centralbrain.sdk.model;

// Ephemeral debug projection. It is never part of the frozen Orchestration V1 snapshot.
parcelable DevelopmentModelProjection {
    int schemaVersion = 1;
    String sessionId = "";
    String scenarioId = "";
    String providerId = "";
    String assistantDisplayText = "";
    long latencyMs = 0;
    String outputDigest = "";
    String projectionDigest = "";
    long completedAtEpochMs = 0;
}
