package com.centralbrain.runtime.session;

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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Binder publication for the process-local Stage 2 Session/Event V1 contracts. */
public final class TransientSessionEndpoint implements AutoCloseable {
    public static final int MAX_CALLBACKS_PER_SESSION = 4;
    public static final int MAX_CALLBACKS_TOTAL = 128;
    public enum Operation {
        SESSION_PROTOCOL_READ,
        SESSION_OPEN,
        SESSION_READ_OWN,
        SESSION_CANCEL_OWN,
        EVENT_PROTOCOL_READ,
        EVENT_READ_OWN,
        EVENT_SUBSCRIBE_OWN
    }

    public interface Authorizer {
        String requireOwner(Operation operation);
    }

    private final Object lock = new Object();
    private final Authorizer authorizer;
    private final TransientSessionRegistry registry;
    private final Map<String, List<CallbackRecord>> callbacksBySession =
            new LinkedHashMap<>();
    private boolean closed;

    private final ICentralBrainSessionRuntime.Stub sessionBinder =
            new ICentralBrainSessionRuntime.Stub() {
                @Override
                public int getProtocolVersion() {
                    authorizer.requireOwner(Operation.SESSION_PROTOCOL_READ);
                    return ICentralBrainSessionRuntime.INTERFACE_VERSION;
                }

                @Override
                public String getProtocolHash() {
                    authorizer.requireOwner(Operation.SESSION_PROTOCOL_READ);
                    return ICentralBrainSessionRuntime.INTERFACE_HASH;
                }

                @Override
                public SessionHandle openSession(SessionRequest request) {
                    String owner = authorizer.requireOwner(Operation.SESSION_OPEN);
                    synchronized (lock) {
                        rejectClosed();
                        return registry.openOwned(owner, request);
                    }
                }

                @Override
                public SessionSnapshot getSession(SessionHandle handle) {
                    String owner = authorizer.requireOwner(Operation.SESSION_READ_OWN);
                    synchronized (lock) {
                        rejectClosed();
                        return registry.findOwned(owner, handle);
                    }
                }

                @Override
                public SessionPage listSessions(SessionQuery query) {
                    String owner = authorizer.requireOwner(Operation.SESSION_READ_OWN);
                    synchronized (lock) {
                        rejectClosed();
                        return registry.listOwned(owner, query);
                    }
                }

                @Override
                public boolean cancelSession(SessionHandle handle, int reasonCode) {
                    String owner = authorizer.requireOwner(Operation.SESSION_CANCEL_OWN);
                    synchronized (lock) {
                        rejectClosed();
                        TransientSessionRegistry.CancelResult result =
                                registry.cancelOwned(owner, handle, reasonCode);
                        if (result.isChanged()) {
                            dispatchOwned(owner, handle.sessionId, result.getEvent());
                        }
                        return result.isChanged();
                    }
                }
            };

    private final ICentralBrainSessionEvents.Stub eventBinder =
            new ICentralBrainSessionEvents.Stub() {
                @Override
                public int getProtocolVersion() {
                    authorizer.requireOwner(Operation.EVENT_PROTOCOL_READ);
                    return ICentralBrainSessionEvents.INTERFACE_VERSION;
                }

                @Override
                public String getProtocolHash() {
                    authorizer.requireOwner(Operation.EVENT_PROTOCOL_READ);
                    return ICentralBrainSessionEvents.INTERFACE_HASH;
                }

                @Override
                public EventPage getEvents(String sessionId, String cursor, int limit) {
                    String owner = authorizer.requireOwner(Operation.EVENT_READ_OWN);
                    synchronized (lock) {
                        rejectClosed();
                        return registry.eventsOwned(owner, sessionId, cursor, limit);
                    }
                }

                @Override
                public boolean registerSessionCallback(
                        String sessionId,
                        String cursor,
                        ICentralBrainSessionEventCallback callback) {
                    String owner = authorizer.requireOwner(Operation.EVENT_SUBSCRIBE_OWN);
                    Objects.requireNonNull(callback, "callback");
                    synchronized (lock) {
                        rejectClosed();
                        return registerLocked(owner, sessionId, cursor, callback);
                    }
                }

                @Override
                public boolean unregisterSessionCallback(
                        String sessionId,
                        ICentralBrainSessionEventCallback callback) {
                    String owner = authorizer.requireOwner(Operation.EVENT_SUBSCRIBE_OWN);
                    Objects.requireNonNull(callback, "callback");
                    synchronized (lock) {
                        rejectClosed();
                        return unregisterLocked(owner, sessionId, callback.asBinder());
                    }
                }
            };

    public TransientSessionEndpoint(Authorizer authorizer) {
        this(authorizer, ProcessRegistryHolder.INSTANCE);
    }

    TransientSessionEndpoint(
            Authorizer authorizer,
            TransientSessionRegistry registry) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public IBinder sessionBinder() {
        return sessionBinder;
    }

    public IBinder eventBinder() {
        return eventBinder;
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            for (List<CallbackRecord> records : callbacksBySession.values()) {
                for (CallbackRecord record : records) {
                    try {
                        record.callback.onClosed(
                                ICentralBrainSessionRuntime.CANCEL_REASON_CALLER_GONE,
                                record.resumeCursor);
                    } catch (RemoteException ignored) {
                        // Closing is best effort; no payload is retained for retry.
                    }
                    safeUnlink(record);
                }
            }
            callbacksBySession.clear();
        }
    }

    private boolean registerLocked(
            String owner,
            String sessionId,
            String cursor,
            ICentralBrainSessionEventCallback callback) {
        IBinder binder = callback.asBinder();
        unregisterLocked(owner, sessionId, binder);
        List<CallbackRecord> existing = callbacksBySession.get(sessionId);
        if ((existing != null && existing.size() >= MAX_CALLBACKS_PER_SESSION)
                || callbackCount() >= MAX_CALLBACKS_TOTAL) {
            return false;
        }
        CallbackRecord record = new CallbackRecord(owner, sessionId, cursor, callback);
        record.deathRecipient = () -> removeDead(record);
        try {
            binder.linkToDeath(record.deathRecipient, 0);
            String replayCursor = cursor;
            for (int pageCount = 0; pageCount < 64; pageCount++) {
                EventPage page = registry.eventsOwned(
                        owner,
                        sessionId,
                        replayCursor,
                        ICentralBrainSessionEvents.MAX_PAGE_SIZE);
                for (RuntimeEvent event : page.events) {
                    callback.onEvent(event);
                }
                if (!page.hasMore) {
                    record.resumeCursor = replayCursor;
                    if (!binder.isBinderAlive()) {
                        safeUnlink(record);
                        return false;
                    }
                    callbacksBySession
                            .computeIfAbsent(sessionId, ignored -> new ArrayList<>())
                            .add(record);
                    return true;
                }
                replayCursor = page.nextCursor;
            }
        } catch (RemoteException failure) {
            safeUnlink(record);
            return false;
        }
        safeUnlink(record);
        throw new IllegalStateException("CB_SESSION_RUNTIME: callback replay limit exceeded");
    }

    private boolean unregisterLocked(String owner, String sessionId, IBinder binder) {
        List<CallbackRecord> records = callbacksBySession.get(sessionId);
        if (records == null) {
            return true;
        }
        Iterator<CallbackRecord> iterator = records.iterator();
        while (iterator.hasNext()) {
            CallbackRecord record = iterator.next();
            if (record.owner.equals(owner) && record.callback.asBinder() == binder) {
                iterator.remove();
                safeUnlink(record);
            }
        }
        if (records.isEmpty()) {
            callbacksBySession.remove(sessionId);
        }
        return true;
    }

    private void dispatchOwned(String owner, String sessionId, RuntimeEvent event) {
        List<CallbackRecord> records = callbacksBySession.get(sessionId);
        if (records == null) {
            return;
        }
        Iterator<CallbackRecord> iterator = records.iterator();
        while (iterator.hasNext()) {
            CallbackRecord record = iterator.next();
            if (!record.owner.equals(owner)) {
                continue;
            }
            try {
                record.callback.onEvent(event);
            } catch (RemoteException failure) {
                iterator.remove();
                safeUnlink(record);
            }
        }
        if (records.isEmpty()) {
            callbacksBySession.remove(sessionId);
        }
    }

    private void removeDead(CallbackRecord dead) {
        synchronized (lock) {
            List<CallbackRecord> records = callbacksBySession.get(dead.sessionId);
            if (records == null) {
                return;
            }
            records.remove(dead);
            if (records.isEmpty()) {
                callbacksBySession.remove(dead.sessionId);
            }
        }
    }

    private void rejectClosed() {
        if (closed) {
            throw new IllegalStateException("CB_SESSION_RUNTIME: endpoint is closed");
        }
    }

    private int callbackCount() {
        int count = 0;
        for (List<CallbackRecord> records : callbacksBySession.values()) {
            count += records.size();
        }
        return count;
    }

    private static void safeUnlink(CallbackRecord record) {
        if (record.deathRecipient == null) {
            return;
        }
        try {
            record.callback.asBinder().unlinkToDeath(record.deathRecipient, 0);
        } catch (NoSuchElementException ignored) {
            // Binder already removed the recipient.
        }
    }

    private static final class CallbackRecord {
        private final String owner;
        private final String sessionId;
        private final ICentralBrainSessionEventCallback callback;
        private String resumeCursor;
        private IBinder.DeathRecipient deathRecipient;

        private CallbackRecord(
                String owner,
                String sessionId,
                String resumeCursor,
                ICentralBrainSessionEventCallback callback) {
            this.owner = owner;
            this.sessionId = sessionId;
            this.resumeCursor = resumeCursor;
            this.callback = callback;
        }
    }

    /** Survives Service rebinds, but is intentionally lost when the Runtime process dies. */
    private static final class ProcessRegistryHolder {
        private static final TransientSessionRegistry INSTANCE =
                TransientSessionRegistry.createDefault();
    }
}
