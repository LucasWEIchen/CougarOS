package com.centralbrain.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import com.centralbrain.sdk.event.EventPage;
import com.centralbrain.sdk.event.EventAckRequest;
import com.centralbrain.sdk.event.EventAckResult;
import com.centralbrain.sdk.event.EventPageV2;
import com.centralbrain.sdk.event.EventSubscriptionHandle;
import com.centralbrain.sdk.event.EventSubscriptionRequest;
import com.centralbrain.sdk.event.ICentralBrainSessionEventCallback;
import com.centralbrain.sdk.event.ICentralBrainSessionEventCallbackV2;
import com.centralbrain.sdk.event.ICentralBrainSessionEvents;
import com.centralbrain.sdk.event.ICentralBrainSessionEventsV2;
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

/** Android explicit-component transport for the Stage 2 Session/Event Binder actions. */
final class AndroidScenarioTransport implements ScenarioTransport {
    private final Context appContext;
    private final Object lock = new Object();
    private final Map<EventSink, CallbackRecord> callbacks = new IdentityHashMap<>();
    private final Map<EventSink, CallbackRecordV2> callbacksV2 = new IdentityHashMap<>();

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

        Intent eventV2Intent = new Intent(CentralBrainSdk.ACTION_SESSION_EVENTS_V2)
                .setComponent(CentralBrainClient.RUNTIME_COMPONENT);
        attempt.eventV2Bound = appContext.bindService(
                eventV2Intent,
                attempt.eventV2Connection,
                Context.BIND_AUTO_CREATE);
        if (!attempt.eventV2Bound) {
            resolveEventV2(attempt, null, null, null);
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
    public boolean supportsEventV2() {
        synchronized (lock) {
            return !closed
                    && active != null
                    && active.isReady()
                    && active.eventsV2 != null;
        }
    }

    @Override
    public int getEventV2ProtocolVersion() throws RemoteException {
        return requireEventsV2().getProtocolVersion();
    }

    @Override
    public String getEventV2ProtocolHash() throws RemoteException {
        return requireEventsV2().getProtocolHash();
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
    public EventPageV2 getEventsV2(String sessionId, String cursor, int limit)
            throws RemoteException {
        return requireEventsV2().getEvents(sessionId, cursor, limit);
    }

    @Override
    public EventSubscriptionHandle registerSessionCallbackV2(
            EventSubscriptionRequest request,
            EventSink sink) throws RemoteException {
        Objects.requireNonNull(sink, "sink");
        ICentralBrainSessionEventsV2 events = requireEventsV2();
        CallbackRecordV2 replacement = new CallbackRecordV2(sink);
        EventSubscriptionHandle handle = events.registerSessionCallback(
                request,
                replacement.callback);
        if (handle == null) {
            return null;
        }
        replacement.handle = handle;
        CallbackRecordV2 previous;
        synchronized (lock) {
            if (closed
                    || active == null
                    || !active.isReady()
                    || active.eventsV2 != events) {
                try {
                    events.unregisterSessionCallback(handle, replacement.callback);
                } catch (RemoteException ignored) {
                    // Connection already failed.
                }
                return null;
            }
            previous = callbacksV2.put(sink, replacement);
        }
        if (previous != null && previous.handle != null) {
            try {
                events.unregisterSessionCallback(previous.handle, previous.callback);
            } catch (RemoteException ignored) {
                // Replacement is authoritative.
            }
        }
        return handle;
    }

    @Override
    public EventAckResult acknowledgeV2(EventAckRequest request) throws RemoteException {
        return requireEventsV2().acknowledge(request);
    }

    @Override
    public boolean unregisterSessionCallbackV2(
            EventSubscriptionHandle handle,
            EventSink sink) throws RemoteException {
        Objects.requireNonNull(sink, "sink");
        CallbackRecordV2 record;
        synchronized (lock) {
            record = callbacksV2.remove(sink);
        }
        if (record == null) {
            return true;
        }
        EventSubscriptionHandle effective = handle == null ? record.handle : handle;
        return effective == null
                || requireEventsV2().unregisterSessionCallback(effective, record.callback);
    }

    @Override
    public boolean cancelSubscriptionV2(EventSubscriptionHandle handle)
            throws RemoteException {
        return requireEventsV2().cancelSubscription(handle);
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

    private void connectedV2(Attempt attempt, IBinder binder) {
        IBinder.DeathRecipient recipient = () -> detach(attempt, true, null);
        try {
            binder.linkToDeath(recipient, 0);
            ICentralBrainSessionEventsV2 candidate =
                    ICentralBrainSessionEventsV2.Stub.asInterface(binder);
            if (candidate == null
                    || candidate.getProtocolVersion()
                            != ICentralBrainSessionEventsV2.INTERFACE_VERSION
                    || !ICentralBrainSessionEventsV2.INTERFACE_HASH.equals(
                            candidate.getProtocolHash())) {
                safeUnlink(binder, recipient);
                resolveEventV2(attempt, null, null, null);
                return;
            }
            resolveEventV2(attempt, binder, recipient, candidate);
        } catch (RemoteException | RuntimeException failure) {
            safeUnlink(binder, recipient);
            resolveEventV2(attempt, null, null, null);
        }
    }

    private void resolveEventV2(
            Attempt attempt,
            IBinder binder,
            IBinder.DeathRecipient recipient,
            ICentralBrainSessionEventsV2 events) {
        boolean notify = false;
        synchronized (lock) {
            if (closed || active != attempt) {
                safeUnlink(binder, recipient);
                return;
            }
            attempt.eventV2Binder = binder;
            attempt.eventV2Recipient = recipient;
            attempt.eventsV2 = events;
            attempt.eventV2Resolved = true;
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
        ICentralBrainSessionEventsV2 eventsV2;
        List<CallbackRecordV2> staleCallbacksV2;
        synchronized (lock) {
            wasCurrent = active == attempt;
            if (!wasCurrent) {
                return;
            }
            active = null;
            events = attempt.events;
            staleCallbacks = new ArrayList<>(callbacks.values());
            callbacks.clear();
            eventsV2 = attempt.eventsV2;
            staleCallbacksV2 = new ArrayList<>(callbacksV2.values());
            callbacksV2.clear();
        }
        unregisterCallbacks(events, staleCallbacks);
        unregisterCallbacksV2(eventsV2, staleCallbacksV2);
        safeUnlink(attempt.sessionBinder, attempt.sessionRecipient);
        safeUnlink(attempt.eventBinder, attempt.eventRecipient);
        safeUnlink(attempt.eventV2Binder, attempt.eventV2Recipient);
        if (attempt.sessionBound) {
            safeUnbind(attempt.sessionConnection);
        }
        if (attempt.eventBound) {
            safeUnbind(attempt.eventConnection);
        }
        if (attempt.eventV2Bound) {
            safeUnbind(attempt.eventV2Connection);
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

    private static void unregisterCallbacksV2(
            ICentralBrainSessionEventsV2 events,
            List<CallbackRecordV2> staleCallbacks) {
        if (events == null) {
            return;
        }
        for (CallbackRecordV2 record : staleCallbacks) {
            if (record.handle == null) {
                continue;
            }
            try {
                events.unregisterSessionCallback(record.handle, record.callback);
            } catch (RemoteException | RuntimeException ignored) {
                // Durable ACK state remains available for the next connection.
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

    private ICentralBrainSessionEventsV2 requireEventsV2() {
        synchronized (lock) {
            if (closed
                    || active == null
                    || !active.isReady()
                    || active.eventsV2 == null) {
                throw new IllegalStateException("event V2 Binder is not connected");
            }
            return active.eventsV2;
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
        private final ServiceConnection eventV2Connection;
        private boolean sessionBound;
        private boolean eventBound;
        private boolean eventV2Bound;
        private ICentralBrainSessionRuntime session;
        private ICentralBrainSessionEvents events;
        private ICentralBrainSessionEventsV2 eventsV2;
        private IBinder sessionBinder;
        private IBinder eventBinder;
        private IBinder eventV2Binder;
        private IBinder.DeathRecipient sessionRecipient;
        private IBinder.DeathRecipient eventRecipient;
        private IBinder.DeathRecipient eventV2Recipient;
        private boolean eventV2Resolved;
        private boolean connectedNotified;

        private Attempt(long generation) {
            this.generation = generation;
            sessionConnection = connection(this, true);
            eventConnection = connection(this, false);
            eventV2Connection = connectionV2(this);
        }

        private boolean isReady() {
            return session != null
                    && events != null
                    && sessionBinder != null
                    && eventBinder != null
                    && eventV2Resolved
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

    private ServiceConnection connectionV2(Attempt attempt) {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                connectedV2(attempt, service);
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
                resolveEventV2(attempt, null, null, null);
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

    private static final class CallbackRecordV2 {
        private final ICentralBrainSessionEventCallbackV2 callback;
        private EventSubscriptionHandle handle;

        private CallbackRecordV2(EventSink sink) {
            callback = new ICentralBrainSessionEventCallbackV2.Stub() {
                @Override
                public void onEvent(RuntimeEvent event, String resumeCursor) {
                    sink.onEventV2(event, resumeCursor);
                }

                @Override
                public void onOverflow(String resumeCursor, long droppedCount) {
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
