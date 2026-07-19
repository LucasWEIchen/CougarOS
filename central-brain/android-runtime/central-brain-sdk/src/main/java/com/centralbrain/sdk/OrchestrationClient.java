package com.centralbrain.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import com.centralbrain.sdk.orchestration.ApprovalResponse;
import com.centralbrain.sdk.orchestration.ICentralBrainOrchestration;
import com.centralbrain.sdk.orchestration.OrchestrationContract;
import com.centralbrain.sdk.orchestration.OrchestrationSnapshot;
import com.centralbrain.sdk.orchestration.OrchestrationStartRequest;
import com.centralbrain.sdk.orchestration.UndoRequest;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.ScenarioPlan;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Typed client for the owner-scoped Plan/Graph/Effect orchestration surface. */
public final class OrchestrationClient implements AutoCloseable {
    public interface ConnectionListener {
        void onConnected(OrchestrationClient client, boolean reconnected);

        void onDisconnected();

        void onConnectionFailed(String code, String message);
    }

    private final Context appContext;
    private final Executor callbackExecutor;
    private final ConnectionListener listener;
    private final Object lock = new Object();

    private ICentralBrainOrchestration orchestration;
    private IBinder binder;
    private IBinder.DeathRecipient deathRecipient;
    private boolean bound;
    private boolean closed;
    private boolean everConnected;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ICentralBrainOrchestration candidate =
                    ICentralBrainOrchestration.Stub.asInterface(service);
            IBinder.DeathRecipient recipient = () -> serviceDied(service);
            try {
                service.linkToDeath(recipient, 0);
                if (candidate.getProtocolVersion()
                                != ICentralBrainOrchestration.INTERFACE_VERSION
                        || !ICentralBrainOrchestration.INTERFACE_HASH.equals(
                                candidate.getProtocolHash())) {
                    safeUnlink(service, recipient);
                    invalidateBinding();
                    dispatch(() -> listener.onConnectionFailed(
                            ScenarioClient.ERROR_PROTOCOL_MISMATCH,
                            "orchestration Binder protocol mismatch"));
                    return;
                }
            } catch (RemoteException | RuntimeException failure) {
                safeUnlink(service, recipient);
                invalidateBinding();
                dispatch(() -> listener.onConnectionFailed(
                        ScenarioClient.ERROR_TRANSPORT,
                        "orchestration protocol negotiation failed"));
                return;
            }

            IBinder previous;
            IBinder.DeathRecipient previousRecipient;
            boolean reconnected;
            synchronized (lock) {
                if (closed) {
                    safeUnlink(service, recipient);
                    return;
                }
                previous = binder;
                previousRecipient = deathRecipient;
                orchestration = candidate;
                binder = service;
                deathRecipient = recipient;
                reconnected = everConnected;
                everConnected = true;
            }
            safeUnlink(previous, previousRecipient);
            if (!service.isBinderAlive()) {
                serviceDied(service);
                return;
            }
            dispatch(() -> {
                if (isCurrent(service)) {
                    listener.onConnected(OrchestrationClient.this, reconnected);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceDied(null);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            serviceDied(null);
            invalidateBinding();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            serviceDied(null);
            invalidateBinding();
            dispatch(() -> listener.onConnectionFailed(
                    ScenarioClient.ERROR_TRANSPORT,
                    "orchestration returned a null Binder"));
        }
    };

    public OrchestrationClient(
            Context context,
            Executor callbackExecutor,
            ConnectionListener listener) {
        appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public boolean connect() {
        boolean didBind;
        synchronized (lock) {
            rejectClosed();
            if (bound) {
                return true;
            }
            Intent intent = new Intent(CentralBrainSdk.ACTION_ORCHESTRATION)
                    .setComponent(CentralBrainClient.RUNTIME_COMPONENT);
            didBind = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            bound = didBind;
        }
        if (!didBind) {
            dispatch(() -> listener.onConnectionFailed(
                    ScenarioClient.ERROR_TRANSPORT,
                    "orchestration bindService returned false"));
        }
        return didBind;
    }

    public boolean reconnect() {
        synchronized (lock) {
            rejectClosed();
        }
        serviceDied(null);
        invalidateBinding();
        return connect();
    }

    public boolean isConnected() {
        synchronized (lock) {
            return !closed && orchestration != null && binder != null && binder.isBinderAlive();
        }
    }

    public OrchestrationSnapshot start(OrchestrationStartRequest request)
            throws RemoteException {
        OrchestrationContract.validateStartRequest(request, System.currentTimeMillis());
        return validated(requireService().start(request));
    }

    public OrchestrationSnapshot getSnapshot(String sessionId) throws RemoteException {
        requireSessionId(sessionId);
        return validated(requireService().getSnapshot(sessionId));
    }

    public ScenarioPlan getPlan(String sessionId) throws RemoteException {
        requireSessionId(sessionId);
        ScenarioPlan plan = requireService().getPlan(sessionId);
        if (plan != null) {
            PlanContract.validatePlan(plan);
            if (!sessionId.equals(plan.sessionId)) {
                throw new IllegalArgumentException(
                        "CB_ORCHESTRATION_CLIENT: plan/session binding mismatch");
            }
        }
        return plan;
    }

    public OrchestrationSnapshot respondToApproval(ApprovalResponse response)
            throws RemoteException {
        OrchestrationContract.validateApprovalResponse(response, System.currentTimeMillis());
        return validated(requireService().respondToApproval(response));
    }

    public OrchestrationSnapshot requestUndo(UndoRequest request) throws RemoteException {
        OrchestrationContract.validateUndoRequest(request, System.currentTimeMillis());
        return validated(requireService().requestUndo(request));
    }

    public OrchestrationSnapshot cancel(String sessionId, int reasonCode)
            throws RemoteException {
        requireSessionId(sessionId);
        return validated(requireService().cancel(sessionId, reasonCode));
    }

    @Override
    public void close() {
        IBinder previous;
        IBinder.DeathRecipient previousRecipient;
        boolean shouldUnbind;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            previous = binder;
            previousRecipient = deathRecipient;
            shouldUnbind = bound;
            orchestration = null;
            binder = null;
            deathRecipient = null;
            bound = false;
        }
        safeUnlink(previous, previousRecipient);
        if (shouldUnbind) {
            safeUnbind();
        }
    }

    private OrchestrationSnapshot validated(OrchestrationSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalStateException("runtime returned null orchestration snapshot");
        }
        OrchestrationContract.validateSnapshot(snapshot);
        return snapshot;
    }

    private ICentralBrainOrchestration requireService() throws RemoteException {
        synchronized (lock) {
            if (orchestration == null || binder == null || !binder.isBinderAlive()) {
                throw new RemoteException("orchestration service is not connected");
            }
            return orchestration;
        }
    }

    private void serviceDied(IBinder expected) {
        IBinder previous;
        IBinder.DeathRecipient previousRecipient;
        boolean notify;
        synchronized (lock) {
            if (expected != null && binder != expected) {
                return;
            }
            notify = orchestration != null || binder != null;
            previous = binder;
            previousRecipient = deathRecipient;
            orchestration = null;
            binder = null;
            deathRecipient = null;
        }
        safeUnlink(previous, previousRecipient);
        if (notify) {
            dispatch(listener::onDisconnected);
        }
    }

    private void invalidateBinding() {
        boolean shouldUnbind;
        synchronized (lock) {
            shouldUnbind = bound;
            bound = false;
        }
        if (shouldUnbind) {
            safeUnbind();
        }
    }

    private boolean isCurrent(IBinder candidate) {
        synchronized (lock) {
            return !closed && binder == candidate && candidate.isBinderAlive();
        }
    }

    private void rejectClosed() {
        if (closed) {
            throw new IllegalStateException("client is closed");
        }
    }

    private static void requireSessionId(String sessionId) {
        try {
            if (!java.util.UUID.fromString(sessionId).toString().equals(sessionId)) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(
                    "CB_ORCHESTRATION_CLIENT: invalid sessionId");
        }
    }

    private void dispatch(Runnable runnable) {
        callbackExecutor.execute(runnable);
    }

    private static void safeUnlink(IBinder target, IBinder.DeathRecipient recipient) {
        if (target == null || recipient == null) {
            return;
        }
        try {
            target.unlinkToDeath(recipient, 0);
        } catch (NoSuchElementException ignored) {
            // Binder already removed the recipient.
        }
    }

    private void safeUnbind() {
        try {
            appContext.unbindService(serviceConnection);
        } catch (IllegalArgumentException ignored) {
            // Android already discarded a dead/null binding.
        }
    }
}
