package com.centralbrain.sdk.model;

// Receipt for an ephemeral debug multimodal input accepted by the runtime process.
parcelable DevelopmentModelInputReceipt {
    int schemaVersion = 1;
    String sessionId = "";
    String scenarioId = "";
    String inputText = "";
    String imageMimeType = "";
    String imageFileName = "";
    long imageByteCount = 0;
    String imageSha256 = "";
    String inputAggregateDigest = "";
    long acceptedAtEpochMs = 0;
    String receiptDigest = "";
}
