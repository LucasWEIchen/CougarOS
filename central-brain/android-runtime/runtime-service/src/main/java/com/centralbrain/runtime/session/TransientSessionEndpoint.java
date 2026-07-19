package com.centralbrain.runtime.session;

import android.os.IBinder;
import android.os.RemoteException;

import com.centralbrain.sdk.event.EventPage;
import com.centralbrain.sdk.event.EventPageV2;
import com.centralbrain.sdk.event.EventAckRequest;
import com.centralbrain.sdk.event.EventAckResult;
import com.centralbrain.sdk.event.EventSubscriptionHandle;
import com.centralbrain.sdk.event.EventSubscriptionRequest;
import com.centralbrain.sdk.event.EventV2Contract;
import com.centralbrain.sdk.event.ICentralBrainSessionEventCallback;
import com.centralbrain.sdk.event.ICentralBrainSessionEventCallbackV2;
import com.centralbrain.sdk.event.ICentralBrainSessionEvents;
import com.centralbrain.sdk.event.ICentralBrainSessionEventsV2;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.runtime.persistence.DurableEventCursorRepository;
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

/** Binder publication for Stage 2 Session/Event V1; callback registrations remain process-local. */
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
        EVENT_SUBSCRIBE_OWN,
        EVENT_ACK_OWN
    }

    public interface Authorizer {
        String requireOwner(Operation operation);
    }

    private final Object lock = new Object();
    private final Authorizer authorizer;
    private final SessionRegistry registry;
    private final DurableEventCursorRepository durableEventCursors;
    private final SessionEventCursorCodec cursorCodec = new SessionEventCursorCodec();
    private final Map<String, List<CallbackRecord>> callbacksBySession =
            new LinkedHashMap<>();
    private final Map<String, List<CallbackRecordV2>> callbacksV2BySession =
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
                        SessionRegistry.CancelResult result =
                                registry.cancelOwned(owner, handle, reasonCode);
                        if (result.isChanged()) {
                            dispatchOwned(owner, handle.sessionId, result.getEvent());
                        }
                        return result.isChanged();
                    }
                }
            };

    private final ICentralBrainSessionEventsV2.Stub eventBinderV2 =
            new ICentralBrainSessionEventsV2.Stub() {
                @Override
                public int getProtocolVersion() {
                    authorizer.requireOwner(Operation.EVENT_PROTOCOL_READ);
                    return ICentralBrainSessionEventsV2.INTERFACE_VERSION;
                }

                @Override
                public String getProtocolHash() {
                    authorizer.requireOwner(Operation.EVENT_PROTOCOL_READ);
                    return ICentralBrainSessionEventsV2.INTERFACE_HASH;
                }

                @Override
                public EventPageV2 getEvents(String sessionId, String cursor, int limit) {
                    String owner = authorizer.requireOwner(Operation.EVENT_READ_OWN);
                    synchronized (lock) {
                        rejectClosed();
                        return pageV2(owner, sessionId, cursor, limit);
                    }
                }

                @Override
                public EventSubscriptionHandle registerSessionCallback(
                        EventSubscriptionRequest request,
                        ICentralBrainSessionEventCallbackV2 callback) {
                    String owner = authorizer.requireOwner(Operation.EVENT_SUBSCRIBE_OWN);
                    Objects.requireNonNull(callback, "callback");
                    synchronized (lock) {
                        rejectClosed();
                        return registerV2Locked(owner, request, callback);
                    }
                }

                @Override
                public EventAckResult acknowledge(EventAckRequest request) {
                    String owner = authorizer.requireOwner(Operation.EVENT_ACK_OWN);
                    synchronized (lock) {
                        rejectClosed();
                        return acknowledgeV2Locked(owner, request);
                    }
                }

                @Override
                public boolean unregisterSessionCallback(
                        EventSubscriptionHandle handle,
                        ICentralBrainSessionEventCallbackV2 callback) {
                    String owner = authorizer.requireOwner(Operation.EVENT_SUBSCRIBE_OWN);
                    Objects.requireNonNull(callback, "callback");
                    synchronized (lock) {
                        rejectClosed();
                        EventV2Contract.validateSubscriptionHandle(handle);
                        return unregisterV2Locked(owner, handle, callback.asBinder());
                    }
                }

                @Override
                public boolean cancelSubscription(EventSubscriptionHandle handle) {
                    String owner = authorizer.requireOwner(Operation.EVENT_SUBSCRIBE_OWN);
                    synchronized (lock) {
                        rejectClosed();
                        EventV2Contract.validateSubscriptionHandle(handle);
                        removeSubscriptionCallbacksV2(owner, handle);
                        return durableEventCursors.cancelSessionOwned(
                                        handle.subscriptionId,
                                        owner,
                                        handle.sessionId)
                                != DurableEventCursorRepository.CancelOutcome.NOT_FOUND;
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

    public TransientSessionEndpoint(
            Authorizer authorizer,
            SessionRegistry registry,
            DurableEventCursorRepository durableEventCursors) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.durableEventCursors = Objects.requireNonNull(
                durableEventCursors,
                "durableEventCursors");
    }

    public IBinder sessionBinder() {
        return sessionBinder;
    }

    public IBinder eventBinder() {
        return eventBinder;
    }

    public IBinder eventBinderV2() {
        return eventBinderV2;
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
            for (List<CallbackRecordV2> records : callbacksV2BySession.values()) {
                for (CallbackRecordV2 record : records) {
                    try {
                        record.callback.onClosed(
                                ICentralBrainSessionRuntime.CANCEL_REASON_CALLER_GONE,
                                record.resumeCursor);
                    } catch (RemoteException ignored) {
                        // Durable ACK state remains available for reconnect.
                    }
                    safeUnlink(record);
                }
            }
            callbacksV2BySession.clear();
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

    private EventSubscriptionHandle registerV2Locked(
            String owner,
            EventSubscriptionRequest request,
            ICentralBrainSessionEventCallbackV2 callback) {
        EventV2Contract.validateSubscriptionRequest(request);
        long requestedSequence = cursorCodec.decode(
                owner,
                request.sessionId,
                request.resumeCursor,
                true);
        if (requestedSequence != request.resumeSequence) {
            throw new IllegalArgumentException("CB_EVENT_V2: request cursor mismatch");
        }
        long latestSequence = registry.latestEventSequenceOwned(owner, request.sessionId);
        DurableEventCursorRepository.RegisterResult registration =
                durableEventCursors.registerSession(
                        owner,
                        request.clientSubscriptionId,
                        request.sessionId,
                        requestedSequence,
                        request.queueCapacity,
                        latestSequence);
        if (registration.getOutcome()
                        != DurableEventCursorRepository.RegisterOutcome.CREATED
                && registration.getOutcome()
                        != DurableEventCursorRepository.RegisterOutcome.REOPENED
                && registration.getOutcome()
                        != DurableEventCursorRepository.RegisterOutcome.REPLAYED) {
            throw new IllegalArgumentException(
                    "CB_EVENT_V2: subscription rejected " + registration.getOutcome().name());
        }

        DurableEventCursorRepository.Snapshot snapshot = registration.getSnapshot();
        EventSubscriptionHandle handle = handleV2(owner, request.sessionId, snapshot);
        unregisterV2Locked(owner, handle, callback.asBinder());
        List<CallbackRecordV2> existing = callbacksV2BySession.get(request.sessionId);
        if ((existing != null && existing.size() >= MAX_CALLBACKS_PER_SESSION)
                || callbackCount() + callbackCountV2() >= MAX_CALLBACKS_TOTAL) {
            throw new IllegalStateException("CB_EVENT_V2: callback capacity exhausted");
        }

        CallbackRecordV2 record = new CallbackRecordV2(
                owner,
                request.sessionId,
                snapshot.getCursorId(),
                callback,
                cursorCodec.encode(owner, request.sessionId, requestedSequence));
        record.deathRecipient = () -> removeDead(record);
        try {
            callback.asBinder().linkToDeath(record.deathRecipient, 0);
            String cursor = request.resumeCursor;
            for (int pageCount = 0; pageCount < 64; pageCount++) {
                EventPageV2 page = pageV2(
                        owner,
                        request.sessionId,
                        cursor,
                        ICentralBrainSessionEventsV2.MAX_PAGE_SIZE);
                for (RuntimeEvent event : page.events) {
                    callback.onEvent(
                            event,
                            cursorCodec.encode(owner, request.sessionId, event.sequence));
                }
                record.resumeCursor = page.resumeCursor;
                if (!page.hasMore) {
                    if (!callback.asBinder().isBinderAlive()) {
                        safeUnlink(record);
                        throw new IllegalStateException("CB_EVENT_V2: callback binder died");
                    }
                    callbacksV2BySession
                            .computeIfAbsent(request.sessionId, ignored -> new ArrayList<>())
                            .add(record);
                    return handle;
                }
                cursor = page.resumeCursor;
            }
        } catch (RemoteException failure) {
            safeUnlink(record);
            throw new IllegalStateException("CB_EVENT_V2: callback replay failed", failure);
        }
        safeUnlink(record);
        throw new IllegalStateException("CB_EVENT_V2: callback replay limit exceeded");
    }

    private EventAckResult acknowledgeV2Locked(String owner, EventAckRequest request) {
        EventV2Contract.validateAckRequest(request);
        long acknowledged = cursorCodec.decode(
                owner,
                request.sessionId,
                request.resumeCursor,
                false);
        if (acknowledged != request.acknowledgedSequence) {
            throw new IllegalArgumentException("CB_EVENT_V2: ACK cursor mismatch");
        }
        long latest = registry.latestEventSequenceOwned(owner, request.sessionId);
        DurableEventCursorRepository.AckOutcome outcome =
                durableEventCursors.acknowledgeSessionOwned(
                        request.subscriptionId,
                        owner,
                        request.sessionId,
                        acknowledged,
                        latest);
        DurableEventCursorRepository.Snapshot snapshot =
                durableEventCursors.findSessionOwned(
                        request.subscriptionId,
                        owner,
                        request.sessionId);
        long resultSequence = snapshot == null
                ? acknowledged : snapshot.getAcknowledgedSequence();
        EventAckResult result = new EventAckResult();
        result.outcome = ackOutcome(outcome);
        result.subscriptionId = request.subscriptionId;
        result.acknowledgedSequence = resultSequence;
        result.resumeCursor = cursorCodec.encode(owner, request.sessionId, resultSequence);
        EventV2Contract.validateAckResult(result);
        return result;
    }

    private EventPageV2 pageV2(String owner, String sessionId, String cursor, int limit) {
        long afterSequence = cursorCodec.decode(owner, sessionId, cursor, true);
        String v1Cursor = afterSequence == 0 ? "" : "e:" + afterSequence;
        EventPage legacy = registry.eventsOwned(owner, sessionId, v1Cursor, limit);
        EventPageV2 page = new EventPageV2();
        page.sessionId = sessionId;
        page.requestCursor = cursor;
        page.afterSequence = afterSequence;
        page.events = legacy.events;
        page.resumeSequence = legacy.nextSequence;
        page.resumeCursor = cursorCodec.encode(owner, sessionId, page.resumeSequence);
        page.hasMore = legacy.hasMore;
        page.redactionApplied = legacy.redactionApplied;
        page.generatedAtEpochMs = legacy.generatedAtEpochMs;
        EventV2Contract.validatePage(page);
        return page;
    }

    private EventSubscriptionHandle handleV2(
            String owner,
            String sessionId,
            DurableEventCursorRepository.Snapshot snapshot) {
        EventSubscriptionHandle handle = new EventSubscriptionHandle();
        handle.subscriptionId = snapshot.getCursorId();
        handle.clientSubscriptionId = snapshot.getClientSubscriptionId();
        handle.sessionId = sessionId;
        handle.resumeCursor = cursorCodec.encode(
                owner,
                sessionId,
                snapshot.getAcknowledgedSequence());
        handle.acknowledgedSequence = snapshot.getAcknowledgedSequence();
        handle.state = subscriptionState(snapshot.getState());
        handle.updatedAtEpochMs = snapshot.getUpdatedAtWallMs();
        EventV2Contract.validateSubscriptionHandle(handle);
        return handle;
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

    private boolean unregisterV2Locked(
            String owner,
            EventSubscriptionHandle handle,
            IBinder binder) {
        DurableEventCursorRepository.Snapshot snapshot =
                durableEventCursors.findSessionOwned(
                        handle.subscriptionId,
                        owner,
                        handle.sessionId);
        if (snapshot == null
                || !snapshot.getClientSubscriptionId().equals(handle.clientSubscriptionId)) {
            return false;
        }
        List<CallbackRecordV2> records = callbacksV2BySession.get(handle.sessionId);
        if (records == null) {
            return true;
        }
        Iterator<CallbackRecordV2> iterator = records.iterator();
        while (iterator.hasNext()) {
            CallbackRecordV2 record = iterator.next();
            if (record.owner.equals(owner)
                    && record.subscriptionId.equals(handle.subscriptionId)
                    && record.callback.asBinder() == binder) {
                iterator.remove();
                safeUnlink(record);
            }
        }
        if (records.isEmpty()) {
            callbacksV2BySession.remove(handle.sessionId);
        }
        return true;
    }

    private void removeSubscriptionCallbacksV2(
            String owner,
            EventSubscriptionHandle handle) {
        List<CallbackRecordV2> records = callbacksV2BySession.get(handle.sessionId);
        if (records == null) {
            return;
        }
        Iterator<CallbackRecordV2> iterator = records.iterator();
        while (iterator.hasNext()) {
            CallbackRecordV2 record = iterator.next();
            if (record.owner.equals(owner)
                    && record.subscriptionId.equals(handle.subscriptionId)) {
                iterator.remove();
                safeUnlink(record);
            }
        }
        if (records.isEmpty()) {
            callbacksV2BySession.remove(handle.sessionId);
        }
    }

    private void dispatchOwned(String owner, String sessionId, RuntimeEvent event) {
        List<CallbackRecord> records = callbacksBySession.get(sessionId);
        if (records != null) {
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

        List<CallbackRecordV2> recordsV2 = callbacksV2BySession.get(sessionId);
        if (recordsV2 == null) {
            return;
        }
        Iterator<CallbackRecordV2> iteratorV2 = recordsV2.iterator();
        while (iteratorV2.hasNext()) {
            CallbackRecordV2 record = iteratorV2.next();
            if (!record.owner.equals(owner)) {
                continue;
            }
            String resumeCursor = cursorCodec.encode(owner, sessionId, event.sequence);
            try {
                record.callback.onEvent(event, resumeCursor);
                record.resumeCursor = resumeCursor;
            } catch (RemoteException failure) {
                iteratorV2.remove();
                safeUnlink(record);
            }
        }
        if (recordsV2.isEmpty()) {
            callbacksV2BySession.remove(sessionId);
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

    private void removeDead(CallbackRecordV2 dead) {
        synchronized (lock) {
            List<CallbackRecordV2> records = callbacksV2BySession.get(dead.sessionId);
            if (records == null) {
                return;
            }
            records.remove(dead);
            if (records.isEmpty()) {
                callbacksV2BySession.remove(dead.sessionId);
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

    private int callbackCountV2() {
        int count = 0;
        for (List<CallbackRecordV2> records : callbacksV2BySession.values()) {
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

    private static void safeUnlink(CallbackRecordV2 record) {
        if (record.deathRecipient == null) {
            return;
        }
        try {
            record.callback.asBinder().unlinkToDeath(record.deathRecipient, 0);
        } catch (NoSuchElementException ignored) {
            // Binder already removed the recipient.
        }
    }

    private static int subscriptionState(String state) {
        if (DurableEventCursorRepository.STATE_ACTIVE.equals(state)) {
            return ICentralBrainSessionEventsV2.SUBSCRIPTION_STATE_ACTIVE;
        }
        if (DurableEventCursorRepository.STATE_RESYNC_REQUIRED.equals(state)) {
            return ICentralBrainSessionEventsV2.SUBSCRIPTION_STATE_RESYNC_REQUIRED;
        }
        if (DurableEventCursorRepository.STATE_CANCELLED.equals(state)) {
            return ICentralBrainSessionEventsV2.SUBSCRIPTION_STATE_CANCELLED;
        }
        throw new IllegalStateException("CB_EVENT_V2: unknown durable subscription state");
    }

    private static int ackOutcome(DurableEventCursorRepository.AckOutcome outcome) {
        switch (outcome) {
            case APPLIED:
                return ICentralBrainSessionEventsV2.ACK_APPLIED;
            case REPLAYED:
                return ICentralBrainSessionEventsV2.ACK_REPLAYED;
            case NOT_FOUND:
                return ICentralBrainSessionEventsV2.ACK_NOT_FOUND;
            case NOT_ACTIVE:
                return ICentralBrainSessionEventsV2.ACK_NOT_ACTIVE;
            case RESYNC_REQUIRED:
                return ICentralBrainSessionEventsV2.ACK_RESYNC_REQUIRED;
            case REGRESSION:
                return ICentralBrainSessionEventsV2.ACK_STALE;
            case FUTURE_SEQUENCE:
                return ICentralBrainSessionEventsV2.ACK_FUTURE;
            case SOURCE_REGRESSION:
                return ICentralBrainSessionEventsV2.ACK_SOURCE_REGRESSION;
            default:
                throw new IllegalStateException("CB_EVENT_V2: unknown ACK outcome");
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

    private static final class CallbackRecordV2 {
        private final String owner;
        private final String sessionId;
        private final String subscriptionId;
        private final ICentralBrainSessionEventCallbackV2 callback;
        private String resumeCursor;
        private IBinder.DeathRecipient deathRecipient;

        private CallbackRecordV2(
                String owner,
                String sessionId,
                String subscriptionId,
                ICentralBrainSessionEventCallbackV2 callback,
                String resumeCursor) {
            this.owner = owner;
            this.sessionId = sessionId;
            this.subscriptionId = subscriptionId;
            this.callback = callback;
            this.resumeCursor = resumeCursor;
        }
    }

}
