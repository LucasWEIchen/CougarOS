package com.centralbrain.sdk;

import android.content.Context;
import android.os.RemoteException;

import com.centralbrain.sdk.event.EventContract;
import com.centralbrain.sdk.event.EventAckRequest;
import com.centralbrain.sdk.event.EventAckResult;
import com.centralbrain.sdk.event.EventPage;
import com.centralbrain.sdk.event.EventPageV2;
import com.centralbrain.sdk.event.EventSubscriptionHandle;
import com.centralbrain.sdk.event.EventSubscriptionRequest;
import com.centralbrain.sdk.event.EventV2Contract;
import com.centralbrain.sdk.event.ICentralBrainSessionEvents;
import com.centralbrain.sdk.event.ICentralBrainSessionEventsV2;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionContract;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionPage;
import com.centralbrain.sdk.session.SessionQuery;
import com.centralbrain.sdk.session.SessionRequest;
import com.centralbrain.sdk.session.SessionSnapshot;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;

/** Reconnecting Stage 2 facade with Event V2 negotiation and V1 compatibility. */
public final class SessionClient implements ScenarioClient {
    private static final int REPLAY_PAGE_SIZE = ICentralBrainSessionEvents.MAX_PAGE_SIZE;
    private static final int MAX_REPLAY_PAGES = 64;
    private static final int EVENT_V2_QUEUE_CAPACITY = 32;

    private final Object lock = new Object();
    private final ScenarioTransport transport;
    private final SerialExecutor serialExecutor;
    private final ConnectionListener connectionListener;
    private final Map<String, Subscription> subscriptions = new LinkedHashMap<>();

    private boolean connected;
    private boolean everConnected;
    private boolean closed;

    public SessionClient(
            Context context,
            Executor callbackExecutor,
            ConnectionListener connectionListener) {
        this(new AndroidScenarioTransport(
                        Objects.requireNonNull(context, "context").getApplicationContext()),
                callbackExecutor,
                connectionListener);
    }

    SessionClient(
            ScenarioTransport transport,
            Executor callbackExecutor,
            ConnectionListener connectionListener) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.serialExecutor = new SerialExecutor(
                Objects.requireNonNull(callbackExecutor, "callbackExecutor"));
        this.connectionListener = Objects.requireNonNull(
                connectionListener,
                "connectionListener");
        transport.setListener(new TransportListener());
    }

    @Override
    public boolean connect() {
        rejectClosed();
        return transport.connect();
    }

    @Override
    public boolean reconnect() {
        rejectClosed();
        return transport.reconnect();
    }

    @Override
    public boolean isConnected() {
        synchronized (lock) {
            return !closed && connected && transport.isConnected();
        }
    }

    @Override
    public SessionHandle openSession(
            SessionRequest request,
            RuntimeEventListener listener) {
        Objects.requireNonNull(listener, "listener");
        SessionContract.validateRequest(request, System.currentTimeMillis());
        SessionHandle handle = remote(() -> transport.openSession(request));
        if (handle == null) {
            throw failure(ERROR_TRANSPORT, "runtime returned a null SessionHandle", null);
        }
        SessionContract.validateHandle(handle);
        observeSession(handle, "", listener);
        return handle;
    }

    @Override
    public SessionSnapshot getSession(SessionHandle handle) {
        SessionContract.validateHandle(handle);
        SessionSnapshot snapshot = remote(() -> transport.getSession(handle));
        if (snapshot != null) {
            SessionContract.validateSnapshot(snapshot);
        }
        return snapshot;
    }

    @Override
    public SessionPage listSessions(SessionQuery query) {
        SessionContract.validateQuery(query);
        SessionPage page = remote(() -> transport.listSessions(query));
        if (page == null) {
            throw failure(ERROR_TRANSPORT, "runtime returned a null SessionPage", null);
        }
        SessionContract.validatePage(page);
        return page;
    }

    @Override
    public boolean cancelSession(SessionHandle handle, int reasonCode) {
        SessionContract.validateHandle(handle);
        if (!SessionContract.isCancelReason(reasonCode)) {
            throw new IllegalArgumentException("CB_SESSION_CONTRACT: unknown cancel reason");
        }
        return remote(() -> transport.cancelSession(handle, reasonCode));
    }

    @Override
    public void observeSession(
            SessionHandle handle,
            String resumeCursor,
            RuntimeEventListener listener) {
        SessionContract.validateHandle(handle);
        validateCursor(resumeCursor);
        Objects.requireNonNull(listener, "listener");
        rejectClosed();

        Subscription replacement = new Subscription(copyHandle(handle), resumeCursor, listener);
        Subscription previous;
        synchronized (lock) {
            previous = subscriptions.put(handle.sessionId, replacement);
        }
        if (previous != null && isConnected()) {
            unregisterQuietly(previous);
        }
        serialExecutor.execute(() -> recover(replacement));
    }

    @Override
    public void stopObserving(SessionHandle handle) {
        SessionContract.validateHandle(handle);
        Subscription removed;
        synchronized (lock) {
            removed = subscriptions.remove(handle.sessionId);
        }
        if (removed != null) {
            removed.active = false;
            if (isConnected()) {
                unregisterQuietly(removed);
            }
        }
    }

    @Override
    public void close() {
        Map<String, Subscription> removed;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            connected = false;
            removed = new LinkedHashMap<>(subscriptions);
            subscriptions.clear();
        }
        for (Subscription subscription : removed.values()) {
            subscription.active = false;
            if (transport.isConnected()) {
                unregisterQuietly(subscription);
            }
        }
        transport.close();
    }

    private void handleConnected() {
        if (isClosed()) {
            return;
        }
        try {
            if (transport.getSessionProtocolVersion()
                            != ICentralBrainSessionRuntime.INTERFACE_VERSION
                    || !ICentralBrainSessionRuntime.INTERFACE_HASH.equals(
                            transport.getSessionProtocolHash())
                    || transport.getEventProtocolVersion()
                            != ICentralBrainSessionEvents.INTERFACE_VERSION
                    || !ICentralBrainSessionEvents.INTERFACE_HASH.equals(
                            transport.getEventProtocolHash())) {
                throw failure(
                        ERROR_PROTOCOL_MISMATCH,
                        "Session/Event Binder protocol mismatch",
                        null);
            }
            if (transport.supportsEventV2()
                    && (transport.getEventV2ProtocolVersion()
                                    != ICentralBrainSessionEventsV2.INTERFACE_VERSION
                            || !ICentralBrainSessionEventsV2.INTERFACE_HASH.equals(
                                    transport.getEventV2ProtocolHash()))) {
                throw failure(
                        ERROR_PROTOCOL_MISMATCH,
                        "Event V2 Binder protocol mismatch",
                        null);
            }
        } catch (RemoteException exception) {
            handleConnectionFailure(ERROR_TRANSPORT, "protocol negotiation failed", exception);
            return;
        } catch (Failure failure) {
            handleConnectionFailure(failure.getCode(), failure.getMessage(), failure);
            return;
        } catch (RuntimeException failure) {
            handleConnectionFailure(
                    ERROR_TRANSPORT,
                    "protocol negotiation was rejected",
                    failure);
            return;
        }

        boolean reconnected;
        Map<String, Subscription> current;
        synchronized (lock) {
            if (closed) {
                return;
            }
            connected = true;
            reconnected = everConnected;
            everConnected = true;
            current = new LinkedHashMap<>(subscriptions);
        }
        connectionListener.onConnected(this, reconnected);
        for (Subscription subscription : current.values()) {
            recover(subscription);
        }
    }

    private void handleDisconnected() {
        boolean notify;
        synchronized (lock) {
            notify = !closed && connected;
            connected = false;
        }
        if (notify) {
            connectionListener.onDisconnected();
        }
    }

    private void handleConnectionFailure(String code, String message, Throwable cause) {
        synchronized (lock) {
            connected = false;
        }
        if (!isClosed()) {
            connectionListener.onConnectionFailed(code, message);
        }
        if (cause instanceof Failure
                && ERROR_PROTOCOL_MISMATCH.equals(((Failure) cause).getCode())) {
            transport.close();
        }
    }

    private void recover(Subscription subscription) {
        if (!isCurrent(subscription) || !isConnected() || subscription.recovering) {
            return;
        }
        subscription.recovering = true;
        try {
            SessionSnapshot snapshot = transport.getSession(subscription.handle);
            if (snapshot == null) {
                subscription.listener.onError(
                        ERROR_SUBSCRIPTION,
                        "session is no longer available");
                return;
            }
            SessionContract.validateSnapshot(snapshot);
            if (!isCurrent(subscription)) {
                return;
            }
            subscription.listener.onSnapshot(snapshot);

            if (transport.supportsEventV2()) {
                recoverV2(subscription);
            } else {
                recoverV1(subscription);
            }
        } catch (RemoteException exception) {
            reportSubscriptionFailure(subscription, ERROR_TRANSPORT, exception.getMessage());
        } catch (RuntimeException exception) {
            String code = exception instanceof Failure
                    ? ((Failure) exception).getCode() : ERROR_SUBSCRIPTION;
            reportSubscriptionFailure(subscription, code, exception.getMessage());
        } finally {
            subscription.recovering = false;
        }
    }

    private void recoverV1(Subscription subscription) throws RemoteException {
        subscription.usingEventV2 = false;
        subscription.eventV2Handle = null;
        String cursor = v1Cursor(subscription.resumeCursor);
        for (int pageCount = 0; pageCount < MAX_REPLAY_PAGES; pageCount++) {
            EventPage page = transport.getEvents(
                    subscription.handle.sessionId,
                    cursor,
                    REPLAY_PAGE_SIZE);
            EventContract.validatePage(page);
            for (RuntimeEvent event : page.events) {
                deliverEvent(subscription, event);
            }
            if (!page.hasMore) {
                subscription.resumeCursor = cursor;
                if (!transport.registerSessionCallback(
                        subscription.handle.sessionId,
                        cursor,
                        subscription.sink)) {
                    throw failure(
                            ERROR_SUBSCRIPTION,
                            "runtime rejected the session callback",
                            null);
                }
                notifyReplayComplete(subscription);
                return;
            }
            cursor = page.nextCursor;
        }
        throw failure(ERROR_SUBSCRIPTION, "event replay page limit exceeded", null);
    }

    private void recoverV2(Subscription subscription) throws RemoteException {
        subscription.usingEventV2 = true;
        String cursor = subscription.resumeCursor;
        EventPageV2 terminal = null;
        for (int pageCount = 0; pageCount < MAX_REPLAY_PAGES; pageCount++) {
            EventPageV2 page = transport.getEventsV2(
                    subscription.handle.sessionId,
                    cursor,
                    ICentralBrainSessionEventsV2.MAX_PAGE_SIZE);
            EventV2Contract.validatePage(page);
            for (RuntimeEvent event : page.events) {
                deliverEvent(subscription, event);
            }
            subscription.resumeCursor = page.resumeCursor;
            if (!page.hasMore) {
                terminal = page;
                break;
            }
            cursor = page.resumeCursor;
        }
        if (terminal == null) {
            throw failure(ERROR_SUBSCRIPTION, "event V2 replay page limit exceeded", null);
        }

        EventSubscriptionRequest request = new EventSubscriptionRequest();
        request.clientSubscriptionId = subscription.handle.sessionId;
        request.sessionId = subscription.handle.sessionId;
        request.resumeCursor = terminal.resumeCursor;
        request.resumeSequence = terminal.resumeSequence;
        request.queueCapacity = EVENT_V2_QUEUE_CAPACITY;
        EventV2Contract.validateSubscriptionRequest(request);
        EventSubscriptionHandle handle = transport.registerSessionCallbackV2(
                request,
                subscription.sink);
        if (handle == null) {
            throw failure(
                    ERROR_SUBSCRIPTION,
                    "runtime rejected the Event V2 callback",
                    null);
        }
        EventV2Contract.validateSubscriptionHandle(handle);
        if (!subscription.handle.sessionId.equals(handle.sessionId)
                || !request.clientSubscriptionId.equals(handle.clientSubscriptionId)
                || handle.acknowledgedSequence != terminal.resumeSequence) {
            throw failure(ERROR_SUBSCRIPTION, "runtime returned an inconsistent V2 handle", null);
        }
        subscription.eventV2Handle = handle;
        subscription.resumeCursor = handle.resumeCursor;
        notifyReplayComplete(subscription);
    }

    private void notifyReplayComplete(Subscription subscription) {
        if (isCurrent(subscription)) {
            subscription.listener.onReplayComplete(subscription.lastSequence);
        }
    }

    private void deliverEvent(Subscription subscription, RuntimeEvent event) {
        EventContract.validateEvent(event);
        if (!subscription.handle.sessionId.equals(event.sessionId)) {
            throw failure(ERROR_SUBSCRIPTION, "cross-session event rejected", null);
        }
        if (!isCurrent(subscription) || event.sequence <= subscription.lastSequence) {
            return;
        }
        if (subscription.lastSequence > 0
                && event.sequence != subscription.lastSequence + 1) {
            throw failure(ERROR_SUBSCRIPTION, "non-contiguous event sequence", null);
        }
        subscription.lastSequence = event.sequence;
        subscription.listener.onEvent(event);
    }

    private void handleEvent(Subscription subscription, RuntimeEvent event) {
        if (!isCurrent(subscription)) {
            return;
        }
        try {
            deliverEvent(subscription, event);
        } catch (RuntimeException failure) {
            reportSubscriptionFailure(subscription, ERROR_SUBSCRIPTION, failure.getMessage());
            recover(subscription);
        }
    }

    private void handleEventV2(
            Subscription subscription,
            RuntimeEvent event,
            String resumeCursor) {
        if (!isCurrent(subscription)) {
            return;
        }
        try {
            EventSubscriptionHandle handle = subscription.eventV2Handle;
            if (!subscription.usingEventV2 || handle == null) {
                throw failure(ERROR_SUBSCRIPTION, "Event V2 callback arrived without a handle", null);
            }
            long cursorSequence = EventV2Contract.cursorSequence(resumeCursor, false);
            if (cursorSequence != event.sequence) {
                throw failure(ERROR_SUBSCRIPTION, "Event V2 callback cursor mismatch", null);
            }
            deliverEvent(subscription, event);

            EventAckRequest request = new EventAckRequest();
            request.subscriptionId = handle.subscriptionId;
            request.sessionId = subscription.handle.sessionId;
            request.resumeCursor = resumeCursor;
            request.acknowledgedSequence = event.sequence;
            EventV2Contract.validateAckRequest(request);
            EventAckResult result = transport.acknowledgeV2(request);
            EventV2Contract.validateAckResult(result);
            if (!handle.subscriptionId.equals(result.subscriptionId)
                    || result.acknowledgedSequence != event.sequence
                    || (result.outcome != ICentralBrainSessionEventsV2.ACK_APPLIED
                            && result.outcome != ICentralBrainSessionEventsV2.ACK_REPLAYED)) {
                throw failure(
                        ERROR_SUBSCRIPTION,
                        "Event V2 ACK was not accepted: " + result.outcome,
                        null);
            }
            subscription.resumeCursor = result.resumeCursor;
            handle.resumeCursor = result.resumeCursor;
            handle.acknowledgedSequence = result.acknowledgedSequence;
        } catch (RemoteException failure) {
            reportSubscriptionFailure(subscription, ERROR_TRANSPORT, failure.getMessage());
            recover(subscription);
        } catch (RuntimeException failure) {
            String code = failure instanceof Failure
                    ? ((Failure) failure).getCode() : ERROR_SUBSCRIPTION;
            reportSubscriptionFailure(subscription, code, failure.getMessage());
            recover(subscription);
        }
    }

    private void handleOverflow(Subscription subscription, String resumeCursor) {
        if (!isCurrent(subscription)) {
            return;
        }
        try {
            validateCursor(resumeCursor);
        } catch (IllegalArgumentException failure) {
            reportSubscriptionFailure(subscription, ERROR_SUBSCRIPTION, failure.getMessage());
            return;
        }
        subscription.resumeCursor = resumeCursor;
        subscription.listener.onOverflow(subscription.resumeCursor);
        recover(subscription);
    }

    private void handleClosed(
            Subscription subscription,
            int reasonCode,
            String resumeCursor) {
        if (!isCurrent(subscription)) {
            return;
        }
        String safeCursor = resumeCursor == null ? "" : resumeCursor;
        try {
            validateCursor(safeCursor);
        } catch (IllegalArgumentException failure) {
            reportSubscriptionFailure(subscription, ERROR_SUBSCRIPTION, failure.getMessage());
            safeCursor = "";
        }
        subscription.listener.onClosed(
                reasonCode,
                safeCursor);
    }

    private void reportSubscriptionFailure(
            Subscription subscription,
            String code,
            String message) {
        if (isCurrent(subscription)) {
            subscription.listener.onError(code, message == null ? "subscription failed" : message);
        }
    }

    private void unregisterQuietly(Subscription subscription) {
        try {
            if (subscription.usingEventV2 && subscription.eventV2Handle != null) {
                EventSubscriptionHandle handle = subscription.eventV2Handle;
                transport.unregisterSessionCallbackV2(handle, subscription.sink);
                transport.cancelSubscriptionV2(handle);
                subscription.eventV2Handle = null;
            } else {
                transport.unregisterSessionCallback(
                        subscription.handle.sessionId,
                        subscription.sink);
            }
        } catch (RemoteException | RuntimeException ignored) {
            // Binder death and close are idempotent from the facade's perspective.
        }
    }

    private boolean isCurrent(Subscription subscription) {
        synchronized (lock) {
            return !closed
                    && subscription.active
                    && subscriptions.get(subscription.handle.sessionId) == subscription;
        }
    }

    private boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    private void rejectClosed() {
        synchronized (lock) {
            if (closed) {
                throw failure(ERROR_CLOSED, "scenario client is closed", null);
            }
        }
    }

    private void requireConnected() {
        if (!isConnected()) {
            throw failure(ERROR_NOT_CONNECTED, "scenario runtime is not connected", null);
        }
    }

    private <T> T remote(RemoteCall<T> call) {
        requireConnected();
        try {
            return call.call();
        } catch (RemoteException exception) {
            throw failure(ERROR_TRANSPORT, "scenario Binder call failed", exception);
        }
    }

    private static Failure failure(String code, String message, Throwable cause) {
        return new Failure(code, message, cause);
    }

    private static SessionHandle copyHandle(SessionHandle original) {
        SessionHandle copy = new SessionHandle();
        copy.schemaVersion = original.schemaVersion;
        copy.sessionId = original.sessionId;
        copy.acceptedAtEpochMs = original.acceptedAtEpochMs;
        copy.expiresAtEpochMs = original.expiresAtEpochMs;
        return copy;
    }

    private static void validateCursor(String cursor) {
        Objects.requireNonNull(cursor, "resumeCursor");
        if (cursor.length() > EventContract.MAX_CURSOR_CHARS) {
            throw new IllegalArgumentException("CB_EVENT_CONTRACT: resume cursor is oversized");
        }
        for (int index = 0; index < cursor.length(); index++) {
            if (Character.isISOControl(cursor.charAt(index))) {
                throw new IllegalArgumentException(
                        "CB_EVENT_CONTRACT: resume cursor contains a control character");
            }
        }
    }

    private static String v1Cursor(String cursor) {
        if (cursor.startsWith("ev2:")) {
            long sequence = EventV2Contract.cursorSequence(cursor, false);
            return sequence == 0 ? "" : "e:" + sequence;
        }
        return cursor;
    }

    private interface RemoteCall<T> {
        T call() throws RemoteException;
    }

    private final class TransportListener implements ScenarioTransport.Listener {
        @Override
        public void onConnected() {
            serialExecutor.execute(SessionClient.this::handleConnected);
        }

        @Override
        public void onDisconnected() {
            serialExecutor.execute(SessionClient.this::handleDisconnected);
        }

        @Override
        public void onConnectionFailed(String message) {
            serialExecutor.execute(() -> handleConnectionFailure(
                    ERROR_TRANSPORT,
                    message,
                    null));
        }
    }

    private final class Subscription {
        private final SessionHandle handle;
        private final RuntimeEventListener listener;
        private final ScenarioTransport.EventSink sink;
        private String resumeCursor;
        private long lastSequence;
        private EventSubscriptionHandle eventV2Handle;
        private boolean usingEventV2;
        private boolean active = true;
        private boolean recovering;

        private Subscription(
                SessionHandle handle,
                String resumeCursor,
                RuntimeEventListener listener) {
            this.handle = handle;
            this.resumeCursor = resumeCursor;
            this.listener = listener;
            this.sink = new ScenarioTransport.EventSink() {
                @Override
                public void onEvent(RuntimeEvent event) {
                    serialExecutor.execute(() -> handleEvent(Subscription.this, event));
                }

                @Override
                public void onEventV2(RuntimeEvent event, String cursor) {
                    serialExecutor.execute(() -> handleEventV2(
                            Subscription.this,
                            event,
                            cursor));
                }

                @Override
                public void onOverflow(String cursor) {
                    serialExecutor.execute(() -> handleOverflow(Subscription.this, cursor));
                }

                @Override
                public void onClosed(int reasonCode, String cursor) {
                    serialExecutor.execute(() -> handleClosed(
                            Subscription.this,
                            reasonCode,
                            cursor));
                }
            };
        }
    }

    private static final class SerialExecutor implements Executor {
        private final Executor delegate;
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private Runnable active;

        private SerialExecutor(Executor delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void execute(Runnable command) {
            tasks.add(() -> {
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
