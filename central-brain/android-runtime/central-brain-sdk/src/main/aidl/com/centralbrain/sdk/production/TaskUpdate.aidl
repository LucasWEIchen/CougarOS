package com.centralbrain.sdk.production;

parcelable TaskUpdate {
    int schemaVersion = 1;
    String taskId = "";
    int state = 0;
    int progressPercent = 0;
    long sequence = 0;
    String message = "";
}
