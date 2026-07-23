package com.centralbrain.sdk.model;

// Ephemeral debug projection. It is never part of the frozen Orchestration V1 snapshot.
parcelable DevelopmentModelProjection {
    int schemaVersion = 2;
    String sessionId = "";
    String scenarioId = "";
    String providerId = "";
    String assistantDisplayText = "";
    long latencyMs = 0;
    String inputAggregateDigest = "";
    boolean imageConsumed = false;
    String[] admittedActions = {};
    String outputDigest = "";
    String projectionDigest = "";
    long completedAtEpochMs = 0;
}
