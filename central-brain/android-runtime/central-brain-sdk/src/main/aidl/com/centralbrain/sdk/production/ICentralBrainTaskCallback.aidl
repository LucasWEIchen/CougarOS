package com.centralbrain.sdk.production;

import com.centralbrain.sdk.production.TaskFailure;
import com.centralbrain.sdk.production.TaskResult;
import com.centralbrain.sdk.production.TaskUpdate;

// Oneway callbacks must never block the Runtime Binder thread.
oneway interface ICentralBrainTaskCallback {
    void onTaskUpdate(in TaskUpdate update);
    void onTaskCompleted(in TaskResult result);
    void onTaskFailed(in TaskFailure failure);
}
