package com.centralbrain.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import com.centralbrain.sdk.event.EventPage;
import com.centralbrain.sdk.event.ICentralBrainSessionEventCallback;
import com.centralbrain.sdk.event.ICentralBrainSessionEvents;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionPage;
import com.centralbrain.sdk.session.SessionQuery;
import com.centralbrain.sdk.session.SessionRequest;
import com.centralbrain.sdk.session.SessionSnapshot;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Android explicit-component transport for the two Stage 2 Binder actions. */
final class AndroidScenarioTransport implements ScenarioTransport {
    private final Context appContext;
    private final Object lock = new Object();
    private final Map<EventSink, CallbackRecord> callbacks = new IdentityHashMap<>();

    private Listener listener;
    private Attempt active;
    private long nextGeneration;
    private boolean closed;

    AndroidScenarioTransport(Context appContext) {
        this.appContext = Objects.requireNonNull(appContext, "appContext");
    }

    @Override
    public void setListener(Listener listener) {
        synchronized (lock) {
            if (this.listener != null) {
                throw new IllegalStateException("transport listener is already set");
            }
            this.listener = Objects.requireNonNull(listener, "listener");
        }
    }

    @Override
    public boolean connect() {
        Attempt attempt;
        synchronized (lock) {
            rejectClosed();
            requireListener();
            if (active != null) {
                return true;
            }
            attempt = new Attempt(++nextGeneration);
            active = attempt;
        }

        Intent sessionIntent = new Intent(CentralBrainSdk.ACTION_SESSION_RUNTIME)
                .setComponent(CentralBrainClient.RUNTIME_COMPONENT);
        attempt.sessionBound = appContext.bindService(
                sessionIntent,
                attempt.sessionConnection,
                Context.BIND_AUTO_CREATE);
        if (!attempt.sessionBound) {
            failAttempt(attempt, "session bindService returned false", false);
            return false;
        }

        Intent eventIntent = new Intent(CentralBrainSdk.ACTION_SESSION_EVENTS)
                .setComponent(CentralBrainClient.RUNTIME_COMPONENT);
        attempt.eventBound = appContext.bindService(
                eventIntent,
                attempt.eventConnection,
                Context.BIND_AUTO_CREATE);
        if (!attempt.eventBound) {
            failAttempt(attempt, "event bindService returned false", false);
            return false;
        }
        return true;
    }

    @Override
    public boolean reconnect() {
        Attempt previous;
        synchronized (lock) {
            rejectClosed();
            previous = active;
        }
        if (previous != null) {
            detach(previous, false, null);
        }
        return connect();
    }

    @Override
    public boolean isConnected() {
        synchronized (lock) {
            return !closed && active != null && active.isReady();
        }
    }

    @Override
    public int getSessionProtocolVersion() throws RemoteException {
        return requireSession().getProtocolVersion();
    }

    @Override
    public String getSessionProtocolHash() throws RemoteException {
        return requireSession().getProtocolHash();
    }

    @Override
    public int getEventProtocolVersion() throws RemoteException {
        return requireEvents().getProtocolVersion();
    }

    @Override
    public String getEventProtocolHash() throws RemoteException {
        return requireEvents().getProtocolHash();
    }

    @Override
    public SessionHandle openSession(SessionRequest request) throws RemoteException {
        return requireSession().openSession(request);
    }

    @Override
    public SessionSnapshot getSession(SessionHandle handle) throws RemoteException {
        return requireSession().getSession(handle);
    }

    @Override
    public SessionPage listSessions(SessionQuery query) throws RemoteException {
        return requireSession().listSessions(query);
    }

    @Override
    public boolean cancelSession(SessionHandle handle, int reasonCode) throws RemoteException {
        return requireSession().cancelSession(handle, reasonCode);
    }

    @Override
    public EventPage getEvents(String sessionId, String cursor, int limit)
            throws RemoteException {
        return requireEvents().getEvents(sessionId, cursor, limit);
    }

    @Override
    public boolean registerSessionCallback(
            String sessionId,
            String cursor,
            EventSink sink) throws RemoteException {
        Objects.requireNonNull(sink, "sink");
        ICentralBrainSessionEvents events = requireEvents();
        CallbackRecord replacement = new CallbackRecord(sessionId, sink);
        boolean registered = events.registerSessionCallback(
                sessionId,
                cursor,
                replacement.callback);
        if (!registered) {
            return false;
        }
        CallbackRecord previous;
        synchronized (lock) {
            if (closed
                    || active == null
                    || !active.isReady()
                    || active.events != events) {
                try {
                    events.unregisterSessionCallback(sessionId, replacement.callback);
                } catch (RemoteException ignored) {
                    // Connection already failed.
                }
                return false;
            }
            previous = callbacks.put(sink, replacement);
        }
        if (previous != null) {
            try {
                events.unregisterSessionCallback(previous.sessionId, previous.callback);
            } catch (RemoteException ignored) {
                // Replacement is authoritative.
            }
        }
        return true;
    }

    @Override
    public boolean unregisterSessionCallback(String sessionId, EventSink sink)
            throws RemoteException {
        Objects.requireNonNull(sink, "sink");
        CallbackRecord record;
        synchronized (lock) {
            record = callbacks.remove(sink);
        }
        if (record == null) {
            return true;
        }
        return requireEvents().unregisterSessionCallback(sessionId, record.callback);
    }

    @Override
    public void close() {
        Attempt previous;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            previous = active;
        }
        if (previous != null) {
            detach(previous, false, null);
        }
    }

    private void connected(Attempt attempt, boolean session, IBinder binder) {
        IBinder.DeathRecipient recipient = () -> detach(attempt, true, null);
        try {
            binder.linkToDeath(recipient, 0);
        } catch (RemoteException exception) {
            failAttempt(attempt, "Binder died during connection", true);
            return;
        }

        boolean notify = false;
        synchronized (lock) {
            if (closed || active != attempt) {
                safeUnlink(binder, recipient);
                return;
            }
            if (session) {
                attempt.sessionBinder = binder;
                attempt.sessionRecipient = recipient;
                attempt.session = ICentralBrainSessionRuntime.Stub.asInterface(binder);
            } else {
                attempt.eventBinder = binder;
                attempt.eventRecipient = recipient;
                attempt.events = ICentralBrainSessionEvents.Stub.asInterface(binder);
            }
            if (attempt.isReady() && !attempt.connectedNotified) {
                attempt.connectedNotified = true;
                notify = true;
            }
        }
        if (notify) {
            currentListener().onConnected();
        }
    }

    private void failAttempt(Attempt attempt, String message, boolean disconnected) {
        detach(attempt, disconnected, message);
    }

    private void detach(Attempt attempt, boolean notifyDisconnected, String failure) {
        boolean wasCurrent;
        ICentralBrainSessionEvents events;
        List<CallbackRecord> staleCallbacks;
        synchronized (lock) {
            wasCurrent = active == attempt;
            if (!wasCurrent) {
                return;
            }
            active = null;
            events = attempt.events;
            staleCallbacks = new ArrayList<>(callbacks.values());
            callbacks.clear();
        }
        unregisterCallbacks(events, staleCallbacks);
        safeUnlink(attempt.sessionBinder, attempt.sessionRecipient);
        safeUnlink(attempt.eventBinder, attempt.eventRecipient);
        if (attempt.sessionBound) {
            safeUnbind(attempt.sessionConnection);
        }
        if (attempt.eventBound) {
            safeUnbind(attempt.eventConnection);
        }
        if (failure != null) {
            currentListener().onConnectionFailed(failure);
        } else if (notifyDisconnected && !isClosed()) {
            currentListener().onDisconnected();
        }
    }

    private static void unregisterCallbacks(
            ICentralBrainSessionEvents events,
            List<CallbackRecord> staleCallbacks) {
        if (events == null) {
            return;
        }
        for (CallbackRecord record : staleCallbacks) {
            try {
                events.unregisterSessionCallback(record.sessionId, record.callback);
            } catch (RemoteException | RuntimeException ignored) {
                // Binder death makes cleanup best effort; server death recipients own final cleanup.
            }
        }
    }

    private ICentralBrainSessionRuntime requireSession() {
        synchronized (lock) {
            if (closed || active == null || !active.isReady()) {
                throw new IllegalStateException("session Binder is not connected");
            }
            return active.session;
        }
    }

    private ICentralBrainSessionEvents requireEvents() {
        synchronized (lock) {
            if (closed || active == null || !active.isReady()) {
                throw new IllegalStateException("event Binder is not connected");
            }
            return active.events;
        }
    }

    private Listener currentListener() {
        synchronized (lock) {
            return requireListener();
        }
    }

    private Listener requireListener() {
        if (listener == null) {
            throw new IllegalStateException("transport listener is not set");
        }
        return listener;
    }

    private void rejectClosed() {
        if (closed) {
            throw new IllegalStateException("transport is closed");
        }
    }

    private boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    private void safeUnbind(ServiceConnection connection) {
        try {
            appContext.unbindService(connection);
        } catch (IllegalArgumentException ignored) {
            // The framework may already have removed a dead binding.
        }
    }

    private static void safeUnlink(IBinder binder, IBinder.DeathRecipient recipient) {
        if (binder == null || recipient == null) {
            return;
        }
        try {
            binder.unlinkToDeath(recipient, 0);
        } catch (NoSuchElementException ignored) {
            // Binder already removed the death recipient.
        }
    }

    private final class Attempt {
        private final long generation;
        private final ServiceConnection sessionConnection;
        private final ServiceConnection eventConnection;
        private boolean sessionBound;
        private boolean eventBound;
        private ICentralBrainSessionRuntime session;
        private ICentralBrainSessionEvents events;
        private IBinder sessionBinder;
        private IBinder eventBinder;
        private IBinder.DeathRecipient sessionRecipient;
        private IBinder.DeathRecipient eventRecipient;
        private boolean connectedNotified;

        private Attempt(long generation) {
            this.generation = generation;
            sessionConnection = connection(this, true);
            eventConnection = connection(this, false);
        }

        private boolean isReady() {
            return session != null
                    && events != null
                    && sessionBinder != null
                    && eventBinder != null
                    && sessionBinder.isBinderAlive()
                    && eventBinder.isBinderAlive();
        }

        @Override
        public String toString() {
            return "Attempt{" + generation + '}';
        }
    }

    private ServiceConnection connection(Attempt attempt, boolean session) {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                connected(attempt, session, service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                detach(attempt, true, null);
            }

            @Override
            public void onBindingDied(ComponentName name) {
                detach(attempt, true, null);
            }

            @Override
            public void onNullBinding(ComponentName name) {
                failAttempt(attempt, "runtime returned a null Binder", false);
            }
        };
    }

    private static final class CallbackRecord {
        private final String sessionId;
        private final ICentralBrainSessionEventCallback callback;

        private CallbackRecord(String sessionId, EventSink sink) {
            this.sessionId = sessionId;
            this.callback = new ICentralBrainSessionEventCallback.Stub() {
                @Override
                public void onEvent(RuntimeEvent event) {
                    sink.onEvent(event);
                }

                @Override
                public void onOverflow(String resumeCursor) {
                    sink.onOverflow(resumeCursor);
                }

                @Override
                public void onClosed(int reasonCode, String resumeCursor) {
                    sink.onClosed(reasonCode, resumeCursor);
                }
            };
        }
    }
}
