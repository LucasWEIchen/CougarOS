package com.centralbrain.sdk.production;

import com.centralbrain.sdk.production.AgentTaskRequest;
import com.centralbrain.sdk.production.ICentralBrainTaskCallback;
import com.centralbrain.sdk.production.TaskHandle;
import com.centralbrain.sdk.production.TaskUpdate;

// Production interface: typed task control only. Diagnostics are a separate Binder surface.
interface ICentralBrainRuntime {
    const int INTERFACE_VERSION = 1;
    const String INTERFACE_HASH = "55bed5957371d691221ba99032a07f7162f4699c6a0c6c3d1f5e1fcc5707f1f5";

    const int TASK_STATE_UNKNOWN = 0;
    const int TASK_STATE_ACCEPTED = 1;
    const int TASK_STATE_RUNNING = 2;
    const int TASK_STATE_COMPLETED = 3;
    const int TASK_STATE_FAILED = 4;
    const int TASK_STATE_CANCELLED = 5;

    const int CANCEL_REASON_USER = 1;
    const int CANCEL_REASON_CLIENT_DIED = 2;
    const int CANCEL_REASON_DEADLINE = 3;
    const int CANCEL_REASON_POLICY = 4;

    const int ERROR_NONE = 0;
    const int ERROR_INVALID_ARGUMENT = 1;
    const int ERROR_DEADLINE_EXCEEDED = 2;
    const int ERROR_CANCELLED = 3;
    const int ERROR_SERVICE_DIED = 4;
    const int ERROR_INTERNAL = 5;

    int getProtocolVersion();
    String getProtocolHash();
    TaskHandle submitAgentTask(in AgentTaskRequest request, ICentralBrainTaskCallback callback);
    boolean cancelTask(in TaskHandle handle, int reasonCode);
    TaskUpdate getTaskStatus(in TaskHandle handle);
}
