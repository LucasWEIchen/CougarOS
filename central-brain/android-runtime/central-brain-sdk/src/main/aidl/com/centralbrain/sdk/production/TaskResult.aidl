package com.centralbrain.sdk.production;

parcelable TaskResult {
    int schemaVersion = 1;
    String taskId = "";
    int completionCode = 0;
    String replyText = "";
    String summary = "";
    long completedAtElapsedRealtimeMs = 0;
}
