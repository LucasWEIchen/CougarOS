package com.centralbrain.runtime;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.centralbrain.sdk.CentralBrainSdk;
import com.centralbrain.sdk.production.AgentTaskRequest;
import com.centralbrain.sdk.production.ICentralBrainRuntime;
import com.centralbrain.sdk.production.ICentralBrainTaskCallback;
import com.centralbrain.sdk.production.TaskFailure;
import com.centralbrain.sdk.production.TaskHandle;
import com.centralbrain.sdk.production.TaskResult;
import com.centralbrain.sdk.production.TaskUpdate;

import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Typed R2 production Binder with deterministic, hardware-free task execution.
 * Req IDs: XSC-001, XSC-004, XSC-006, NV-F-001, NV-G-003, NV-G-006, NV-P-002.
 */
public final class CentralBrainRuntimeService extends Service {
    public static final String BIND_PERMISSION = "com.centralbrain.permission.BIND_RUNTIME";
    public static final String RUNTIME_STAGE = CentralBrainSdk.EVOLUTION_STAGE;

    private static final String TAG = "CentralBrainRuntime";
    private static final long START_DELAY_MS = 40;
    private static final long COMPLETE_DELAY_MS = 160;
    private static final int MAX_TEXT_LENGTH = 4096;

    private final AtomicLong nextTaskId = new AtomicLong(1);
    private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "central-brain-task-runner");
        thread.setDaemon(true);
        return thread;
    });

    private final ICentralBrainRuntime.Stub binder = new ICentralBrainRuntime.Stub() {
        @Override
        public int getProtocolVersion() {
            return ICentralBrainRuntime.INTERFACE_VERSION;
        }

        @Override
        public String getProtocolHash() {
            return ICentralBrainRuntime.INTERFACE_HASH;
        }

        @Override
        public TaskHandle submitAgentTask(
                AgentTaskRequest request,
                ICentralBrainTaskCallback callback) {
            validateRequest(request, callback);

            String taskId = "task-" + nextTaskId.getAndIncrement();
            TaskRecord record = new TaskRecord(
                    taskId,
                    request,
                    callback,
                    Binder.getCallingUid(),
                    SystemClock.elapsedRealtime());
            record.deathRecipient = () -> handleCallbackDeath(record);

            try {
                callback.asBinder().linkToDeath(record.deathRecipient, 0);
            } catch (RemoteException exception) {
                throw new IllegalStateException("callback binder is already dead");
            }

            tasks.put(taskId, record);
            executor.schedule(() -> startTask(record), START_DELAY_MS, TimeUnit.MILLISECONDS);
            Log.i(TAG, "accepted taskId=" + taskId + " callerUid=" + record.callerUid
                    + " hardware_accessed=false");
            return copyHandle(record.handle);
        }

        @Override
        public boolean cancelTask(TaskHandle handle, int reasonCode) {
            validateCancelReason(reasonCode);
            TaskRecord record = findRecord(handle);
            return record != null && cancelRecord(record, reasonCode, true);
        }

        @Override
        public TaskUpdate getTaskStatus(TaskHandle handle) {
            TaskRecord record = findRecord(handle);
            if (record == null) {
                return updateFor(
                        handle == null ? "" : safe(handle.taskId),
                        ICentralBrainRuntime.TASK_STATE_UNKNOWN,
                        0,
                        0,
                        "unknown task");
            }
            synchronized (record) {
                return copyUpdate(record.latestUpdate);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "created maturity=contract_defined hardware_accessed=false");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "started startId=" + startId + " hardware_accessed=false");
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "production binder requested hardware_accessed=false");
        return binder;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        tasks.values().forEach(this::unlinkCallbackDeath);
        tasks.clear();
        Log.i(TAG, "destroyed hardware_accessed=false");
        super.onDestroy();
    }

    private static void validateRequest(
            AgentTaskRequest request,
            ICentralBrainTaskCallback callback) {
        if (request == null || callback == null) {
            invalidArgument("request and callback are required");
        }
        if (request.schemaVersion != 1) {
            invalidArgument("unsupported request schemaVersion");
        }
        if (isBlank(request.clientRequestId)
                || isBlank(request.utterance)
                || isBlank(request.idempotencyKey)) {
            invalidArgument("clientRequestId, utterance and idempotencyKey are required");
        }
        if (tooLong(request.clientRequestId)
                || tooLong(request.sessionId)
                || tooLong(request.utterance)
                || tooLong(request.locale)
                || tooLong(request.idempotencyKey)) {
            invalidArgument("request text exceeds limit");
        }
        if (request.priority < 0 || request.priority > 3) {
            invalidArgument("priority must be in range 0..3");
        }
        if (request.deadlineElapsedRealtimeMs > 0
                && request.deadlineElapsedRealtimeMs <= SystemClock.elapsedRealtime()) {
            throw new IllegalArgumentException("request deadline has elapsed");
        }
    }

    private static void invalidArgument(String message) {
        throw new IllegalArgumentException(message);
    }

    private static void validateCancelReason(int reasonCode) {
        if (reasonCode < ICentralBrainRuntime.CANCEL_REASON_USER
                || reasonCode > ICentralBrainRuntime.CANCEL_REASON_POLICY) {
            invalidArgument("unsupported cancellation reason");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean tooLong(String value) {
        return value != null && value.length() > MAX_TEXT_LENGTH;
    }

    private TaskRecord findRecord(TaskHandle handle) {
        if (handle == null || isBlank(handle.taskId)) {
            return null;
        }
        return tasks.get(handle.taskId);
    }

    private void startTask(TaskRecord record) {
        TaskUpdate accepted;
        TaskUpdate running;
        synchronized (record) {
            if (record.terminal) {
                return;
            }
            accepted = copyUpdate(record.latestUpdate);
            record.latestUpdate = updateFor(
                    record.handle.taskId,
                    ICentralBrainRuntime.TASK_STATE_RUNNING,
                    50,
                    record.latestUpdate.sequence + 1,
                    "deterministic stub running");
            running = copyUpdate(record.latestUpdate);
        }

        deliverUpdate(record, accepted);
        synchronized (record) {
            if (record.terminal) {
                return;
            }
        }
        deliverUpdate(record, running);
        synchronized (record) {
            if (record.terminal) {
                return;
            }
        }
        executor.schedule(() -> completeTask(record), COMPLETE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void completeTask(TaskRecord record) {
        TaskUpdate completed;
        TaskResult result;
        synchronized (record) {
            if (record.terminal) {
                return;
            }
            record.terminal = true;
            record.latestUpdate = updateFor(
                    record.handle.taskId,
                    ICentralBrainRuntime.TASK_STATE_COMPLETED,
                    100,
                    record.latestUpdate.sequence + 1,
                    "deterministic stub completed");
            completed = copyUpdate(record.latestUpdate);
            result = new TaskResult();
            result.taskId = record.handle.taskId;
            result.completionCode = ICentralBrainRuntime.ERROR_NONE;
            result.replyText = "Deterministic Binder reply: " + safe(record.request.utterance);
            result.summary = "typed Binder task completed without hardware access";
            result.completedAtElapsedRealtimeMs = SystemClock.elapsedRealtime();
        }

        deliverUpdate(record, completed);
        try {
            record.callback.onTaskCompleted(result);
        } catch (RemoteException exception) {
            Log.w(TAG, "completion callback failed taskId=" + record.handle.taskId, exception);
        } finally {
            unlinkCallbackDeath(record);
        }
    }

    private boolean cancelRecord(TaskRecord record, int reasonCode, boolean notifyClient) {
        TaskUpdate cancelled;
        TaskFailure failure;
        synchronized (record) {
            if (record.cancellationAccepted) {
                return true;
            }
            if (record.terminal) {
                return false;
            }
            record.cancellationAccepted = true;
            record.terminal = true;
            record.latestUpdate = updateFor(
                    record.handle.taskId,
                    ICentralBrainRuntime.TASK_STATE_CANCELLED,
                    record.latestUpdate.progressPercent,
                    record.latestUpdate.sequence + 1,
                    "cancelled reason=" + reasonCode);
            cancelled = copyUpdate(record.latestUpdate);
            failure = new TaskFailure();
            failure.taskId = record.handle.taskId;
            failure.errorCode = ICentralBrainRuntime.ERROR_CANCELLED;
            failure.errorMessage = "task cancelled reason=" + reasonCode;
            failure.retryable = false;
        }

        if (notifyClient) {
            executor.execute(() -> notifyCancellation(record, cancelled, failure));
        } else {
            unlinkCallbackDeath(record);
        }
        Log.i(TAG, "cancelled taskId=" + record.handle.taskId + " reason=" + reasonCode
                + " hardware_accessed=false");
        return true;
    }

    private void notifyCancellation(
            TaskRecord record,
            TaskUpdate cancelled,
            TaskFailure failure) {
        deliverUpdate(record, cancelled);
        try {
            record.callback.onTaskFailed(failure);
        } catch (RemoteException exception) {
            Log.w(TAG, "cancel callback failed taskId=" + record.handle.taskId, exception);
        } finally {
            unlinkCallbackDeath(record);
        }
    }

    private void deliverUpdate(TaskRecord record, TaskUpdate update) {
        try {
            record.callback.onTaskUpdate(update);
        } catch (RemoteException exception) {
            handleCallbackDeath(record);
        }
    }

    private void handleCallbackDeath(TaskRecord record) {
        boolean cancelled = cancelRecord(
                record,
                ICentralBrainRuntime.CANCEL_REASON_CLIENT_DIED,
                false);
        if (cancelled) {
            Log.i(TAG, "callback died taskId=" + record.handle.taskId
                    + " hardware_accessed=false");
        }
    }

    private void unlinkCallbackDeath(TaskRecord record) {
        if (record.deathRecipient == null) {
            return;
        }
        try {
            record.callback.asBinder().unlinkToDeath(record.deathRecipient, 0);
        } catch (NoSuchElementException ignored) {
            // The remote callback already died and Binder removed the recipient.
        }
    }

    private static TaskHandle copyHandle(TaskHandle source) {
        TaskHandle copy = new TaskHandle();
        copy.schemaVersion = source.schemaVersion;
        copy.taskId = source.taskId;
        copy.acceptedAtElapsedRealtimeMs = source.acceptedAtElapsedRealtimeMs;
        return copy;
    }

    private static TaskUpdate copyUpdate(TaskUpdate source) {
        return updateFor(
                source.taskId,
                source.state,
                source.progressPercent,
                source.sequence,
                source.message);
    }

    private static TaskUpdate updateFor(
            String taskId,
            int state,
            int progressPercent,
            long sequence,
            String message) {
        TaskUpdate update = new TaskUpdate();
        update.taskId = safe(taskId);
        update.state = state;
        update.progressPercent = progressPercent;
        update.sequence = sequence;
        update.message = safe(message);
        return update;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class TaskRecord {
        final AgentTaskRequest request;
        final ICentralBrainTaskCallback callback;
        final TaskHandle handle;
        final int callerUid;
        TaskUpdate latestUpdate;
        IBinder.DeathRecipient deathRecipient;
        boolean terminal;
        boolean cancellationAccepted;

        TaskRecord(
                String taskId,
                AgentTaskRequest request,
                ICentralBrainTaskCallback callback,
                int callerUid,
                long acceptedAtElapsedRealtimeMs) {
            this.request = request;
            this.callback = callback;
            this.callerUid = callerUid;
            this.handle = new TaskHandle();
            this.handle.taskId = taskId;
            this.handle.acceptedAtElapsedRealtimeMs = acceptedAtElapsedRealtimeMs;
            this.latestUpdate = updateFor(
                    taskId,
                    ICentralBrainRuntime.TASK_STATE_ACCEPTED,
                    0,
                    1,
                    "accepted");
        }
    }
}
