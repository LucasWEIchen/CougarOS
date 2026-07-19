package com.centralbrain.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import com.centralbrain.sdk.model.DevelopmentModelProjection;
import com.centralbrain.sdk.model.DevelopmentModelProjectionContract;
import com.centralbrain.sdk.model.ICentralBrainDevelopmentModelProjection;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Typed client for the debug-only, owner-scoped model UX projection Service. */
public final class DevelopmentModelProjectionClient implements AutoCloseable {
    public static final String ACTION =
            "com.centralbrain.runtime.action.DEVELOPMENT_MODEL_PROJECTION";
    public interface ConnectionListener {
        void onConnected(DevelopmentModelProjectionClient client, boolean reconnected);
        void onDisconnected();
        void onConnectionFailed(String code);
    }

    public static final ComponentName COMPONENT = new ComponentName(
            CentralBrainClient.RUNTIME_PACKAGE,
            "com.centralbrain.runtime.model.DevelopmentModelProjectionService");

    private final Context appContext;
    private final Executor callbackExecutor;
    private final ConnectionListener listener;
    private final Object lock = new Object();
    private ICentralBrainDevelopmentModelProjection service;
    private IBinder binder;
    private IBinder.DeathRecipient deathRecipient;
    private boolean bound;
    private boolean closed;
    private boolean everConnected;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder candidateBinder) {
            ICentralBrainDevelopmentModelProjection candidate =
                    ICentralBrainDevelopmentModelProjection.Stub.asInterface(candidateBinder);
            IBinder.DeathRecipient recipient = () -> serviceDied(candidateBinder);
            try {
                candidateBinder.linkToDeath(recipient, 0);
                if (candidate.getProtocolVersion()
                                != ICentralBrainDevelopmentModelProjection.INTERFACE_VERSION
                        || !ICentralBrainDevelopmentModelProjection.INTERFACE_HASH.equals(
                                candidate.getProtocolHash())) {
                    safeUnlink(candidateBinder, recipient);
                    invalidateBinding();
                    dispatch(() -> listener.onConnectionFailed("PROTOCOL_MISMATCH"));
                    return;
                }
            } catch (RemoteException | RuntimeException failure) {
                safeUnlink(candidateBinder, recipient);
                invalidateBinding();
                dispatch(() -> listener.onConnectionFailed("NEGOTIATION_FAILED"));
                return;
            }
            boolean reconnected;
            synchronized (lock) {
                if (closed) {
                    safeUnlink(candidateBinder, recipient);
                    return;
                }
                service = candidate;
                binder = candidateBinder;
                deathRecipient = recipient;
                reconnected = everConnected;
                everConnected = true;
            }
            dispatch(() -> listener.onConnected(
                    DevelopmentModelProjectionClient.this, reconnected));
        }

        @Override public void onServiceDisconnected(ComponentName name) { serviceDied(null); }
        @Override public void onBindingDied(ComponentName name) {
            serviceDied(null);
            invalidateBinding();
        }
        @Override public void onNullBinding(ComponentName name) {
            serviceDied(null);
            invalidateBinding();
            dispatch(() -> listener.onConnectionFailed("NULL_BINDING"));
        }
    };

    public DevelopmentModelProjectionClient(
            Context context, Executor callbackExecutor, ConnectionListener listener) {
        appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public boolean connect() {
        boolean didBind;
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("client is closed");
            }
            if (bound) {
                return true;
            }
            Intent intent = new Intent(ACTION)
                    .setComponent(COMPONENT);
            didBind = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            bound = didBind;
        }
        if (!didBind) dispatch(() -> listener.onConnectionFailed("BIND_REJECTED"));
        return didBind;
    }

    public boolean isConnected() {
        synchronized (lock) {
            return !closed && service != null && binder != null && binder.isBinderAlive();
        }
    }

    public DevelopmentModelProjection getOwnProjection(String sessionId)
            throws RemoteException {
        requireSessionId(sessionId);
        DevelopmentModelProjection projection = requireService().getOwnProjection(sessionId);
        if (projection != null) {
            DevelopmentModelProjectionContract.validate(projection);
            if (!sessionId.equals(projection.sessionId)) {
                throw new IllegalArgumentException(
                        "CB_DEVELOPMENT_MODEL_PROJECTION_CLIENT: session mismatch");
            }
        }
        return projection;
    }

    @Override
    public void close() {
        IBinder previous;
        IBinder.DeathRecipient previousRecipient;
        boolean shouldUnbind;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            previous = binder;
            previousRecipient = deathRecipient;
            shouldUnbind = bound;
            service = null;
            binder = null;
            deathRecipient = null;
            bound = false;
        }
        safeUnlink(previous, previousRecipient);
        if (shouldUnbind) safeUnbind();
    }

    private ICentralBrainDevelopmentModelProjection requireService() throws RemoteException {
        synchronized (lock) {
            if (service == null || binder == null || !binder.isBinderAlive()) {
                throw new RemoteException("development model projection service is not connected");
            }
            return service;
        }
    }

    private void serviceDied(IBinder expected) {
        IBinder previous;
        IBinder.DeathRecipient previousRecipient;
        boolean notify;
        synchronized (lock) {
            if (expected != null && binder != expected) return;
            notify = service != null || binder != null;
            previous = binder;
            previousRecipient = deathRecipient;
            service = null;
            binder = null;
            deathRecipient = null;
        }
        safeUnlink(previous, previousRecipient);
        if (notify) dispatch(listener::onDisconnected);
    }

    private void invalidateBinding() {
        boolean shouldUnbind;
        synchronized (lock) {
            shouldUnbind = bound;
            bound = false;
        }
        if (shouldUnbind) safeUnbind();
    }

    private void dispatch(Runnable command) { callbackExecutor.execute(command); }

    private static void requireSessionId(String value) {
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(
                    "CB_DEVELOPMENT_MODEL_PROJECTION_CLIENT: invalid sessionId");
        }
    }

    private static void safeUnlink(IBinder target, IBinder.DeathRecipient recipient) {
        if (target == null || recipient == null) return;
        try {
            target.unlinkToDeath(recipient, 0);
        } catch (NoSuchElementException ignored) {
            // Binder already removed it.
        }
    }

    private void safeUnbind() {
        try {
            appContext.unbindService(connection);
        } catch (IllegalArgumentException ignored) {
            // Android already discarded the binding.
        }
    }
}
