package com.centralbrain.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import com.centralbrain.sdk.governance.ActionDecision;
import com.centralbrain.sdk.governance.ActionRequest;
import com.centralbrain.sdk.governance.ApprovalHandle;
import com.centralbrain.sdk.governance.ApprovalStatus;
import com.centralbrain.sdk.governance.ICentralBrainGovernance;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Typed client for the independent R3C Governance Binder surface. */
public final class CentralBrainGovernanceClient implements AutoCloseable {
    public static final String GOVERNANCE_SERVICE =
            "com.centralbrain.runtime.CentralBrainGovernanceService";
    public static final String BIND_PERMISSION =
            "com.centralbrain.permission.BIND_GOVERNANCE";
    public static final ComponentName GOVERNANCE_COMPONENT = new ComponentName(
            CentralBrainClient.RUNTIME_PACKAGE,
            GOVERNANCE_SERVICE);

    public interface ConnectionListener {
        void onConnected(CentralBrainGovernanceClient client);

        void onDisconnected();

        void onConnectionFailed(String reason);
    }

    private final Context appContext;
    private final Executor callbackExecutor;
    private final ConnectionListener connectionListener;
    private final Object connectionLock = new Object();

    private ICentralBrainGovernance governance;
    private IBinder governanceBinder;
    private IBinder.DeathRecipient deathRecipient;
    private boolean bound;
    private boolean closed;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ICentralBrainGovernance connected = ICentralBrainGovernance.Stub.asInterface(service);
            IBinder.DeathRecipient recipient = () -> handleServiceDeath(service);
            try {
                service.linkToDeath(recipient, 0);
            } catch (RemoteException exception) {
                invalidateBinding();
                dispatch(() -> connectionListener.onConnectionFailed(
                        "governance service died during bind"));
                return;
            }

            IBinder previousBinder;
            IBinder.DeathRecipient previousRecipient;
            synchronized (connectionLock) {
                if (closed) {
                    safeUnlinkToDeath(service, recipient);
                    return;
                }
                previousBinder = governanceBinder;
                previousRecipient = deathRecipient;
                governance = connected;
                governanceBinder = service;
                deathRecipient = recipient;
            }
            safeUnlinkToDeath(previousBinder, previousRecipient);
            if (!service.isBinderAlive()) {
                handleServiceDeath(service);
                return;
            }
            dispatch(() -> {
                if (isCurrentConnection(service)) {
                    connectionListener.onConnected(CentralBrainGovernanceClient.this);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            handleServiceDeath(null);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            handleServiceDeath(null);
            invalidateBinding();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            handleServiceDeath(null);
            invalidateBinding();
            dispatch(() -> connectionListener.onConnectionFailed(
                    "governance returned a null Binder"));
        }
    };

    public CentralBrainGovernanceClient(
            Context context,
            Executor callbackExecutor,
            ConnectionListener connectionListener) {
        appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.connectionListener = Objects.requireNonNull(
                connectionListener,
                "connectionListener");
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
            didBind = appContext.bindService(
                    new Intent().setComponent(GOVERNANCE_COMPONENT),
                    serviceConnection,
                    Context.BIND_AUTO_CREATE);
            bound = didBind;
        }
        if (!didBind) {
            dispatch(() -> connectionListener.onConnectionFailed(
                    "governance bindService returned false"));
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
            return governance != null
                    && governanceBinder != null
                    && governanceBinder.isBinderAlive();
        }
    }

    public int getProtocolVersion() throws RemoteException {
        return requireGovernance().getProtocolVersion();
    }

    public String getProtocolHash() throws RemoteException {
        return requireGovernance().getProtocolHash();
    }

    public ActionDecision evaluateAction(ActionRequest request) throws RemoteException {
        return requireGovernance().evaluateAction(request);
    }

    public ApprovalHandle requestApproval(ActionRequest request) throws RemoteException {
        return requireGovernance().requestApproval(request);
    }

    public ApprovalStatus getApprovalStatus(ApprovalHandle handle) throws RemoteException {
        return requireGovernance().getApprovalStatus(handle);
    }

    public boolean cancelApproval(ApprovalHandle handle) throws RemoteException {
        return requireGovernance().cancelApproval(handle);
    }

    @Override
    public void close() {
        IBinder binder;
        IBinder.DeathRecipient recipient;
        boolean shouldUnbind;
        synchronized (connectionLock) {
            if (closed) {
                return;
            }
            closed = true;
            binder = governanceBinder;
            recipient = deathRecipient;
            shouldUnbind = bound;
            governance = null;
            governanceBinder = null;
            deathRecipient = null;
            bound = false;
        }
        safeUnlinkToDeath(binder, recipient);
        if (shouldUnbind) {
            safeUnbind();
        }
    }

    private ICentralBrainGovernance requireGovernance() throws RemoteException {
        synchronized (connectionLock) {
            if (governance == null
                    || governanceBinder == null
                    || !governanceBinder.isBinderAlive()) {
                throw new RemoteException("governance service is not connected");
            }
            return governance;
        }
    }

    private void handleServiceDeath(IBinder expectedBinder) {
        IBinder disconnectedBinder;
        IBinder.DeathRecipient disconnectedRecipient;
        boolean notify;
        synchronized (connectionLock) {
            if (expectedBinder != null && governanceBinder != expectedBinder) {
                return;
            }
            notify = governanceBinder != null || governance != null;
            disconnectedBinder = governanceBinder;
            disconnectedRecipient = deathRecipient;
            governance = null;
            governanceBinder = null;
            deathRecipient = null;
        }
        safeUnlinkToDeath(disconnectedBinder, disconnectedRecipient);
        if (notify) {
            dispatch(connectionListener::onDisconnected);
        }
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

    private boolean isCurrentConnection(IBinder binder) {
        synchronized (connectionLock) {
            return !closed && governanceBinder == binder && binder.isBinderAlive();
        }
    }

    private void dispatch(Runnable runnable) {
        callbackExecutor.execute(runnable);
    }

    private static void safeUnlinkToDeath(
            IBinder binder,
            IBinder.DeathRecipient recipient) {
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
}
