package com.centralbrain.runtime;

import android.app.Service;
import android.content.Intent;
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
import com.centralbrain.runtime.identity.AndroidCallerIdentityResolver;
import com.centralbrain.runtime.identity.CallerIdentitySnapshot;
import com.centralbrain.runtime.supervisor.JobSupervisor;

import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Typed production Binder with a bounded R3 Job Supervisor and hardware-free execution.
 * Req IDs: XSC-001, XSC-004, XSC-006, NV-F-001, FW-U-007, NV-G-003,
 * NV-G-005, NV-G-006, NV-G-007, NV-P-002.
 */
public final class CentralBrainRuntimeService extends Service {
    public static final String BIND_PERMISSION = "com.centralbrain.permission.BIND_RUNTIME";
    public static final String RUNTIME_STAGE = CentralBrainSdk.EVOLUTION_STAGE;

    private static final String TAG = "CentralBrainRuntime";
    private static final long START_DELAY_MS = 40;
    private static final long COMPLETE_DELAY_MS = BuildConfig.DEBUG ? 3000 : 160;
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_TASK_RECORDS = 128;
    private static final long TERMINAL_RETENTION_MS = TimeUnit.MINUTES.toMillis(5);

    private final AtomicLong nextTaskId = new AtomicLong(1);
    private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    private final JobSupervisor jobSupervisor = new JobSupervisor(
            MAX_TASK_RECORDS,
            TERMINAL_RETENTION_MS,
            SystemClock::elapsedRealtime);
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
            CallerIdentitySnapshot caller = resolveTrustedCaller();
            validateRequest(request, callback);
            removeTaskRecords(jobSupervisor.pruneExpired());

            String taskId = "task-" + nextTaskId.getAndIncrement();
            JobSupervisor.Admission admission = jobSupervisor.admit(taskId, caller, "accepted");
            removeTaskRecords(admission.getEvictedTaskIds());
            TaskRecord record = new TaskRecord(
                    taskId,
                    request,
                    callback,
                    admission.getSnapshot().getAcceptedAtElapsedRealtimeMs());
            record.deathRecipient = () -> handleCallbackDeath(record);

            try {
                callback.asBinder().linkToDeath(record.deathRecipient, 0);
            } catch (RemoteException exception) {
                jobSupervisor.remove(taskId);
                throw new IllegalStateException("callback binder is already dead");
            }

            tasks.put(taskId, record);
            JobSupervisor.Snapshot current = jobSupervisor.find(taskId);
            if (current != null && !current.isTerminal()) {
                executor.schedule(() -> startTask(record), START_DELAY_MS, TimeUnit.MILLISECONDS);
            }
            Log.i(TAG, "accepted taskId=" + taskId + " " + caller.auditSummary()
                    + " hardware_accessed=false");
            return copyHandle(record.handle);
        }

        @Override
        public boolean cancelTask(TaskHandle handle, int reasonCode) {
            CallerIdentitySnapshot caller = resolveTrustedCaller();
            validateCancelReason(reasonCode);
            removeTaskRecords(jobSupervisor.pruneExpired());
            TaskRecord record = findRecord(handle);
            return record != null && cancelRecord(record, reasonCode, true, caller);
        }

        @Override
        public TaskUpdate getTaskStatus(TaskHandle handle) {
            CallerIdentitySnapshot caller = resolveTrustedCaller();
            removeTaskRecords(jobSupervisor.pruneExpired());
            TaskRecord record = findRecord(handle);
            JobSupervisor.Snapshot snapshot = record == null
                    ? null
                    : jobSupervisor.findOwned(record.handle.taskId, caller);
            if (snapshot == null) {
                return updateFor(
                        handle == null ? "" : safe(handle.taskId),
                        ICentralBrainRuntime.TASK_STATE_UNKNOWN,
                        0,
                        0,
                        "unknown task");
            }
            return updateFor(snapshot);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        identityResolver = new AndroidCallerIdentityResolver(this);
        Log.i(TAG, "created maturity=" + CentralBrainSdk.MATURITY
                + " job_supervisor_max_records=" + MAX_TASK_RECORDS
                + " terminal_retention_ms=" + TERMINAL_RETENTION_MS
                + " hardware_accessed=false");
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

    private AndroidCallerIdentityResolver identityResolver;

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
            JobSupervisor.Snapshot before = jobSupervisor.find(record.handle.taskId);
            if (before == null || before.isTerminal()) {
                return;
            }
            accepted = updateFor(before);
            JobSupervisor.Transition transition = jobSupervisor.transition(
                    record.handle.taskId,
                    JobSupervisor.State.RUNNING,
                    50,
                    "deterministic stub running");
            if (!transition.wasApplied()) {
                return;
            }
            running = updateFor(transition.getSnapshot());
        }

        deliverUpdate(record, accepted);
        synchronized (record) {
            JobSupervisor.Snapshot current = jobSupervisor.find(record.handle.taskId);
            if (current == null || current.isTerminal()) {
                return;
            }
        }
        deliverUpdate(record, running);
        synchronized (record) {
            JobSupervisor.Snapshot current = jobSupervisor.find(record.handle.taskId);
            if (current == null || current.isTerminal()) {
                return;
            }
        }
        executor.schedule(() -> completeTask(record), COMPLETE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void completeTask(TaskRecord record) {
        TaskUpdate completed;
        TaskResult result;
        synchronized (record) {
            JobSupervisor.Snapshot before = jobSupervisor.find(record.handle.taskId);
            if (before == null || before.isTerminal()) {
                return;
            }
            JobSupervisor.Transition transition = jobSupervisor.transition(
                    record.handle.taskId,
                    JobSupervisor.State.COMPLETED,
                    100,
                    "deterministic stub completed");
            if (!transition.wasApplied()) {
                return;
            }
            completed = updateFor(transition.getSnapshot());
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
            jobSupervisor.markTerminalDeliverySettled(record.handle.taskId);
        }
    }

    private boolean cancelRecord(
            TaskRecord record,
            int reasonCode,
            boolean notifyClient,
            CallerIdentitySnapshot caller) {
        TaskUpdate cancelled;
        TaskFailure failure;
        synchronized (record) {
            JobSupervisor.Transition transition = caller == null
                    ? jobSupervisor.cancelSystem(
                            record.handle.taskId,
                            "cancelled reason=" + reasonCode)
                    : jobSupervisor.cancelOwned(
                            record.handle.taskId,
                            caller,
                            "cancelled reason=" + reasonCode);
            if (transition.getOutcome() == JobSupervisor.TransitionOutcome.ALREADY_CANCELLED) {
                return true;
            }
            if (!transition.wasApplied()) {
                return false;
            }
            cancelled = updateFor(transition.getSnapshot());
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
            jobSupervisor.markTerminalDeliverySettled(record.handle.taskId);
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
            jobSupervisor.markTerminalDeliverySettled(record.handle.taskId);
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
                false,
                null);
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

    private static TaskUpdate updateFor(JobSupervisor.Snapshot snapshot) {
        return updateFor(
                snapshot.getTaskId(),
                aidlState(snapshot.getState()),
                snapshot.getProgressPercent(),
                snapshot.getSequence(),
                snapshot.getMessage());
    }

    private static int aidlState(JobSupervisor.State state) {
        switch (state) {
            case ACCEPTED:
                return ICentralBrainRuntime.TASK_STATE_ACCEPTED;
            case RUNNING:
                return ICentralBrainRuntime.TASK_STATE_RUNNING;
            case COMPLETED:
                return ICentralBrainRuntime.TASK_STATE_COMPLETED;
            case FAILED:
                return ICentralBrainRuntime.TASK_STATE_FAILED;
            case CANCELLED:
                return ICentralBrainRuntime.TASK_STATE_CANCELLED;
            default:
                throw new IllegalArgumentException("unsupported supervisor state: " + state);
        }
    }

    private CallerIdentitySnapshot resolveTrustedCaller() {
        CallerIdentitySnapshot caller = identityResolver.resolveCallingIdentity();
        if (!caller.isResolved()) {
            Log.w(TAG, "denied unresolved Binder caller " + caller.auditSummary()
                    + " reason=" + caller.getResolutionFailure());
            throw new SecurityException("trusted Binder caller identity could not be resolved");
        }
        return caller;
    }

    private void removeTaskRecords(Iterable<String> taskIds) {
        for (String taskId : taskIds) {
            TaskRecord removed = tasks.remove(taskId);
            if (removed != null) {
                unlinkCallbackDeath(removed);
            }
        }
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
        IBinder.DeathRecipient deathRecipient;

        TaskRecord(
                String taskId,
                AgentTaskRequest request,
                ICentralBrainTaskCallback callback,
                long acceptedAtElapsedRealtimeMs) {
            this.request = request;
            this.callback = callback;
            this.handle = new TaskHandle();
            this.handle.taskId = taskId;
            this.handle.acceptedAtElapsedRealtimeMs = acceptedAtElapsedRealtimeMs;
        }
    }
}
