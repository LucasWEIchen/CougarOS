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

import java.util.ArrayDeque;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

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

    private ICentralBrainRuntime runtime;
    private IBinder runtimeBinder;
    private IBinder.DeathRecipient runtimeDeathRecipient;
    private boolean bound;
    private boolean closed;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ICentralBrainRuntime connected = ICentralBrainRuntime.Stub.asInterface(service);
            IBinder.DeathRecipient recipient = () -> handleServiceDeath(service);
            try {
                service.linkToDeath(recipient, 0);
            } catch (RemoteException exception) {
                invalidateBinding();
                dispatch(() -> connectionListener.onConnectionFailed("service died during bind"));
                return;
            }

            IBinder previousBinder;
            IBinder.DeathRecipient previousRecipient;
            synchronized (connectionLock) {
                if (closed) {
                    safeUnlinkToDeath(service, recipient);
                    return;
                }
                previousBinder = runtimeBinder;
                previousRecipient = runtimeDeathRecipient;
                runtime = connected;
                runtimeBinder = service;
                runtimeDeathRecipient = recipient;
            }
            safeUnlinkToDeath(previousBinder, previousRecipient);
            if (!service.isBinderAlive()) {
                handleServiceDeath(service);
                return;
            }
            dispatch(() -> {
                if (isCurrentConnection(service)) {
                    connectionListener.onConnected(CentralBrainClient.this);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (!hasLiveConnection()) {
                handleServiceDeath(null);
            }
        }

        @Override
        public void onBindingDied(ComponentName name) {
            if (hasLiveConnection()) {
                return;
            }
            invalidateBinding();
            handleServiceDeath(null);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            if (hasLiveConnection()) {
                return;
            }
            invalidateBinding();
            handleServiceDeath(null);
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
        boolean didBind;
        synchronized (connectionLock) {
            if (closed) {
                throw new IllegalStateException("client is closed");
            }
            if (bound) {
                return true;
            }
            Intent intent = new Intent().setComponent(RUNTIME_COMPONENT);
            didBind = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            bound = didBind;
        }
        if (!didBind) {
            dispatch(() -> connectionListener.onConnectionFailed("bindService returned false"));
        }
        return didBind;
    }

    public boolean reconnect() {
        synchronized (connectionLock) {
            if (closed) {
                throw new IllegalStateException("client is closed");
            }
        }
        handleServiceDeath(null);
        invalidateBinding();
        return connect();
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
        IBinder.DeathRecipient recipientToUnlink;
        boolean shouldUnbind;
        synchronized (connectionLock) {
            if (closed) {
                return;
            }
            closed = true;
            binderToUnlink = runtimeBinder;
            recipientToUnlink = runtimeDeathRecipient;
            shouldUnbind = bound;
            runtime = null;
            runtimeBinder = null;
            runtimeDeathRecipient = null;
            bound = false;
        }

        safeUnlinkToDeath(binderToUnlink, recipientToUnlink);
        if (shouldUnbind) {
            safeUnbind();
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

    private void handleServiceDeath(IBinder expectedBinder) {
        IBinder binderToUnlink;
        IBinder.DeathRecipient recipientToUnlink;
        synchronized (connectionLock) {
            if (runtimeBinder == null
                    || (expectedBinder != null && runtimeBinder != expectedBinder)) {
                return;
            }
            binderToUnlink = runtimeBinder;
            recipientToUnlink = runtimeDeathRecipient;
            runtime = null;
            runtimeBinder = null;
            runtimeDeathRecipient = null;
        }
        safeUnlinkToDeath(binderToUnlink, recipientToUnlink);
        failActiveCallbacks(ICentralBrainRuntime.ERROR_SERVICE_DIED, "runtime service died");
        dispatch(() -> {
            if (!isClosed()) {
                connectionListener.onDisconnected();
            }
        });
    }

    private void invalidateBinding() {
        boolean shouldUnbind;
        synchronized (connectionLock) {
            shouldUnbind = bound;
            bound = false;
        }
        if (shouldUnbind) {
            safeUnbind();
        }
    }

    private boolean isCurrentConnection(IBinder service) {
        synchronized (connectionLock) {
            return !closed && runtimeBinder == service && service.isBinderAlive();
        }
    }

    private boolean hasLiveConnection() {
        synchronized (connectionLock) {
            return runtimeBinder != null && runtimeBinder.isBinderAlive();
        }
    }

    private boolean isClosed() {
        synchronized (connectionLock) {
            return closed;
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

    private void safeUnlinkToDeath(IBinder binder, IBinder.DeathRecipient recipient) {
        if (binder == null || recipient == null) {
            return;
        }
        try {
            binder.unlinkToDeath(recipient, 0);
        } catch (NoSuchElementException ignored) {
            // Binder already removed the recipient after service death.
        }
    }

    private void safeUnbind() {
        try {
            appContext.unbindService(serviceConnection);
        } catch (IllegalArgumentException ignored) {
            // Android already discarded a dead/null binding.
        }
    }

    private final class CallbackBridge extends ICentralBrainTaskCallback.Stub {
        private final TaskCallback callback;
        private final Executor deliveryExecutor;
        private boolean terminal;
        private volatile String taskId = "";

        CallbackBridge(TaskCallback callback) {
            this.callback = callback;
            this.deliveryExecutor = new SerialExecutor(callbackExecutor);
        }

        @Override
        public synchronized void onTaskUpdate(TaskUpdate update) {
            if (!terminal) {
                deliveryExecutor.execute(() -> callback.onUpdate(update));
            }
        }

        @Override
        public synchronized void onTaskCompleted(TaskResult result) {
            if (!terminal) {
                terminal = true;
                activeCallbacks.remove(asBinder());
                deliveryExecutor.execute(() -> callback.onCompleted(result));
            }
        }

        @Override
        public synchronized void onTaskFailed(TaskFailure failure) {
            if (!terminal) {
                terminal = true;
                activeCallbacks.remove(asBinder());
                deliveryExecutor.execute(() -> callback.onFailed(failure));
            }
        }

        synchronized void failFromClient(int errorCode, String message) {
            if (!terminal) {
                terminal = true;
                TaskFailure failure = new TaskFailure();
                failure.taskId = taskId;
                failure.errorCode = errorCode;
                failure.errorMessage = message;
                failure.retryable = true;
                deliveryExecutor.execute(() -> callback.onFailed(failure));
            }
        }
    }

    private static final class SerialExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private final Executor delegate;
        private Runnable active;

        SerialExecutor(Executor delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void execute(Runnable command) {
            tasks.offer(() -> {
                try {
                    command.run();
                } finally {
                    scheduleNext();
                }
            });
            if (active == null) {
                scheduleNext();
            }
        }

        private synchronized void scheduleNext() {
            active = tasks.poll();
            if (active != null) {
                delegate.execute(active);
            }
        }
    }
}
