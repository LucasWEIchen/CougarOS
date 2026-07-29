package com.centralbrain.sdk.model;

// Receipt for an ephemeral debug text or multimodal input accepted by the runtime process.
parcelable DevelopmentModelInputReceipt {
    int schemaVersion = 2;
    int inputMode = 0;
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
