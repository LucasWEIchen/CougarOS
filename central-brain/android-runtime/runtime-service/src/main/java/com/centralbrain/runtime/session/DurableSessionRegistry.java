package com.centralbrain.runtime.session;

import com.centralbrain.runtime.persistence.CentralBrainDatabase;
import com.centralbrain.runtime.persistence.DurableDigest;
import com.centralbrain.runtime.persistence.RuntimeEventEntity;
import com.centralbrain.runtime.persistence.RuntimeStateDao;
import com.centralbrain.runtime.persistence.SessionEntity;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Room-backed Session/Event V1 owner. Raw utterances and Binder objects are never persisted. */
public final class DurableSessionRegistry implements SessionRegistry {
    public static final int DEFAULT_MAX_SESSIONS = 64;
    public static final int DEFAULT_MAX_EVENTS_PER_SESSION = 8;
    public static final int MAX_CANONICAL_PAYLOAD_BYTES = 8192;

    private final CentralBrainDatabase database;
    private final RuntimeStateDao dao;
    private final int maxSessions;
    private final int maxEventsPerSession;
    private final LongSupplier epochMs;
    private final Supplier<String> uuidSupplier;

    public static DurableSessionRegistry create(CentralBrainDatabase database) {
        return new DurableSessionRegistry(
                database,
                DEFAULT_MAX_SESSIONS,
                DEFAULT_MAX_EVENTS_PER_SESSION,
                System::currentTimeMillis,
                () -> UUID.randomUUID().toString());
    }

    public static DurableSessionRegistry createForContractTest(
            CentralBrainDatabase database,
            int maxSessions,
            int maxEventsPerSession,
            LongSupplier epochMs,
            Supplier<String> uuidSupplier) {
        return new DurableSessionRegistry(
                database,
                maxSessions,
                maxEventsPerSession,
                epochMs,
                uuidSupplier);
    }

    private DurableSessionRegistry(
            CentralBrainDatabase database,
            int maxSessions,
            int maxEventsPerSession,
            LongSupplier epochMs,
            Supplier<String> uuidSupplier) {
        if (maxSessions < 1 || maxEventsPerSession < 2) {
            throw new IllegalArgumentException("durable session limits are invalid");
        }
        this.database = Objects.requireNonNull(database, "database");
        this.dao = database.runtimeStateDao();
        this.maxSessions = maxSessions;
        this.maxEventsPerSession = maxEventsPerSession;
        this.epochMs = Objects.requireNonNull(epochMs, "epochMs");
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier");
    }

    @Override
    public SessionHandle openOwned(String owner, SessionRequest request) {
        requireOwner(owner);
        long now = now();
        SessionContract.validateRequest(request, now);
        String requestDigest = requestDigest(request);
        return database.runInTransaction(() -> {
            SessionEntity existing = dao.findSessionByOwnerAndRequest(owner, request.requestId);
            if (existing != null) {
                if (!existing.requestDigest.equals(requestDigest)) {
                    throw new IllegalArgumentException(
                            "CB_SESSION_RUNTIME: requestId idempotency conflict");
                }
                return handle(existing);
            }
            ensureCapacity();

            SessionEntity session = new SessionEntity();
            session.sessionId = nextUuid();
            session.ownerFingerprint = owner;
            session.clientRequestId = request.requestId;
            session.requestDigest = requestDigest;
            session.scenarioId = request.scenarioId;
            session.state = ICentralBrainSessionRuntime.SESSION_STATE_CREATED;
            session.activePlanRevision = 0;
            session.lastEventSequence = 1;
            session.createdAtWallMs = now;
            session.updatedAtWallMs = now;
            session.deadlineWallMs = request.deadlineEpochMs;
            session.summary = "Scenario accepted; execution is not enabled";
            session.revision = 1;

            RuntimeEvent event = event(
                    session,
                    "ScenarioRequested",
                    EventContract.SOURCE_SCENARIO,
                    requestDigest,
                    now);
            dao.insertSession(session);
            dao.insertRuntimeEvent(entity(event));
            return handle(session);
        });
    }

    @Override
    public SessionSnapshot findOwned(String owner, SessionHandle handle) {
        requireOwner(owner);
        SessionContract.validateHandle(handle);
        SessionEntity session = dao.findSessionOwned(handle.sessionId, owner);
        return session == null ? null : snapshot(session);
    }

    @Override
    public SessionPage listOwned(String owner, SessionQuery query) {
        requireOwner(owner);
        SessionContract.validateQuery(query);
        int offset = parseSessionCursor(query.cursor);
        int count = dao.countSessionsOwned(owner, query.stateFilter, query.includeTerminal);
        if (offset > count) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: future session cursor");
        }
        List<SessionEntity> rows = dao.listSessionsOwned(
                owner,
                query.stateFilter,
                query.includeTerminal,
                query.pageSize,
                offset);
        SessionSnapshot[] snapshots = new SessionSnapshot[rows.size()];
        for (int index = 0; index < rows.size(); index++) {
            snapshots[index] = snapshot(rows.get(index));
        }
        int end = offset + rows.size();
        SessionPage page = new SessionPage();
        page.sessions = snapshots;
        page.hasMore = end < count;
        page.nextCursor = page.hasMore ? "s:" + end : "";
        page.generatedAtEpochMs = now();
        return page;
    }

    @Override
    public CancelResult cancelOwned(String owner, SessionHandle handle, int reasonCode) {
        requireOwner(owner);
        SessionContract.validateHandle(handle);
        if (!SessionContract.isCancelReason(reasonCode)) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: unknown cancel reason");
        }
        return database.runInTransaction(() -> {
            SessionEntity session = dao.findSessionOwned(handle.sessionId, owner);
            if (session == null || SessionContract.isTerminalState(session.state)) {
                return new CancelResult(false, null);
            }
            if (session.lastEventSequence >= maxEventsPerSession) {
                throw new IllegalStateException("CB_SESSION_RUNTIME: event capacity exhausted");
            }
            long now = now();
            session.state = ICentralBrainSessionRuntime.SESSION_STATE_CANCELLED;
            session.updatedAtWallMs = now;
            session.summary = "Session cancelled";
            session.lastEventSequence++;
            session.revision++;
            RuntimeEvent event = event(
                    session,
                    "SessionStateChanged",
                    EventContract.SOURCE_RUNTIME,
                    DurableDigest.sha256(
                            "central-brain-session-cancel-v1",
                            session.sessionId,
                            Integer.toString(reasonCode)),
                    now);
            if (dao.updateSession(session) != 1) {
                throw new IllegalStateException("CB_SESSION_RUNTIME: session update conflict");
            }
            dao.insertRuntimeEvent(entity(event));
            return new CancelResult(true, event);
        });
    }

    @Override
    public EventPage eventsOwned(String owner, String sessionId, String cursor, int limit) {
        requireOwner(owner);
        if (cursor == null || limit < 1 || limit > EventContract.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: invalid event page request");
        }
        SessionEntity session = dao.findSessionOwned(sessionId, owner);
        if (session == null) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: session not found");
        }
        long afterSequence = parseEventCursor(cursor);
        if (afterSequence > session.lastEventSequence) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: future event cursor");
        }
        List<RuntimeEventEntity> rows = dao.listRuntimeEvents(sessionId, afterSequence, limit);
        RuntimeEvent[] events = new RuntimeEvent[rows.size()];
        for (int index = 0; index < rows.size(); index++) {
            events[index] = event(rows.get(index));
        }
        long nextSequence = rows.isEmpty()
                ? afterSequence : rows.get(rows.size() - 1).sequence;
        EventPage page = new EventPage();
        page.sessionId = sessionId;
        page.requestCursor = cursor;
        page.afterSequence = afterSequence;
        page.events = events;
        page.nextSequence = nextSequence;
        page.hasMore = nextSequence < session.lastEventSequence;
        page.nextCursor = page.hasMore ? "e:" + nextSequence : "";
        page.redactionApplied = false;
        page.generatedAtEpochMs = Math.max(now(), session.updatedAtWallMs);
        return page;
    }

    @Override
    public long latestEventSequenceOwned(String owner, String sessionId) {
        requireOwner(owner);
        SessionEntity session = dao.findSessionOwned(sessionId, owner);
        if (session == null) {
            throw new IllegalArgumentException("CB_SESSION_RUNTIME: session not found");
        }
        return session.lastEventSequence;
    }

    @Override
    public int size() {
        return dao.countSessions();
    }

    private void ensureCapacity() {
        if (dao.countSessions() < maxSessions) {
            return;
        }
        SessionEntity terminal = dao.findOldestTerminalSession();
        if (terminal == null || dao.deleteTerminalSession(terminal.sessionId) != 1) {
            throw new IllegalStateException("CB_SESSION_RUNTIME: active session capacity exhausted");
        }
    }

    private RuntimeEvent event(
            SessionEntity session,
            String type,
            int source,
            String payloadDigest,
            long now) {
        long sequence = session.lastEventSequence;
        String parentId = "";
        if (sequence > 1) {
            List<RuntimeEventEntity> previous = dao.listRuntimeEvents(
                    session.sessionId,
                    sequence - 2,
                    1);
            if (previous.size() != 1 || previous.get(0).sequence != sequence - 1) {
                throw new IllegalStateException("CB_SESSION_RUNTIME: event parent missing");
            }
            parentId = previous.get(0).eventId;
        }
        RuntimeEvent event = new RuntimeEvent();
        event.eventId = nextUuid();
        event.sequence = sequence;
        event.sessionId = session.sessionId;
        event.parentEventId = parentId;
        event.parentSequence = sequence == 1 ? 0 : sequence - 1;
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

    private static SessionHandle handle(SessionEntity session) {
        SessionHandle handle = new SessionHandle();
        handle.sessionId = session.sessionId;
        handle.acceptedAtEpochMs = session.createdAtWallMs;
        handle.expiresAtEpochMs = session.deadlineWallMs;
        return handle;
    }

    private static SessionSnapshot snapshot(SessionEntity session) {
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.sessionId = session.sessionId;
        snapshot.requestId = session.clientRequestId;
        snapshot.scenarioId = session.scenarioId;
        snapshot.state = session.state;
        snapshot.activePlanRevision = session.activePlanRevision;
        snapshot.lastEventSequence = session.lastEventSequence;
        snapshot.createdAtEpochMs = session.createdAtWallMs;
        snapshot.updatedAtEpochMs = session.updatedAtWallMs;
        snapshot.deadlineEpochMs = session.deadlineWallMs;
        snapshot.summary = session.summary;
        SessionContract.validateSnapshot(snapshot);
        return snapshot;
    }

    private static RuntimeEventEntity entity(RuntimeEvent event) {
        if (event.payloadKind != EventContract.PAYLOAD_NONE
                || event.action != null
                || event.observation != null
                || event.message != null) {
            throw new IllegalArgumentException(
                    "CB_SESSION_RUNTIME: typed payload encoder is not published");
        }
        RuntimeEventEntity entity = new RuntimeEventEntity();
        entity.eventId = event.eventId;
        entity.sessionId = event.sessionId;
        entity.sequence = event.sequence;
        entity.parentEventId = event.parentEventId;
        entity.parentSequence = event.parentSequence;
        entity.eventType = event.type;
        entity.source = event.source;
        entity.occurredAtWallMs = event.occurredAtEpochMs;
        entity.privacyClass = event.privacyClass;
        entity.payloadDigest = event.payloadDigest;
        entity.eventDigest = event.eventDigest;
        entity.payloadKind = event.payloadKind;
        entity.payloadCanonical = "";
        return entity;
    }

    private static RuntimeEvent event(RuntimeEventEntity entity) {
        if (entity.payloadCanonical.getBytes(StandardCharsets.UTF_8).length
                > MAX_CANONICAL_PAYLOAD_BYTES) {
            throw new IllegalStateException("CB_SESSION_RUNTIME: durable payload exceeds limit");
        }
        RuntimeEvent event = new RuntimeEvent();
        event.eventId = entity.eventId;
        event.sessionId = entity.sessionId;
        event.sequence = entity.sequence;
        event.parentEventId = entity.parentEventId;
        event.parentSequence = entity.parentSequence;
        event.type = entity.eventType;
        event.source = entity.source;
        event.occurredAtEpochMs = entity.occurredAtWallMs;
        event.privacyClass = entity.privacyClass;
        event.payloadDigest = entity.payloadDigest;
        event.eventDigest = entity.eventDigest;
        event.payloadKind = entity.payloadKind;
        EventContract.validateEvent(event);
        return event;
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
}
