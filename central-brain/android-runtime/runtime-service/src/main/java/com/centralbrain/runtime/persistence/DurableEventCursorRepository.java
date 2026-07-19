package com.centralbrain.runtime.persistence;

import com.centralbrain.runtime.events.BoundedEventRuntime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Durable Event subscription metadata owner. It does not publish or dispatch events. */
public final class DurableEventCursorRepository {
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_RESYNC_REQUIRED = "RESYNC_REQUIRED";
    public static final String STATE_CANCELLED = "CANCELLED";

    public static final String AUDIT_REGISTERED = "EVENT_SUBSCRIPTION_REGISTERED";
    public static final String AUDIT_ACKNOWLEDGED = "EVENT_CURSOR_ACKNOWLEDGED";
    public static final String AUDIT_OVERFLOWED = "EVENT_CURSOR_OVERFLOWED";
    public static final String AUDIT_RESYNCED = "EVENT_CURSOR_RESYNCED";
    public static final String AUDIT_CANCELLED = "EVENT_SUBSCRIPTION_CANCELLED";

    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_QUEUE_CAPACITY = 256;

    private final CentralBrainDatabase database;
    private final RuntimeStateDao dao;
    private final int maxActiveRecords;
    private final int maxActiveRecordsPerOwner;
    private final int maxCancelledRecords;
    private final LongSupplier wallClockMs;
    private final Supplier<String> uniqueIdSource;

    public DurableEventCursorRepository(
            CentralBrainDatabase database,
            int maxActiveRecords,
            int maxActiveRecordsPerOwner,
            int maxCancelledRecords,
            LongSupplier wallClockMs,
            Supplier<String> uniqueIdSource) {
        this.database = Objects.requireNonNull(database, "database");
        this.dao = database.runtimeStateDao();
        if (maxActiveRecords < 1
                || maxActiveRecordsPerOwner < 1
                || maxActiveRecordsPerOwner > maxActiveRecords
                || maxCancelledRecords < 1) {
            throw new IllegalArgumentException("Event cursor repository bounds are invalid");
        }
        this.maxActiveRecords = maxActiveRecords;
        this.maxActiveRecordsPerOwner = maxActiveRecordsPerOwner;
        this.maxCancelledRecords = maxCancelledRecords;
        this.wallClockMs = Objects.requireNonNull(wallClockMs, "wallClockMs");
        this.uniqueIdSource = Objects.requireNonNull(uniqueIdSource, "uniqueIdSource");
    }

    public static DurableEventCursorRepository create(
            CentralBrainDatabase database,
            int maxActiveRecords,
            int maxActiveRecordsPerOwner,
            int maxCancelledRecords) {
        return new DurableEventCursorRepository(
                database,
                maxActiveRecords,
                maxActiveRecordsPerOwner,
                maxCancelledRecords,
                System::currentTimeMillis,
                () -> UUID.randomUUID().toString());
    }

    public RegisterResult register(
            String ownerFingerprint,
            String clientSubscriptionId,
            List<String> topicIds,
            long requestedAfterSequence,
            int queueCapacity,
            long knownLatestSequence) {
        requireOwner(ownerFingerprint);
        requireId(clientSubscriptionId, "clientSubscriptionId");
        String topicsCanonical = canonicalTopics(topicIds);
        requireSequence(requestedAfterSequence, "requestedAfterSequence");
        requireSequence(knownLatestSequence, "knownLatestSequence");
        if (queueCapacity < 1 || queueCapacity > MAX_QUEUE_CAPACITY) {
            throw new IllegalArgumentException("queueCapacity is out of range");
        }
        if (requestedAfterSequence > knownLatestSequence) {
            return new RegisterResult(RegisterOutcome.FUTURE_CURSOR, null);
        }

        return runTransaction(() -> {
            EventCursorEntity existing = dao.findEventCursorByOwnerAndClient(
                    ownerFingerprint,
                    clientSubscriptionId);
            if (existing != null) {
                RegisterOutcome outcome = !matchesRegistration(
                        existing,
                        topicsCanonical,
                        requestedAfterSequence,
                        queueCapacity)
                        ? RegisterOutcome.CONFLICT
                        : knownLatestSequence < existing.acknowledgedSequence
                                ? RegisterOutcome.SOURCE_REGRESSION
                                : RegisterOutcome.REPLAYED;
                return new RegisterResult(outcome, Snapshot.from(existing));
            }
            if (dao.countActiveEventCursors() >= maxActiveRecords) {
                return new RegisterResult(RegisterOutcome.GLOBAL_LIMIT, null);
            }
            if (dao.countActiveEventCursorsByOwner(ownerFingerprint)
                    >= maxActiveRecordsPerOwner) {
                return new RegisterResult(RegisterOutcome.OWNER_LIMIT, null);
            }

            long now = now();
            EventCursorEntity entity = new EventCursorEntity();
            entity.cursorId = generatedId("event-cursor-");
            entity.ownerFingerprint = ownerFingerprint;
            entity.clientSubscriptionId = clientSubscriptionId;
            entity.topicsCanonical = topicsCanonical;
            entity.requestedAfterSequence = requestedAfterSequence;
            entity.acknowledgedSequence = requestedAfterSequence;
            entity.queueCapacity = queueCapacity;
            entity.state = STATE_ACTIVE;
            entity.overflowFirstSequence = 0;
            entity.overflowLastSequence = 0;
            entity.overflowCount = 0;
            entity.createdAtWallMs = now;
            entity.updatedAtWallMs = now;
            dao.insertEventCursor(entity);
            insertAudit(entity, AUDIT_REGISTERED, RegisterOutcome.CREATED.name(), now);
            return new RegisterResult(RegisterOutcome.CREATED, Snapshot.from(entity));
        });
    }

    /** Registers or reopens an Event V2 cursor bound to one owned Session. */
    public RegisterResult registerSession(
            String ownerFingerprint,
            String clientSubscriptionId,
            String sessionId,
            long requestedAfterSequence,
            int queueCapacity,
            long knownLatestSequence) {
        requireOwner(ownerFingerprint);
        requireUuid(clientSubscriptionId, "clientSubscriptionId");
        requireUuid(sessionId, "sessionId");
        requireSequence(requestedAfterSequence, "requestedAfterSequence");
        requireSequence(knownLatestSequence, "knownLatestSequence");
        if (queueCapacity < 1 || queueCapacity > MAX_QUEUE_CAPACITY) {
            throw new IllegalArgumentException("queueCapacity is out of range");
        }
        if (requestedAfterSequence > knownLatestSequence) {
            return new RegisterResult(RegisterOutcome.FUTURE_CURSOR, null);
        }
        String scope = sessionScope(sessionId);

        return runTransaction(() -> {
            EventCursorEntity existing = dao.findEventCursorByOwnerAndClient(
                    ownerFingerprint,
                    clientSubscriptionId);
            if (existing != null) {
                if (!scope.equals(existing.topicsCanonical)
                        || existing.queueCapacity != queueCapacity) {
                    return new RegisterResult(RegisterOutcome.CONFLICT, Snapshot.from(existing));
                }
                if (knownLatestSequence < existing.acknowledgedSequence) {
                    return new RegisterResult(
                            RegisterOutcome.SOURCE_REGRESSION,
                            Snapshot.from(existing));
                }
                if (requestedAfterSequence < existing.acknowledgedSequence) {
                    return new RegisterResult(
                            RegisterOutcome.STALE_CURSOR,
                            Snapshot.from(existing));
                }
                if (requestedAfterSequence > existing.acknowledgedSequence) {
                    return new RegisterResult(
                            RegisterOutcome.UNACKNOWLEDGED_CURSOR,
                            Snapshot.from(existing));
                }
                if (STATE_RESYNC_REQUIRED.equals(existing.state)) {
                    return new RegisterResult(
                            RegisterOutcome.RESYNC_REQUIRED,
                            Snapshot.from(existing));
                }

                if (STATE_CANCELLED.equals(existing.state)) {
                    if (dao.countActiveEventCursors() >= maxActiveRecords) {
                        return new RegisterResult(
                                RegisterOutcome.GLOBAL_LIMIT,
                                Snapshot.from(existing));
                    }
                    if (dao.countActiveEventCursorsByOwner(ownerFingerprint)
                            >= maxActiveRecordsPerOwner) {
                        return new RegisterResult(
                                RegisterOutcome.OWNER_LIMIT,
                                Snapshot.from(existing));
                    }
                    long now = now();
                    existing.requestedAfterSequence = requestedAfterSequence;
                    existing.state = STATE_ACTIVE;
                    existing.updatedAtWallMs = now;
                    updateOne(existing);
                    insertAudit(existing, AUDIT_REGISTERED, RegisterOutcome.REOPENED.name(), now);
                    return new RegisterResult(
                            RegisterOutcome.REOPENED,
                            Snapshot.from(existing));
                }
                return new RegisterResult(RegisterOutcome.REPLAYED, Snapshot.from(existing));
            }
            if (dao.countActiveEventCursors() >= maxActiveRecords) {
                return new RegisterResult(RegisterOutcome.GLOBAL_LIMIT, null);
            }
            if (dao.countActiveEventCursorsByOwner(ownerFingerprint)
                    >= maxActiveRecordsPerOwner) {
                return new RegisterResult(RegisterOutcome.OWNER_LIMIT, null);
            }

            long now = now();
            EventCursorEntity entity = new EventCursorEntity();
            entity.cursorId = generatedId("event-v2-");
            entity.ownerFingerprint = ownerFingerprint;
            entity.clientSubscriptionId = clientSubscriptionId;
            entity.topicsCanonical = scope;
            entity.requestedAfterSequence = requestedAfterSequence;
            entity.acknowledgedSequence = requestedAfterSequence;
            entity.queueCapacity = queueCapacity;
            entity.state = STATE_ACTIVE;
            entity.overflowFirstSequence = 0;
            entity.overflowLastSequence = 0;
            entity.overflowCount = 0;
            entity.createdAtWallMs = now;
            entity.updatedAtWallMs = now;
            dao.insertEventCursor(entity);
            insertAudit(entity, AUDIT_REGISTERED, RegisterOutcome.CREATED.name(), now);
            return new RegisterResult(RegisterOutcome.CREATED, Snapshot.from(entity));
        });
    }

    public Snapshot findOwned(String cursorId, String ownerFingerprint) {
        requireId(cursorId, "cursorId");
        requireOwner(ownerFingerprint);
        EventCursorEntity entity = dao.findEventCursor(cursorId);
        return entity != null && entity.ownerFingerprint.equals(ownerFingerprint)
                ? Snapshot.from(entity)
                : null;
    }

    public Snapshot findSessionOwned(
            String cursorId,
            String ownerFingerprint,
            String sessionId) {
        requireUuid(sessionId, "sessionId");
        Snapshot snapshot = findOwned(cursorId, ownerFingerprint);
        return snapshot != null && sessionScope(sessionId).equals(snapshot.topicsCanonical)
                ? snapshot
                : null;
    }

    public AckOutcome acknowledgeSessionOwned(
            String cursorId,
            String ownerFingerprint,
            String sessionId,
            long acknowledgedSequence,
            long knownLatestSequence) {
        if (findSessionOwned(cursorId, ownerFingerprint, sessionId) == null) {
            return AckOutcome.NOT_FOUND;
        }
        return acknowledgeOwned(
                cursorId,
                ownerFingerprint,
                acknowledgedSequence,
                knownLatestSequence);
    }

    public CancelOutcome cancelSessionOwned(
            String cursorId,
            String ownerFingerprint,
            String sessionId) {
        if (findSessionOwned(cursorId, ownerFingerprint, sessionId) == null) {
            return CancelOutcome.NOT_FOUND;
        }
        return cancelOwned(cursorId, ownerFingerprint);
    }

    public AckOutcome acknowledgeOwned(
            String cursorId,
            String ownerFingerprint,
            long acknowledgedSequence,
            long knownLatestSequence) {
        requireId(cursorId, "cursorId");
        requireOwner(ownerFingerprint);
        requireSequence(acknowledgedSequence, "acknowledgedSequence");
        requireSequence(knownLatestSequence, "knownLatestSequence");
        return runTransaction(() -> {
            EventCursorEntity entity = findEntityOwned(cursorId, ownerFingerprint);
            if (entity == null) {
                return AckOutcome.NOT_FOUND;
            }
            if (STATE_CANCELLED.equals(entity.state)) {
                return AckOutcome.NOT_ACTIVE;
            }
            if (knownLatestSequence < entity.acknowledgedSequence) {
                return AckOutcome.SOURCE_REGRESSION;
            }
            if (acknowledgedSequence > knownLatestSequence) {
                return AckOutcome.FUTURE_SEQUENCE;
            }
            if (STATE_RESYNC_REQUIRED.equals(entity.state)) {
                return AckOutcome.RESYNC_REQUIRED;
            }
            if (acknowledgedSequence < entity.acknowledgedSequence) {
                return AckOutcome.REGRESSION;
            }
            if (acknowledgedSequence == entity.acknowledgedSequence) {
                return AckOutcome.REPLAYED;
            }
            long now = now();
            entity.acknowledgedSequence = acknowledgedSequence;
            entity.updatedAtWallMs = now;
            updateOne(entity);
            insertAudit(entity, AUDIT_ACKNOWLEDGED, AckOutcome.APPLIED.name(), now);
            return AckOutcome.APPLIED;
        });
    }

    public OverflowOutcome markOverflowOwned(
            String cursorId,
            String ownerFingerprint,
            long firstDroppedSequence,
            long lastDroppedSequence,
            long knownLatestSequence) {
        requireId(cursorId, "cursorId");
        requireOwner(ownerFingerprint);
        requireSequence(firstDroppedSequence, "firstDroppedSequence");
        requireSequence(lastDroppedSequence, "lastDroppedSequence");
        requireSequence(knownLatestSequence, "knownLatestSequence");
        if (firstDroppedSequence > lastDroppedSequence) {
            throw new IllegalArgumentException("overflow range is invalid");
        }
        return runTransaction(() -> {
            EventCursorEntity entity = findEntityOwned(cursorId, ownerFingerprint);
            if (entity == null) {
                return OverflowOutcome.NOT_FOUND;
            }
            if (STATE_CANCELLED.equals(entity.state)) {
                return OverflowOutcome.NOT_ACTIVE;
            }
            if (knownLatestSequence < entity.acknowledgedSequence) {
                return OverflowOutcome.SOURCE_REGRESSION;
            }
            if (lastDroppedSequence > knownLatestSequence) {
                return OverflowOutcome.FUTURE_SEQUENCE;
            }
            if (lastDroppedSequence <= entity.acknowledgedSequence) {
                return OverflowOutcome.STALE_RANGE;
            }
            long effectiveFirst = firstDroppedSequence <= entity.acknowledgedSequence
                    ? entity.acknowledgedSequence + 1
                    : firstDroppedSequence;
            long mergedFirst = STATE_RESYNC_REQUIRED.equals(entity.state)
                    ? Math.min(entity.overflowFirstSequence, effectiveFirst)
                    : effectiveFirst;
            long mergedLast = STATE_RESYNC_REQUIRED.equals(entity.state)
                    ? Math.max(entity.overflowLastSequence, lastDroppedSequence)
                    : lastDroppedSequence;
            if (STATE_RESYNC_REQUIRED.equals(entity.state)
                    && mergedFirst == entity.overflowFirstSequence
                    && mergedLast == entity.overflowLastSequence) {
                return OverflowOutcome.REPLAYED;
            }

            long now = now();
            entity.state = STATE_RESYNC_REQUIRED;
            entity.overflowFirstSequence = mergedFirst;
            entity.overflowLastSequence = mergedLast;
            entity.overflowCount = inclusiveRangeCount(mergedFirst, mergedLast);
            entity.updatedAtWallMs = now;
            updateOne(entity);
            insertAudit(entity, AUDIT_OVERFLOWED, OverflowOutcome.APPLIED.name(), now);
            return OverflowOutcome.APPLIED;
        });
    }

    public ResyncOutcome completeResyncOwned(
            String cursorId,
            String ownerFingerprint,
            long resynchronizedSequence,
            long knownLatestSequence) {
        requireId(cursorId, "cursorId");
        requireOwner(ownerFingerprint);
        requireSequence(resynchronizedSequence, "resynchronizedSequence");
        requireSequence(knownLatestSequence, "knownLatestSequence");
        return runTransaction(() -> {
            EventCursorEntity entity = findEntityOwned(cursorId, ownerFingerprint);
            if (entity == null) {
                return ResyncOutcome.NOT_FOUND;
            }
            if (STATE_CANCELLED.equals(entity.state)) {
                return ResyncOutcome.NOT_ACTIVE;
            }
            if (knownLatestSequence < entity.acknowledgedSequence) {
                return ResyncOutcome.SOURCE_REGRESSION;
            }
            if (resynchronizedSequence > knownLatestSequence) {
                return ResyncOutcome.FUTURE_SEQUENCE;
            }
            if (STATE_ACTIVE.equals(entity.state)) {
                return resynchronizedSequence == entity.acknowledgedSequence
                        ? ResyncOutcome.REPLAYED
                        : ResyncOutcome.NOT_REQUIRED;
            }
            if (resynchronizedSequence < entity.acknowledgedSequence) {
                return ResyncOutcome.REGRESSION;
            }
            if (resynchronizedSequence < entity.overflowLastSequence) {
                return ResyncOutcome.INCOMPLETE;
            }

            long now = now();
            entity.acknowledgedSequence = resynchronizedSequence;
            entity.state = STATE_ACTIVE;
            entity.overflowFirstSequence = 0;
            entity.overflowLastSequence = 0;
            entity.overflowCount = 0;
            entity.updatedAtWallMs = now;
            updateOne(entity);
            insertAudit(entity, AUDIT_RESYNCED, ResyncOutcome.APPLIED.name(), now);
            return ResyncOutcome.APPLIED;
        });
    }

    public CancelOutcome cancelOwned(String cursorId, String ownerFingerprint) {
        requireId(cursorId, "cursorId");
        requireOwner(ownerFingerprint);
        return runTransaction(() -> {
            EventCursorEntity entity = findEntityOwned(cursorId, ownerFingerprint);
            if (entity == null) {
                return CancelOutcome.NOT_FOUND;
            }
            if (STATE_CANCELLED.equals(entity.state)) {
                return CancelOutcome.REPLAYED;
            }
            long now = now();
            entity.state = STATE_CANCELLED;
            entity.updatedAtWallMs = now;
            updateOne(entity);
            insertAudit(entity, AUDIT_CANCELLED, CancelOutcome.APPLIED.name(), now);
            trimCancelledRecords(entity.cursorId);
            return CancelOutcome.APPLIED;
        });
    }

    public boolean isDurable() {
        return true;
    }

    public boolean isProductionWired() {
        return false;
    }

    public boolean requiresDurableMonotonicEventSource() {
        return true;
    }

    private EventCursorEntity findEntityOwned(String cursorId, String ownerFingerprint) {
        EventCursorEntity entity = dao.findEventCursor(cursorId);
        return entity != null && entity.ownerFingerprint.equals(ownerFingerprint)
                ? entity
                : null;
    }

    private void trimCancelledRecords(String retainedCursorId) {
        while (dao.countCancelledEventCursors() > maxCancelledRecords) {
            EventCursorEntity oldest = dao.findOldestCancelledEventCursorExcept(
                    retainedCursorId);
            if (oldest == null || dao.deleteCancelledEventCursor(oldest.cursorId) != 1) {
                throw new IllegalStateException("cancelled Event cursor trim failed");
            }
        }
    }

    private void updateOne(EventCursorEntity entity) {
        if (dao.updateEventCursor(entity) != 1) {
            throw new IllegalStateException("Event cursor update did not affect one row");
        }
    }

    private void insertAudit(
            EventCursorEntity entity,
            String eventType,
            String outcome,
            long observedAtWallMs) {
        AuditEventEntity audit = new AuditEventEntity();
        audit.eventId = generatedId("audit-event-cursor-");
        audit.eventType = eventType;
        audit.subjectId = entity.cursorId;
        audit.ownerFingerprint = entity.ownerFingerprint;
        audit.outcome = outcome;
        audit.detailDigest = DurableDigest.sha256(
                "central-brain-event-cursor-v1",
                entity.cursorId,
                entity.clientSubscriptionId,
                entity.topicsCanonical,
                Long.toString(entity.requestedAfterSequence),
                Long.toString(entity.acknowledgedSequence),
                Integer.toString(entity.queueCapacity),
                entity.state,
                Long.toString(entity.overflowFirstSequence),
                Long.toString(entity.overflowLastSequence),
                Long.toString(entity.overflowCount));
        audit.observedAtWallMs = observedAtWallMs;
        dao.insertAuditEvent(audit);
    }

    private static boolean matchesRegistration(
            EventCursorEntity entity,
            String topicsCanonical,
            long requestedAfterSequence,
            int queueCapacity) {
        return entity.topicsCanonical.equals(topicsCanonical)
                && entity.requestedAfterSequence == requestedAfterSequence
                && entity.queueCapacity == queueCapacity;
    }

    private static String canonicalTopics(List<String> topicIds) {
        if (topicIds == null || topicIds.isEmpty() || topicIds.size() > 8) {
            throw new IllegalArgumentException("topicIds must contain 1..8 values");
        }
        TreeSet<String> canonical = new TreeSet<>();
        for (String topicId : topicIds) {
            requireId(topicId, "topicId");
            if (!BoundedEventRuntime.trustedTopics().contains(topicId)) {
                throw new IllegalArgumentException("topicId is not trusted");
            }
            if (!canonical.add(topicId)) {
                throw new IllegalArgumentException("topicIds must be unique");
            }
        }
        return String.join(",", canonical);
    }

    private static String sessionScope(String sessionId) {
        return "session:" + sessionId;
    }

    private <T> T runTransaction(Callable<T> operation) {
        return database.runInTransaction(operation);
    }

    private long now() {
        long value = wallClockMs.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("wall clock must be non-negative");
        }
        return value;
    }

    private String generatedId(String prefix) {
        String value = uniqueIdSource.get();
        if (value == null
                || value.isEmpty()
                || value.length() > MAX_ID_LENGTH
                || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalStateException("unique ID source returned an invalid value");
        }
        return prefix + value;
    }

    private static void requireOwner(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "ownerFingerprint must be a lowercase SHA-256 digest");
        }
    }

    private static void requireId(String value, String name) {
        if (value == null
                || value.isEmpty()
                || value.length() > MAX_ID_LENGTH
                || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void requireUuid(String value, String name) {
        if (value == null
                || !value.matches(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void requireSequence(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static long inclusiveRangeCount(long first, long last) {
        long distance = last - first;
        return distance == Long.MAX_VALUE ? Long.MAX_VALUE : distance + 1;
    }

    public enum RegisterOutcome {
        CREATED,
        REOPENED,
        REPLAYED,
        CONFLICT,
        FUTURE_CURSOR,
        STALE_CURSOR,
        UNACKNOWLEDGED_CURSOR,
        RESYNC_REQUIRED,
        SOURCE_REGRESSION,
        GLOBAL_LIMIT,
        OWNER_LIMIT
    }

    public enum AckOutcome {
        APPLIED,
        REPLAYED,
        NOT_FOUND,
        NOT_ACTIVE,
        RESYNC_REQUIRED,
        REGRESSION,
        FUTURE_SEQUENCE,
        SOURCE_REGRESSION
    }

    public enum OverflowOutcome {
        APPLIED,
        REPLAYED,
        NOT_FOUND,
        NOT_ACTIVE,
        STALE_RANGE,
        FUTURE_SEQUENCE,
        SOURCE_REGRESSION
    }

    public enum ResyncOutcome {
        APPLIED,
        REPLAYED,
        NOT_FOUND,
        NOT_ACTIVE,
        NOT_REQUIRED,
        INCOMPLETE,
        REGRESSION,
        FUTURE_SEQUENCE,
        SOURCE_REGRESSION
    }

    public enum CancelOutcome {
        APPLIED,
        REPLAYED,
        NOT_FOUND
    }

    public static final class RegisterResult {
        private final RegisterOutcome outcome;
        private final Snapshot snapshot;

        RegisterResult(RegisterOutcome outcome, Snapshot snapshot) {
            this.outcome = outcome;
            this.snapshot = snapshot;
        }

        public RegisterOutcome getOutcome() {
            return outcome;
        }

        public Snapshot getSnapshot() {
            return snapshot;
        }
    }

    public static final class Snapshot {
        private final String cursorId;
        private final String clientSubscriptionId;
        private final List<String> topicIds;
        private final long requestedAfterSequence;
        private final long acknowledgedSequence;
        private final int queueCapacity;
        private final String state;
        private final long overflowFirstSequence;
        private final long overflowLastSequence;
        private final long overflowCount;
        private final long createdAtWallMs;
        private final long updatedAtWallMs;
        private final String topicsCanonical;

        private Snapshot(EventCursorEntity entity) {
            cursorId = entity.cursorId;
            clientSubscriptionId = entity.clientSubscriptionId;
            topicIds = Collections.unmodifiableList(
                    new ArrayList<>(Arrays.asList(entity.topicsCanonical.split(","))));
            requestedAfterSequence = entity.requestedAfterSequence;
            acknowledgedSequence = entity.acknowledgedSequence;
            queueCapacity = entity.queueCapacity;
            state = entity.state;
            overflowFirstSequence = entity.overflowFirstSequence;
            overflowLastSequence = entity.overflowLastSequence;
            overflowCount = entity.overflowCount;
            createdAtWallMs = entity.createdAtWallMs;
            updatedAtWallMs = entity.updatedAtWallMs;
            topicsCanonical = entity.topicsCanonical;
        }

        static Snapshot from(EventCursorEntity entity) {
            return new Snapshot(entity);
        }

        public String getCursorId() {
            return cursorId;
        }

        public String getClientSubscriptionId() {
            return clientSubscriptionId;
        }

        public List<String> getTopicIds() {
            return topicIds;
        }

        public long getRequestedAfterSequence() {
            return requestedAfterSequence;
        }

        public long getAcknowledgedSequence() {
            return acknowledgedSequence;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public String getState() {
            return state;
        }

        public long getOverflowFirstSequence() {
            return overflowFirstSequence;
        }

        public long getOverflowLastSequence() {
            return overflowLastSequence;
        }

        public long getOverflowCount() {
            return overflowCount;
        }

        public long getCreatedAtWallMs() {
            return createdAtWallMs;
        }

        public long getUpdatedAtWallMs() {
            return updatedAtWallMs;
        }

        public String getSessionId() {
            return topicsCanonical.startsWith("session:")
                    ? topicsCanonical.substring("session:".length())
                    : "";
        }
    }
}
