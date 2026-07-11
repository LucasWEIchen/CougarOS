package com.centralbrain.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import com.centralbrain.sdk.production.AgentTaskRequest;
import com.centralbrain.sdk.production.ICentralBrainRuntime;
import com.centralbrain.sdk.production.ICentralBrainTaskCallback;
import com.centralbrain.sdk.production.TaskFailure;
import com.centralbrain.sdk.production.TaskHandle;
import com.centralbrain.sdk.production.TaskResult;
import com.centralbrain.sdk.production.TaskUpdate;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Typed Android client for the R2 production Binder surface.
 * Req IDs: XSC-001, XSC-006, NV-G-003, NV-G-006, NV-P-002, DEL-001.
 */
public final class CentralBrainClient implements AutoCloseable {
    public static final String RUNTIME_PACKAGE = "com.centralbrain.runtime";
    public static final String RUNTIME_SERVICE =
            "com.centralbrain.runtime.CentralBrainRuntimeService";
    public static final String BIND_PERMISSION =
            "com.centralbrain.permission.BIND_RUNTIME";
    public static final ComponentName RUNTIME_COMPONENT =
            new ComponentName(RUNTIME_PACKAGE, RUNTIME_SERVICE);

    public interface ConnectionListener {
        void onConnected(CentralBrainClient client);

        void onDisconnected();

        void onConnectionFailed(String reason);
    }

    public interface TaskCallback {
        void onUpdate(TaskUpdate update);

        void onCompleted(TaskResult result);

        void onFailed(TaskFailure failure);
    }

    private final Context appContext;
    private final Executor callbackExecutor;
    private final ConnectionListener connectionListener;
    private final Object connectionLock = new Object();
    private final Map<IBinder, CallbackBridge> activeCallbacks = new ConcurrentHashMap<>();
    private final IBinder.DeathRecipient deathRecipient = this::handleServiceDeath;

    private ICentralBrainRuntime runtime;
    private IBinder runtimeBinder;
    private boolean bound;
    private boolean closed;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ICentralBrainRuntime connected = ICentralBrainRuntime.Stub.asInterface(service);
            try {
                service.linkToDeath(deathRecipient, 0);
            } catch (RemoteException exception) {
                handleServiceDeath();
                dispatch(() -> connectionListener.onConnectionFailed("service died during bind"));
                return;
            }

            synchronized (connectionLock) {
                if (closed) {
                    safeUnlinkToDeath(service);
                    return;
                }
                runtime = connected;
                runtimeBinder = service;
            }
            dispatch(() -> connectionListener.onConnected(CentralBrainClient.this));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            handleServiceDeath();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            handleServiceDeath();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            clearConnection();
            dispatch(() -> connectionListener.onConnectionFailed("runtime returned a null Binder"));
        }
    };

    public CentralBrainClient(
            Context context,
            Executor callbackExecutor,
            ConnectionListener connectionListener) {
        this.appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.connectionListener = Objects.requireNonNull(connectionListener, "connectionListener");
    }

    public boolean connect() {
        synchronized (connectionLock) {
            if (closed) {
                throw new IllegalStateException("client is closed");
            }
            if (bound) {
                return true;
            }
            Intent intent = new Intent().setComponent(RUNTIME_COMPONENT);
            bound = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            if (!bound) {
                dispatch(() -> connectionListener.onConnectionFailed("bindService returned false"));
            }
            return bound;
        }
    }

    public boolean isConnected() {
        synchronized (connectionLock) {
            return runtime != null && runtimeBinder != null && runtimeBinder.isBinderAlive();
        }
    }

    public int getProtocolVersion() throws RemoteException {
        return requireRuntime().getProtocolVersion();
    }

    public String getProtocolHash() throws RemoteException {
        return requireRuntime().getProtocolHash();
    }

    public TaskHandle submitAgentTask(AgentTaskRequest request, TaskCallback callback)
            throws RemoteException {
        Objects.requireNonNull(callback, "callback");
        ICentralBrainRuntime service = requireRuntime();
        CallbackBridge bridge = new CallbackBridge(callback);
        activeCallbacks.put(bridge.asBinder(), bridge);
        try {
            TaskHandle handle = service.submitAgentTask(request, bridge);
            if (handle == null) {
                activeCallbacks.remove(bridge.asBinder());
                throw new RemoteException("runtime returned a null TaskHandle");
            }
            bridge.taskId = handle.taskId;
            return handle;
        } catch (RemoteException | RuntimeException exception) {
            activeCallbacks.remove(bridge.asBinder());
            throw exception;
        }
    }

    public boolean cancelTask(TaskHandle handle, int reasonCode) throws RemoteException {
        return requireRuntime().cancelTask(handle, reasonCode);
    }

    public TaskUpdate getTaskStatus(TaskHandle handle) throws RemoteException {
        return requireRuntime().getTaskStatus(handle);
    }

    @Override
    public void close() {
        IBinder binderToUnlink;
        boolean shouldUnbind;
        synchronized (connectionLock) {
            if (closed) {
                return;
            }
            closed = true;
            binderToUnlink = runtimeBinder;
            shouldUnbind = bound;
            runtime = null;
            runtimeBinder = null;
            bound = false;
        }

        safeUnlinkToDeath(binderToUnlink);
        if (shouldUnbind) {
            appContext.unbindService(serviceConnection);
        }
        failActiveCallbacks(ICentralBrainRuntime.ERROR_SERVICE_DIED, "client closed");
    }

    private ICentralBrainRuntime requireRuntime() {
        synchronized (connectionLock) {
            if (runtime == null || runtimeBinder == null || !runtimeBinder.isBinderAlive()) {
                throw new IllegalStateException("Central Brain Runtime is not connected");
            }
            return runtime;
        }
    }

    private void handleServiceDeath() {
        clearConnection();
        failActiveCallbacks(ICentralBrainRuntime.ERROR_SERVICE_DIED, "runtime service died");
        dispatch(connectionListener::onDisconnected);
    }

    private void clearConnection() {
        synchronized (connectionLock) {
            runtime = null;
            runtimeBinder = null;
        }
    }

    private void failActiveCallbacks(int errorCode, String message) {
        for (CallbackBridge bridge : activeCallbacks.values()) {
            bridge.failFromClient(errorCode, message);
        }
        activeCallbacks.clear();
    }

    private void dispatch(Runnable runnable) {
        callbackExecutor.execute(runnable);
    }

    private void safeUnlinkToDeath(IBinder binder) {
        if (binder == null) {
            return;
        }
        try {
            binder.unlinkToDeath(deathRecipient, 0);
        } catch (NoSuchElementException ignored) {
            // Binder already removed the recipient after service death.
        }
    }

    private final class CallbackBridge extends ICentralBrainTaskCallback.Stub {
        private final TaskCallback callback;
        private final AtomicBoolean terminal = new AtomicBoolean(false);
        private volatile String taskId = "";

        CallbackBridge(TaskCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onTaskUpdate(TaskUpdate update) {
            if (!terminal.get()) {
                dispatch(() -> callback.onUpdate(update));
            }
        }

        @Override
        public void onTaskCompleted(TaskResult result) {
            if (terminal.compareAndSet(false, true)) {
                activeCallbacks.remove(asBinder());
                dispatch(() -> callback.onCompleted(result));
            }
        }

        @Override
        public void onTaskFailed(TaskFailure failure) {
            if (terminal.compareAndSet(false, true)) {
                activeCallbacks.remove(asBinder());
                dispatch(() -> callback.onFailed(failure));
            }
        }

        void failFromClient(int errorCode, String message) {
            if (terminal.compareAndSet(false, true)) {
                TaskFailure failure = new TaskFailure();
                failure.taskId = taskId;
                failure.errorCode = errorCode;
                failure.errorMessage = message;
                failure.retryable = true;
                dispatch(() -> callback.onFailed(failure));
            }
        }
    }
}
