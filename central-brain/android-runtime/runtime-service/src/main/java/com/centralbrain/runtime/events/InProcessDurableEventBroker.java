package com.centralbrain.runtime.events;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Append-before-notify Event Broker with bounded process-local retention.
 *
 * <p>The required class name reflects the Stage 2 contract target. This implementation is not
 * process-death durable and does not claim DDS or other production middleware.</p>
 */
public final class InProcessDurableEventBroker implements EventBroker {
    private final Limits limits;
    private final LongSupplier elapsedRealtimeMs;
    private final Supplier<String> subscriptionIdGenerator;
    private final AccessAuthority accessAuthority;
    private final Map<String, Deque<EventRecord>> retainedByTopic = new LinkedHashMap<>();
    private final Map<String, Long> latestCursorByTopic = new LinkedHashMap<>();
    private final LinkedHashMap<String, PublicationReplay> publicationReplayByKey =
            new LinkedHashMap<>();
    private final Map<String, SubscriptionRecord> activeById = new LinkedHashMap<>();
    private final Map<String, SubscriptionRecord> activeByClientKey = new LinkedHashMap<>();
    private final LinkedHashMap<String, ClosedSubscription> closedById = new LinkedHashMap<>();

    private long publishedCount;
    private long deliveredCount;
    private long callbackFailureCount;
    private long retentionEvictionCount;
    private int callbackDepth;
    private int authorityDepth;

    private InProcessDurableEventBroker(
            Limits limits,
            LongSupplier elapsedRealtimeMs,
            Supplier<String> subscriptionIdGenerator,
            AccessAuthority accessAuthority) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.elapsedRealtimeMs = Objects.requireNonNull(
                elapsedRealtimeMs,
                "elapsedRealtimeMs");
        this.subscriptionIdGenerator = Objects.requireNonNull(
                subscriptionIdGenerator,
                "subscriptionIdGenerator");
        this.accessAuthority = Objects.requireNonNull(accessAuthority, "accessAuthority");
        initializeTopic(TASK_STATE_TOPIC);
        initializeTopic(POLICY_DECISION_TOPIC);
        initializeTopic(MODEL_HEALTH_TOPIC);
    }

    public static InProcessDurableEventBroker createForContractTest(
            Limits limits,
            LongSupplier elapsedRealtimeMs,
            Supplier<String> subscriptionIdGenerator,
            AccessAuthority accessAuthority) {
        return new InProcessDurableEventBroker(
                limits,
                elapsedRealtimeMs,
                subscriptionIdGenerator,
                accessAuthority);
    }

    @Override
    public synchronized PublishResult publish(
            PublishRequest<?> request,
            AccessEvidence evidence) {
        rejectMutationReentrancy();
        Objects.requireNonNull(request, "request");
        Topic<?> topic = request.getTopic();
        AccessCheck access = checkAccess(Operation.PUBLISH, topic, evidence);
        if (access != AccessCheck.ALLOWED) {
            return new PublishResult(toPublishCode(access), null, 0, 0);
        }

        String replayKey = evidence.getOwnerFingerprint() + ":" + request.getRequestId();
        PublicationReplay replay = publicationReplayByKey.get(replayKey);
        if (replay != null) {
            PublishCode code = replay.requestDigest.equals(request.requestDigest())
                    ? PublishCode.REPLAYED
                    : PublishCode.REQUEST_CONFLICT;
            return new PublishResult(
                    code,
                    code == PublishCode.REPLAYED ? replay.event : null,
                    0,
                    0);
        }

        long currentCursor = latestCursorByTopic.get(topic.getTopicId());
        if (currentCursor == Long.MAX_VALUE) {
            return new PublishResult(PublishCode.SEQUENCE_EXHAUSTED, null, 0, 0);
        }
        long now = now();
        EventRecord event = new EventRecord(
                topic,
                currentCursor + 1,
                now,
                evidence.getOwnerFingerprint(),
                request.getPayload());
        Deque<EventRecord> retained = retainedByTopic.get(topic.getTopicId());
        retained.addLast(event);
        latestCursorByTopic.put(topic.getTopicId(), event.getCursor());
        while (retained.size() > limits.getRetainedEventsPerTopic()) {
            retained.removeFirst();
            retentionEvictionCount++;
        }
        publicationReplayByKey.put(
                replayKey,
                new PublicationReplay(request.requestDigest(), event));
        trim(publicationReplayByKey, limits.getReplayTombstones());
        publishedCount++;

        List<SubscriptionRecord> candidates = new ArrayList<>(activeById.values());
        int delivered = 0;
        int closed = 0;
        for (SubscriptionRecord record : candidates) {
            if (!record.topic.getTopicId().equals(event.getTopicId())
                    || event.getCursor() <= record.lastDeliveredCursor
                    || !record.request.getFilter().matches(event)
                    || !activeById.containsKey(record.handle.getSubscriptionId())) {
                continue;
            }
            try {
                callbackDepth++;
                record.consumer.onEvent(event);
                record.lastDeliveredCursor = event.getCursor();
                delivered++;
                deliveredCount++;
            } catch (RuntimeException callbackFailure) {
                callbackFailureCount++;
                close(record, EventSubscription.CloseReason.CALLBACK_FAILED);
                closed++;
            } finally {
                callbackDepth--;
            }
        }
        return new PublishResult(PublishCode.PUBLISHED, event, delivered, closed);
    }

    @Override
    public synchronized SubscribeResult subscribe(
            SubscriptionRequest request,
            EventConsumer consumer,
            AccessEvidence evidence) {
        rejectMutationReentrancy();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(consumer, "consumer");
        Topic<?> topic = request.getTopic();
        AccessCheck access = checkAccess(Operation.SUBSCRIBE, topic, evidence);
        if (access != AccessCheck.ALLOWED) {
            return subscribeFailure(toSubscribeCode(access), topic);
        }

        String clientKey = evidence.getOwnerFingerprint() + ":"
                + request.getClientSubscriptionId();
        SubscriptionRecord existing = activeByClientKey.get(clientKey);
        if (existing != null) {
            SubscribeCode code = existing.requestDigest.equals(request.requestDigest())
                    ? SubscribeCode.REPLAYED
                    : SubscribeCode.REQUEST_CONFLICT;
            return new SubscribeResult(
                    code,
                    code == SubscribeCode.REPLAYED ? existing.snapshot() : null,
                    earliestAvailable(topic),
                    latest(topic));
        }

        long latest = latest(topic);
        long earliest = earliestAvailable(topic);
        if (request.getAfterCursor() > latest) {
            return new SubscribeResult(
                    SubscribeCode.FUTURE_CURSOR,
                    null,
                    earliest,
                    latest);
        }
        if (hasRetentionGap(request.getAfterCursor(), earliest, latest)) {
            return new SubscribeResult(
                    SubscribeCode.CURSOR_GAP,
                    null,
                    earliest,
                    latest);
        }
        if (activeById.size() >= limits.getGlobalSubscriptions()
                || subscriptionsForOwner(evidence.getOwnerFingerprint())
                >= limits.getSubscriptionsPerOwner()) {
            return new SubscribeResult(
                    SubscribeCode.SUBSCRIPTION_LIMIT,
                    null,
                    earliest,
                    latest);
        }

        EventSubscription.Handle handle = new EventSubscription.Handle(
                nextSubscriptionId(),
                evidence.getOwnerFingerprint(),
                topic.getTopicId());
        SubscriptionRecord record = new SubscriptionRecord(
                handle,
                request,
                topic,
                evidence.getOwnerFingerprint(),
                request.requestDigest(),
                consumer);
        activeById.put(handle.getSubscriptionId(), record);
        activeByClientKey.put(clientKey, record);
        return new SubscribeResult(
                SubscribeCode.SUBSCRIBED,
                record.snapshot(),
                earliest,
                latest);
    }

    @Override
    public synchronized EventPage replay(
            ReplayRequest request,
            AccessEvidence evidence) {
        rejectAuthorityReentrancy();
        Objects.requireNonNull(request, "request");
        Topic<?> topic = request.getTopic();
        if (request.getLimit() > limits.getMaxReplayPage()) {
            throw new IllegalArgumentException("replay limit exceeds broker limit");
        }
        AccessCheck access = checkAccess(Operation.REPLAY, topic, evidence);
        long latest = latest(topic);
        long earliest = earliestAvailable(topic);
        if (access != AccessCheck.ALLOWED) {
            return replayFailure(toReplayCode(access), request, earliest, latest);
        }
        if (request.getAfterCursor() > latest) {
            return replayFailure(
                    ReplayCode.FUTURE_CURSOR,
                    request,
                    earliest,
                    latest);
        }
        if (hasRetentionGap(request.getAfterCursor(), earliest, latest)) {
            return replayFailure(
                    ReplayCode.CURSOR_GAP,
                    request,
                    earliest,
                    latest);
        }

        List<EventRecord> page = new ArrayList<>();
        boolean hasMore = false;
        for (EventRecord event : retainedByTopic.get(topic.getTopicId())) {
            if (event.getCursor() <= request.getAfterCursor()
                    || !request.getFilter().matches(event)) {
                continue;
            }
            if (page.size() < request.getLimit()) {
                page.add(event);
            } else {
                hasMore = true;
                break;
            }
        }
        long nextCursor = page.isEmpty()
                ? request.getAfterCursor()
                : page.get(page.size() - 1).getCursor();
        return new EventPage(
                ReplayCode.OK,
                page,
                earliest,
                latest,
                nextCursor,
                hasMore);
    }

    @Override
    public synchronized CancelResult cancel(
            EventSubscription.Handle handle,
            AccessEvidence evidence) {
        rejectMutationReentrancy();
        Objects.requireNonNull(handle, "handle");
        Topic<?> topic = topicById(handle.getTopicId());
        AccessCheck access = checkAccess(Operation.CANCEL, topic, evidence);
        if (access != AccessCheck.ALLOWED) {
            return new CancelResult(toCancelCode(access), null);
        }
        if (!handle.getOwnerFingerprint().equals(evidence.getOwnerFingerprint())) {
            return new CancelResult(CancelCode.NOT_FOUND_OR_NOT_OWNER, null);
        }

        SubscriptionRecord record = activeById.get(handle.getSubscriptionId());
        if (record == null) {
            ClosedSubscription closed = closedById.get(handle.getSubscriptionId());
            CancelCode code = closed != null
                    && closed.ownerFingerprint.equals(evidence.getOwnerFingerprint())
                    && closed.topicId.equals(handle.getTopicId())
                    ? CancelCode.ALREADY_CANCELLED
                    : CancelCode.NOT_FOUND_OR_NOT_OWNER;
            return new CancelResult(code, closed == null ? null : closed.subscription);
        }
        if (!record.ownerFingerprint.equals(evidence.getOwnerFingerprint())
                || !record.topic.getTopicId().equals(handle.getTopicId())) {
            return new CancelResult(CancelCode.NOT_FOUND_OR_NOT_OWNER, null);
        }
        EventSubscription closed = close(
                record,
                EventSubscription.CloseReason.OWNER_CANCELLED);
        return new CancelResult(CancelCode.CANCELLED, closed);
    }

    @Override
    public synchronized BrokerSnapshot snapshot() {
        int retainedCount = 0;
        for (Deque<EventRecord> events : retainedByTopic.values()) {
            retainedCount += events.size();
        }
        return new BrokerSnapshot(
                retainedCount,
                activeById.size(),
                publishedCount,
                deliveredCount,
                callbackFailureCount,
                retentionEvictionCount);
    }

    private void initializeTopic(Topic<?> topic) {
        retainedByTopic.put(topic.getTopicId(), new ArrayDeque<>());
        latestCursorByTopic.put(topic.getTopicId(), 0L);
    }

    private AccessCheck checkAccess(
            Operation operation,
            Topic<?> topic,
            AccessEvidence evidence) {
        long now = now();
        if (evidence == null || !evidence.matches(operation, topic, now)) {
            return AccessCheck.EVIDENCE_INVALID;
        }
        try {
            authorityDepth++;
            AccessDecision decision;
            try {
                decision = accessAuthority.authorize(
                        operation,
                        topic.getTopicId(),
                        evidence);
            } finally {
                authorityDepth--;
            }
            if (decision == AccessDecision.ALLOWED) {
                return AccessCheck.ALLOWED;
            }
            return decision == AccessDecision.DENIED
                    ? AccessCheck.DENIED
                    : AccessCheck.UNAVAILABLE;
        } catch (RuntimeException authorityFailure) {
            return AccessCheck.UNAVAILABLE;
        }
    }

    private long now() {
        long value = elapsedRealtimeMs.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("elapsed realtime must be non-negative");
        }
        return value;
    }

    private long latest(Topic<?> topic) {
        return latestCursorByTopic.get(topic.getTopicId());
    }

    private long earliestAvailable(Topic<?> topic) {
        Deque<EventRecord> retained = retainedByTopic.get(topic.getTopicId());
        return retained.isEmpty() ? latest(topic) + 1 : retained.peekFirst().getCursor();
    }

    private static boolean hasRetentionGap(long afterCursor, long earliest, long latest) {
        return afterCursor < latest && afterCursor + 1 < earliest;
    }

    private int subscriptionsForOwner(String ownerFingerprint) {
        int count = 0;
        for (SubscriptionRecord record : activeById.values()) {
            if (record.ownerFingerprint.equals(ownerFingerprint)) {
                count++;
            }
        }
        return count;
    }

    private String nextSubscriptionId() {
        for (int attempt = 0; attempt < 16; attempt++) {
            String candidate = EventBroker.requireMetadata(
                    subscriptionIdGenerator.get(),
                    "generated subscriptionId",
                    128);
            if (!activeById.containsKey(candidate) && !closedById.containsKey(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("subscription ID generator did not produce a unique ID");
    }

    private EventSubscription close(
            SubscriptionRecord record,
            EventSubscription.CloseReason reason) {
        activeById.remove(record.handle.getSubscriptionId());
        activeByClientKey.remove(record.ownerFingerprint + ":"
                + record.request.getClientSubscriptionId());
        EventSubscription snapshot = record.snapshot(reason);
        closedById.put(
                record.handle.getSubscriptionId(),
                new ClosedSubscription(
                        record.ownerFingerprint,
                        record.topic.getTopicId(),
                        snapshot));
        trim(closedById, limits.getReplayTombstones());
        return snapshot;
    }

    private void rejectMutationReentrancy() {
        if (callbackDepth != 0 || authorityDepth != 0) {
            throw new IllegalStateException("Event callback cannot mutate broker state");
        }
    }

    private void rejectAuthorityReentrancy() {
        if (authorityDepth != 0) {
            throw new IllegalStateException("Event authority cannot re-enter broker state");
        }
    }

    private Topic<?> topicById(String topicId) {
        if (TASK_STATE_TOPIC.getTopicId().equals(topicId)) {
            return TASK_STATE_TOPIC;
        }
        if (POLICY_DECISION_TOPIC.getTopicId().equals(topicId)) {
            return POLICY_DECISION_TOPIC;
        }
        if (MODEL_HEALTH_TOPIC.getTopicId().equals(topicId)) {
            return MODEL_HEALTH_TOPIC;
        }
        throw new IllegalArgumentException("unknown topic ID");
    }

    private SubscribeResult subscribeFailure(SubscribeCode code, Topic<?> topic) {
        return new SubscribeResult(code, null, earliestAvailable(topic), latest(topic));
    }

    private static EventPage replayFailure(
            ReplayCode code,
            ReplayRequest request,
            long earliest,
            long latest) {
        return new EventPage(
                code,
                Collections.emptyList(),
                earliest,
                latest,
                request.getAfterCursor(),
                false);
    }

    private static PublishCode toPublishCode(AccessCheck access) {
        if (access == AccessCheck.EVIDENCE_INVALID) {
            return PublishCode.EVIDENCE_INVALID;
        }
        return access == AccessCheck.DENIED
                ? PublishCode.ACCESS_DENIED
                : PublishCode.AUTHORITY_UNAVAILABLE;
    }

    private static SubscribeCode toSubscribeCode(AccessCheck access) {
        if (access == AccessCheck.EVIDENCE_INVALID) {
            return SubscribeCode.EVIDENCE_INVALID;
        }
        return access == AccessCheck.DENIED
                ? SubscribeCode.ACCESS_DENIED
                : SubscribeCode.AUTHORITY_UNAVAILABLE;
    }

    private static ReplayCode toReplayCode(AccessCheck access) {
        if (access == AccessCheck.EVIDENCE_INVALID) {
            return ReplayCode.EVIDENCE_INVALID;
        }
        return access == AccessCheck.DENIED
                ? ReplayCode.ACCESS_DENIED
                : ReplayCode.AUTHORITY_UNAVAILABLE;
    }

    private static CancelCode toCancelCode(AccessCheck access) {
        if (access == AccessCheck.EVIDENCE_INVALID) {
            return CancelCode.EVIDENCE_INVALID;
        }
        return access == AccessCheck.DENIED
                ? CancelCode.ACCESS_DENIED
                : CancelCode.AUTHORITY_UNAVAILABLE;
    }

    private static <K, V> void trim(LinkedHashMap<K, V> map, int limit) {
        while (map.size() > limit) {
            Iterator<K> iterator = map.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private enum AccessCheck {
        ALLOWED,
        EVIDENCE_INVALID,
        DENIED,
        UNAVAILABLE
    }

    private static final class PublicationReplay {
        final String requestDigest;
        final EventRecord event;

        PublicationReplay(String requestDigest, EventRecord event) {
            this.requestDigest = requestDigest;
            this.event = event;
        }
    }

    private static final class ClosedSubscription {
        final String ownerFingerprint;
        final String topicId;
        final EventSubscription subscription;

        ClosedSubscription(
                String ownerFingerprint,
                String topicId,
                EventSubscription subscription) {
            this.ownerFingerprint = ownerFingerprint;
            this.topicId = topicId;
            this.subscription = subscription;
        }
    }

    private final class SubscriptionRecord {
        final EventSubscription.Handle handle;
        final SubscriptionRequest request;
        final Topic<?> topic;
        final String ownerFingerprint;
        final String requestDigest;
        final EventConsumer consumer;
        long lastDeliveredCursor;

        SubscriptionRecord(
                EventSubscription.Handle handle,
                SubscriptionRequest request,
                Topic<?> topic,
                String ownerFingerprint,
                String requestDigest,
                EventConsumer consumer) {
            this.handle = handle;
            this.request = request;
            this.topic = topic;
            this.ownerFingerprint = ownerFingerprint;
            this.requestDigest = requestDigest;
            this.consumer = consumer;
            this.lastDeliveredCursor = request.getAfterCursor();
        }

        EventSubscription snapshot() {
            return snapshot(EventSubscription.CloseReason.ACTIVE);
        }

        EventSubscription snapshot(EventSubscription.CloseReason closeReason) {
            return new EventSubscription(
                    handle,
                    request.getClientSubscriptionId(),
                    ownerFingerprint,
                    topic.getTopicId(),
                    request.getAfterCursor(),
                    lastDeliveredCursor,
                    request.getFilter(),
                    closeReason);
        }
    }
}
