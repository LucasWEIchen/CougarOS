package com.centralbrain.sdk.production;

parcelable TaskFailure {
    int schemaVersion = 1;
    String taskId = "";
    int errorCode = 0;
    String errorMessage = "";
    boolean retryable = false;
}
