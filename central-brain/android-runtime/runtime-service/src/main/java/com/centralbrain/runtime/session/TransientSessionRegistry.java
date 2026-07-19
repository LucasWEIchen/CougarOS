package com.centralbrain.runtime.session;

import com.centralbrain.runtime.persistence.DurableDigest;
import com.centralbrain.sdk.event.EventContract;
import com.centralbrain.sdk.event.EventPage;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionContract;
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
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Process-local, owner-scoped Stage 2 session registry. It never retains raw utterances. */
public final class TransientSessionRegistry implements SessionRegistry {
    private final int maxSessions;
    private final int maxEventsPerSession;
    private final LongSupplier epochMs;
    private final Supplier<String> uuidSupplier;
    private final Map<String, Record> sessions = new LinkedHashMap<>();
    private final Map<String, Record> requests = new LinkedHashMap<>();

    public static TransientSessionRegistry createForContractTest(
            int maxSessions,
            int maxEventsPerSession,
            LongSupplier epochMs,
            Supplier<String> uuidSupplier) {
        return new TransientSessionRegistry(
                maxSessions,
                maxEventsPerSession,
                epochMs,
                uuidSupplier);
    }

    private TransientSessionRegistry(
            int maxSessions,
            int maxEventsPerSession,
            LongSupplier epochMs,
            Supplier<String> uuidSupplier) {
        if (maxSessions < 1 || maxEventsPerSession < 2) {
            throw new IllegalArgumentException("session registry limits are invalid");
        }
        this.maxSessions = maxSessions;
        this.maxEventsPerSession = maxEventsPerSession;
        this.epochMs = Objects.requireNonNull(epochMs, "epochMs");
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier");
    }

    @Override
    public synchronized SessionHandle openOwned(String owner, SessionRequest request) {
        requireOwner(owner);
        long now = now();
        SessionContract.validateRequest(request, now);
        String requestDigest = requestDigest(request);
        String requestKey = owner + ':' + request.requestId;
        Record existing = requests.get(requestKey);
        if (existing != null) {
            if (!existing.requestDigest.equals(requestDigest)) {
                throw new IllegalArgumentException(
                        "CB_SESSION_RUNTIME: requestId idempotency conflict");
            }
            return copy(existing.handle);
        }

        ensureCapacity();
        String sessionId = nextUuid();
        SessionHandle handle = new SessionHandle();
        handle.sessionId = sessionId;
        handle.acceptedAtEpochMs = now;
        handle.expiresAtEpochMs = request.deadlineEpochMs;

        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.sessionId = sessionId;
        snapshot.requestId = request.requestId;
        snapshot.scenarioId = request.scenarioId;
        snapshot.state = ICentralBrainSessionRuntime.SESSION_STATE_CREATED;
        snapshot.activePlanRevision = 0;
        snapshot.lastEventSequence = 1;
        snapshot.createdAtEpochMs = now;
        snapshot.updatedAtEpochMs = now;
        snapshot.deadlineEpochMs = request.deadlineEpochMs;
        snapshot.summary = "Scenario accepted; execution is not enabled";

        Record created = new Record(owner, requestKey, requestDigest, handle, snapshot);
        created.events.add(event(
                created,
                "ScenarioRequested",
                EventContract.SOURCE_SCENARIO,
                requestDigest,
                now));
        sessions.put(sessionId, created);
        requests.put(requestKey, created);
        return copy(handle);
    }

    @Override
    public synchronized SessionSnapshot findOwned(String owner, SessionHandle handle) {
        requireOwner(owner);
        SessionContract.validateHandle(handle);
        Record record = owned(owner, handle.sessionId);
        return record == null ? null : copy(record.snapshot);
    }

    @Override
    public synchronized SessionPage listOwned(String owner, SessionQuery query) {
        requireOwner(owner);
        SessionContract.validateQuery(query);
        int offset = parseSessionCursor(query.cursor);
        List<SessionSnapshot> matches = new ArrayList<>();
        for (Record record : sessions.values()) {
            if (!record.owner.equals(owner)
                    || (!query.includeTerminal
                            && SessionContract.isTerminalState(record.snapshot.state))
                    || (query.stateFilter != ICentralBrainSessionRuntime.SESSION_STATE_ANY
                            && query.stateFilter != record.snapshot.state)) {
                continue;
            }
            matches.add(copy(record.snapshot));
        }
        if (offset > matches.size()) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: future session cursor");
        }
        int end = Math.min(matches.size(), offset + query.pageSize);
        SessionPage page = new SessionPage();
        page.sessions = matches.subList(offset, end).toArray(new SessionSnapshot[0]);
        page.hasMore = end < matches.size();
        page.nextCursor = page.hasMore ? "s:" + end : "";
        page.generatedAtEpochMs = now();
        return page;
    }

    @Override
    public synchronized CancelResult cancelOwned(
            String owner,
            SessionHandle handle,
            int reasonCode) {
        requireOwner(owner);
        SessionContract.validateHandle(handle);
        if (!SessionContract.isCancelReason(reasonCode)) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: unknown cancel reason");
        }
        Record record = owned(owner, handle.sessionId);
        if (record == null || SessionContract.isTerminalState(record.snapshot.state)) {
            return new CancelResult(false, null);
        }
        if (record.events.size() >= maxEventsPerSession) {
            throw new IllegalStateException("CB_SESSION_RUNTIME: event capacity exhausted");
        }
        long now = now();
        record.snapshot.state = ICentralBrainSessionRuntime.SESSION_STATE_CANCELLED;
        record.snapshot.updatedAtEpochMs = now;
        record.snapshot.summary = "Session cancelled";
        RuntimeEvent event = event(
                record,
                "SessionStateChanged",
                EventContract.SOURCE_RUNTIME,
                DurableDigest.sha256(
                        "central-brain-session-cancel-v1",
                        record.handle.sessionId,
                        Integer.toString(reasonCode)),
                now);
        record.events.add(event);
        record.snapshot.lastEventSequence = event.sequence;
        return new CancelResult(true, copy(event));
    }

    @Override
    public synchronized EventPage eventsOwned(
            String owner,
            String sessionId,
            String cursor,
            int limit) {
        requireOwner(owner);
        if (cursor == null || limit < 1 || limit > EventContract.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: invalid event page request");
        }
        Record record = owned(owner, sessionId);
        if (record == null) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: session not found");
        }
        long afterSequence = parseEventCursor(cursor);
        long latest = record.events.isEmpty()
                ? 0 : record.events.get(record.events.size() - 1).sequence;
        if (afterSequence > latest) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: future event cursor");
        }
        List<RuntimeEvent> selected = new ArrayList<>();
        for (RuntimeEvent event : record.events) {
            if (event.sequence > afterSequence && selected.size() < limit) {
                selected.add(copy(event));
            }
        }
        long nextSequence = selected.isEmpty()
                ? afterSequence : selected.get(selected.size() - 1).sequence;
        EventPage page = new EventPage();
        page.sessionId = sessionId;
        page.requestCursor = cursor;
        page.afterSequence = afterSequence;
        page.events = selected.toArray(new RuntimeEvent[0]);
        page.nextSequence = nextSequence;
        page.hasMore = nextSequence < latest;
        page.nextCursor = page.hasMore ? "e:" + nextSequence : "";
        page.redactionApplied = false;
        page.generatedAtEpochMs = Math.max(
                now(),
                record.events.isEmpty()
                        ? 1 : record.events.get(record.events.size() - 1).occurredAtEpochMs);
        return page;
    }

    @Override
    public synchronized long latestEventSequenceOwned(String owner, String sessionId) {
        requireOwner(owner);
        Record record = owned(owner, sessionId);
        if (record == null) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: session not found");
        }
        return record.snapshot.lastEventSequence;
    }

    @Override
    public synchronized int size() {
        return sessions.size();
    }

    private RuntimeEvent event(
            Record record,
            String type,
            int source,
            String payloadDigest,
            long now) {
        RuntimeEvent previous = record.events.isEmpty()
                ? null : record.events.get(record.events.size() - 1);
        RuntimeEvent event = new RuntimeEvent();
        event.eventId = nextUuid();
        event.sequence = previous == null ? 1 : previous.sequence + 1;
        event.sessionId = record.handle.sessionId;
        event.parentEventId = previous == null ? "" : previous.eventId;
        event.parentSequence = previous == null ? 0 : previous.sequence;
        event.type = type;
        event.source = source;
        event.occurredAtEpochMs = now;
        event.privacyClass = EventContract.PRIVACY_INTERNAL;
        event.payloadDigest = payloadDigest;
        event.eventDigest = DurableDigest.sha256(
                "central-brain-session-event-v1",
                event.eventId,
                Long.toString(event.sequence),
                event.sessionId,
                event.parentEventId,
                type,
                payloadDigest);
        event.payloadKind = EventContract.PAYLOAD_NONE;
        return event;
    }

    private void ensureCapacity() {
        if (sessions.size() < maxSessions) {
            return;
        }
        Iterator<Map.Entry<String, Record>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Record candidate = iterator.next().getValue();
            if (SessionContract.isTerminalState(candidate.snapshot.state)) {
                iterator.remove();
                requests.remove(candidate.requestKey);
                return;
            }
        }
        throw new IllegalStateException("CB_SESSION_RUNTIME: active session capacity exhausted");
    }

    private Record owned(String owner, String sessionId) {
        Record record = sessions.get(sessionId);
        return record != null && record.owner.equals(owner) ? record : null;
    }

    private long now() {
        long value = epochMs.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("epoch clock must be positive");
        }
        return value;
    }

    private String nextUuid() {
        String value = uuidSupplier.get();
        if (value == null || !UUID.fromString(value).toString().equals(value)) {
            throw new IllegalStateException("canonical lowercase UUID supplier is required");
        }
        return value;
    }

    private static String requestDigest(SessionRequest request) {
        return DurableDigest.sha256(
                "central-brain-session-request-v1",
                request.requestId,
                request.scenarioId,
                request.utterance,
                Integer.toString(request.source),
                Integer.toString(request.seatZone),
                request.locale,
                Long.toString(request.deadlineEpochMs),
                Integer.toString(request.clientContextVersion));
    }

    private static int parseSessionCursor(String cursor) {
        if (cursor.isEmpty()) {
            return 0;
        }
        if (!cursor.startsWith("s:")) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: invalid session cursor");
        }
        try {
            int value = Integer.parseInt(cursor.substring(2));
            if (value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: invalid session cursor");
        }
    }

    private static long parseEventCursor(String cursor) {
        if (cursor.isEmpty()) {
            return 0;
        }
        if (!cursor.startsWith("e:")) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: invalid event cursor");
        }
        try {
            long value = Long.parseLong(cursor.substring(2));
            if (value < 1) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: invalid event cursor");
        }
    }

    private static void requireOwner(String owner) {
        if (owner == null || !owner.matches("[0-9a-f]{64}")) {
            throw new SecurityException("durable owner fingerprint is required");
        }
    }

    private static SessionHandle copy(SessionHandle original) {
        SessionHandle copy = new SessionHandle();
        copy.schemaVersion = original.schemaVersion;
        copy.sessionId = original.sessionId;
        copy.acceptedAtEpochMs = original.acceptedAtEpochMs;
        copy.expiresAtEpochMs = original.expiresAtEpochMs;
        return copy;
    }

    private static SessionSnapshot copy(SessionSnapshot original) {
        SessionSnapshot copy = new SessionSnapshot();
        copy.schemaVersion = original.schemaVersion;
        copy.sessionId = original.sessionId;
        copy.requestId = original.requestId;
        copy.scenarioId = original.scenarioId;
        copy.state = original.state;
        copy.activePlanRevision = original.activePlanRevision;
        copy.lastEventSequence = original.lastEventSequence;
        copy.createdAtEpochMs = original.createdAtEpochMs;
        copy.updatedAtEpochMs = original.updatedAtEpochMs;
        copy.deadlineEpochMs = original.deadlineEpochMs;
        copy.summary = original.summary;
        return copy;
    }

    private static RuntimeEvent copy(RuntimeEvent original) {
        RuntimeEvent copy = new RuntimeEvent();
        copy.schemaVersion = original.schemaVersion;
        copy.eventId = original.eventId;
        copy.sequence = original.sequence;
        copy.sessionId = original.sessionId;
        copy.parentEventId = original.parentEventId;
        copy.parentSequence = original.parentSequence;
        copy.type = original.type;
        copy.source = original.source;
        copy.occurredAtEpochMs = original.occurredAtEpochMs;
        copy.privacyClass = original.privacyClass;
        copy.payloadDigest = original.payloadDigest;
        copy.eventDigest = original.eventDigest;
        copy.payloadKind = original.payloadKind;
        return copy;
    }

    private static final class Record {
        private final String owner;
        private final String requestKey;
        private final String requestDigest;
        private final SessionHandle handle;
        private final SessionSnapshot snapshot;
        private final List<RuntimeEvent> events = new ArrayList<>();

        private Record(
                String owner,
                String requestKey,
                String requestDigest,
                SessionHandle handle,
                SessionSnapshot snapshot) {
            this.owner = owner;
            this.requestKey = requestKey;
            this.requestDigest = requestDigest;
            this.handle = handle;
            this.snapshot = snapshot;
        }
    }
}
