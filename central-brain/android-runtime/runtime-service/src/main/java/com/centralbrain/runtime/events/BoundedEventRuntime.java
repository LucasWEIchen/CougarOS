package com.centralbrain.runtime.events;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Process-local bounded Event contract runtime. Production transport is not wired. */
public final class BoundedEventRuntime {
    public static final String TOPIC_TASK_STATE = "runtime.task.state";
    public static final String TOPIC_POLICY_DECISION = "governance.policy.decision";
    public static final String TOPIC_MODEL_HEALTH = "model.runtime.health";

    private static final Set<String> TRUSTED_TOPICS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    TOPIC_TASK_STATE,
                    TOPIC_POLICY_DECISION,
                    TOPIC_MODEL_HEALTH)));
    private static final int MAX_CANCELLED_TOMBSTONES = 64;

    public enum PublishOutcome {
        PUBLISHED,
        UNKNOWN_TOPIC,
        SEQUENCE_EXHAUSTED
    }

    public enum SubscribeOutcome {
        CREATED,
        REPLAYED,
        CONFLICT,
        UNKNOWN_TOPIC,
        FUTURE_CURSOR,
        GLOBAL_LIMIT,
        OWNER_LIMIT
    }

    public enum DispatchOutcome {
        DELIVERED,
        EMPTY,
        OBSERVER_FAILED,
        NOT_FOUND_OR_NOT_OWNER
    }

    public enum CancelOutcome {
        CANCELLED,
        ALREADY_CANCELLED,
        NOT_FOUND_OR_NOT_OWNER
    }

    public enum CloseReason {
        OWNER_CANCELLED
    }

    public interface EventObserver {
        void onOverflow(OverflowSignal overflow);

        void onEvent(EventEnvelope event);

        void onClosed(String subscriptionId, CloseReason reason);
    }

    private final Limits limits;
    private final LongSupplier elapsedRealtimeMs;
    private final Supplier<String> subscriptionIdGenerator;
    private final Deque<EventEnvelope> retained = new ArrayDeque<>();
    private final Map<String, SubscriptionRecord> activeById = new LinkedHashMap<>();
    private final Map<String, SubscriptionRecord> activeByClientKey = new LinkedHashMap<>();
    private final LinkedHashMap<String, CancelledRecord> cancelledById = new LinkedHashMap<>();

    private long latestSequence;
    private long publishedCount;
    private long deliveredCount;
    private long overflowSignalCount;
    private long observerFailureCount;
    private long cancelledCount;
    private long retentionEvictionCount;
    private int observerCallbackDepth;

    private BoundedEventRuntime(
            Limits limits,
            LongSupplier elapsedRealtimeMs,
            Supplier<String> subscriptionIdGenerator) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.elapsedRealtimeMs = Objects.requireNonNull(
                elapsedRealtimeMs,
                "elapsedRealtimeMs");
        this.subscriptionIdGenerator = Objects.requireNonNull(
                subscriptionIdGenerator,
                "subscriptionIdGenerator");
    }

    public static BoundedEventRuntime createForContractTest(
            Limits limits,
            LongSupplier elapsedRealtimeMs,
            Supplier<String> subscriptionIdGenerator) {
        return new BoundedEventRuntime(limits, elapsedRealtimeMs, subscriptionIdGenerator);
    }

    public static Set<String> trustedTopics() {
        return TRUSTED_TOPICS;
    }

    public synchronized PublishResult publish(TrustedPublication publication) {
        rejectObserverReentrancy();
        Objects.requireNonNull(publication, "publication");
        if (!TRUSTED_TOPICS.contains(publication.getTopicId())) {
            return new PublishResult(PublishOutcome.UNKNOWN_TOPIC, null);
        }
        if (latestSequence == Long.MAX_VALUE) {
            return new PublishResult(PublishOutcome.SEQUENCE_EXHAUSTED, null);
        }
        long now = elapsedRealtimeMs.getAsLong();
        if (now < 0) {
            throw new IllegalStateException("elapsed realtime must be non-negative");
        }
        EventEnvelope event = new EventEnvelope(
                publication.getTopicId(),
                ++latestSequence,
                now,
                publication.getSchemaId(),
                publication.getPayloadDigest());
        retained.addLast(event);
        while (retained.size() > limits.getRetainedEventLimit()) {
            retained.removeFirst();
            retentionEvictionCount++;
        }
        for (SubscriptionRecord record : activeById.values()) {
            if (record.request.getTopicIds().contains(event.getTopicId())) {
                enqueue(record, event);
            }
        }
        publishedCount++;
        return new PublishResult(PublishOutcome.PUBLISHED, event);
    }

    public synchronized SubscribeResult subscribe(
            TrustedSubscription request,
            EventObserver observer) {
        rejectObserverReentrancy();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(observer, "observer");
        if (request.getQueueCapacity() > limits.getMaxQueueCapacity()) {
            throw new IllegalArgumentException("queueCapacity exceeds runtime limit");
        }
        String clientKey = clientKey(request.getOwnerFingerprint(), request.getClientSubscriptionId());
        SubscriptionRecord existing = activeByClientKey.get(clientKey);
        if (existing != null) {
            if (!existing.request.matches(request)) {
                return new SubscribeResult(SubscribeOutcome.CONFLICT, null);
            }
            return new SubscribeResult(
                    SubscribeOutcome.REPLAYED,
                    existing.snapshot());
        }
        for (String topicId : request.getTopicIds()) {
            if (!TRUSTED_TOPICS.contains(topicId)) {
                return new SubscribeResult(SubscribeOutcome.UNKNOWN_TOPIC, null);
            }
        }
        if (request.getAfterSequence() > latestSequence) {
            return new SubscribeResult(SubscribeOutcome.FUTURE_CURSOR, null);
        }
        if (activeById.size() >= limits.getGlobalSubscriptionLimit()) {
            return new SubscribeResult(SubscribeOutcome.GLOBAL_LIMIT, null);
        }
        if (ownerSubscriptionCount(request.getOwnerFingerprint())
                >= limits.getPerOwnerSubscriptionLimit()) {
            return new SubscribeResult(SubscribeOutcome.OWNER_LIMIT, null);
        }

        String subscriptionId = nextSubscriptionId();
        SubscriptionRecord record = new SubscriptionRecord(
                subscriptionId,
                request,
                observer);
        markRetentionGap(record);
        for (EventEnvelope event : retained) {
            if (event.getSequence() > request.getAfterSequence()
                    && request.getTopicIds().contains(event.getTopicId())) {
                enqueue(record, event);
            }
        }
        activeById.put(subscriptionId, record);
        activeByClientKey.put(clientKey, record);
        return new SubscribeResult(SubscribeOutcome.CREATED, record.snapshot());
    }

    public synchronized DispatchResult dispatchOwned(
            String subscriptionId,
            String ownerFingerprint,
            int maxEvents) {
        rejectObserverReentrancy();
        requireMetadata(subscriptionId, "subscriptionId", 128);
        requireOwner(ownerFingerprint);
        if (maxEvents < 1 || maxEvents > limits.getMaxDispatchBatch()) {
            throw new IllegalArgumentException("maxEvents is out of range");
        }
        SubscriptionRecord record = activeById.get(subscriptionId);
        if (record == null
                || !record.request.getOwnerFingerprint().equals(ownerFingerprint)) {
            return new DispatchResult(
                    DispatchOutcome.NOT_FOUND_OR_NOT_OWNER,
                    0,
                    false,
                    null);
        }

        boolean overflowDelivered = false;
        if (record.pendingOverflowCount > 0) {
            OverflowSignal overflow = record.overflowSignal(latestSequence);
            try {
                observerCallbackDepth++;
                record.observer.onOverflow(overflow);
            } catch (RuntimeException exception) {
                observerFailureCount++;
                return new DispatchResult(
                        DispatchOutcome.OBSERVER_FAILED,
                        0,
                        false,
                        record.snapshot());
            } finally {
                observerCallbackDepth--;
            }
            record.clearOverflow();
            overflowSignalCount++;
            overflowDelivered = true;
        }

        int delivered = 0;
        while (delivered < maxEvents && !record.pending.isEmpty()) {
            EventEnvelope event = record.pending.peekFirst();
            try {
                observerCallbackDepth++;
                record.observer.onEvent(event);
            } catch (RuntimeException exception) {
                observerFailureCount++;
                return new DispatchResult(
                        DispatchOutcome.OBSERVER_FAILED,
                        delivered,
                        overflowDelivered,
                        record.snapshot());
            } finally {
                observerCallbackDepth--;
            }
            record.pending.removeFirst();
            record.lastDeliveredSequence = event.getSequence();
            delivered++;
            deliveredCount++;
        }
        DispatchOutcome outcome = delivered > 0 || overflowDelivered
                ? DispatchOutcome.DELIVERED
                : DispatchOutcome.EMPTY;
        return new DispatchResult(
                outcome,
                delivered,
                overflowDelivered,
                record.snapshot());
    }

    public synchronized CancelResult cancelOwned(
            String subscriptionId,
            String ownerFingerprint) {
        rejectObserverReentrancy();
        requireMetadata(subscriptionId, "subscriptionId", 128);
        requireOwner(ownerFingerprint);
        SubscriptionRecord record = activeById.get(subscriptionId);
        if (record == null) {
            CancelledRecord cancelled = cancelledById.get(subscriptionId);
            CancelOutcome outcome = cancelled != null
                    && cancelled.ownerFingerprint.equals(ownerFingerprint)
                    ? CancelOutcome.ALREADY_CANCELLED
                    : CancelOutcome.NOT_FOUND_OR_NOT_OWNER;
            return new CancelResult(outcome, null);
        }
        if (!record.request.getOwnerFingerprint().equals(ownerFingerprint)) {
            return new CancelResult(CancelOutcome.NOT_FOUND_OR_NOT_OWNER, null);
        }
        activeById.remove(subscriptionId);
        activeByClientKey.remove(clientKey(
                ownerFingerprint,
                record.request.getClientSubscriptionId()));
        record.pending.clear();
        cancelledById.put(subscriptionId, new CancelledRecord(ownerFingerprint));
        trimCancelledTombstones();
        cancelledCount++;
        try {
            observerCallbackDepth++;
            record.observer.onClosed(subscriptionId, CloseReason.OWNER_CANCELLED);
        } catch (RuntimeException ignored) {
            // Cancellation ownership is independent from callback behavior.
        } finally {
            observerCallbackDepth--;
        }
        return new CancelResult(CancelOutcome.CANCELLED, record.snapshot());
    }

    public synchronized SubscriptionSnapshot findOwned(
            String subscriptionId,
            String ownerFingerprint) {
        requireMetadata(subscriptionId, "subscriptionId", 128);
        requireOwner(ownerFingerprint);
        SubscriptionRecord record = activeById.get(subscriptionId);
        if (record == null
                || !record.request.getOwnerFingerprint().equals(ownerFingerprint)) {
            return null;
        }
        return record.snapshot();
    }

    public synchronized Snapshot snapshot() {
        long earliestSequence = retained.isEmpty()
                ? latestSequence + 1
                : retained.peekFirst().getSequence();
        int queuedDeliveryCount = 0;
        for (SubscriptionRecord record : activeById.values()) {
            queuedDeliveryCount += record.pending.size();
        }
        return new Snapshot(
                latestSequence,
                earliestSequence,
                retained.size(),
                activeById.size(),
                queuedDeliveryCount,
                publishedCount,
                deliveredCount,
                overflowSignalCount,
                observerFailureCount,
                cancelledCount,
                retentionEvictionCount,
                false,
                false,
                false);
    }

    private void markRetentionGap(SubscriptionRecord record) {
        long after = record.request.getAfterSequence();
        if (after >= latestSequence) {
            return;
        }
        long firstAvailable = retained.isEmpty()
                ? latestSequence + 1
                : retained.peekFirst().getSequence();
        if (after + 1 < firstAvailable) {
            record.markOverflow(after + 1, firstAvailable - 1);
        }
    }

    private void enqueue(SubscriptionRecord record, EventEnvelope event) {
        while (record.pending.size() >= record.request.getQueueCapacity()) {
            EventEnvelope dropped = record.pending.removeFirst();
            record.markOverflow(dropped.getSequence(), dropped.getSequence());
        }
        record.pending.addLast(event);
    }

    private int ownerSubscriptionCount(String ownerFingerprint) {
        int count = 0;
        for (SubscriptionRecord record : activeById.values()) {
            if (record.request.getOwnerFingerprint().equals(ownerFingerprint)) {
                count++;
            }
        }
        return count;
    }

    private String nextSubscriptionId() {
        for (int attempt = 0; attempt < 16; attempt++) {
            String candidate = requireMetadata(
                    subscriptionIdGenerator.get(),
                    "generated subscriptionId",
                    128);
            if (!activeById.containsKey(candidate) && !cancelledById.containsKey(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("subscription ID generator did not produce a unique ID");
    }

    private void trimCancelledTombstones() {
        while (cancelledById.size() > MAX_CANCELLED_TOMBSTONES) {
            Iterator<String> iterator = cancelledById.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private void rejectObserverReentrancy() {
        if (observerCallbackDepth != 0) {
            throw new IllegalStateException("observer callbacks cannot mutate Event runtime");
        }
    }

    private static String clientKey(String ownerFingerprint, String clientSubscriptionId) {
        return ownerFingerprint + ":" + clientSubscriptionId;
    }

    private static String requireMetadata(String value, String name, int maxLength) {
        if (value == null
                || value.trim().isEmpty()
                || value.length() > maxLength
                || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadDigest must be lowercase SHA-256");
        }
        return value;
    }

    private static String requireOwner(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("ownerFingerprint must be lowercase SHA-256");
        }
        return value;
    }

    public static final class Limits {
        private final int retainedEventLimit;
        private final int globalSubscriptionLimit;
        private final int perOwnerSubscriptionLimit;
        private final int maxQueueCapacity;
        private final int maxDispatchBatch;

        public Limits(
                int retainedEventLimit,
                int globalSubscriptionLimit,
                int perOwnerSubscriptionLimit,
                int maxQueueCapacity,
                int maxDispatchBatch) {
            if (retainedEventLimit < 1 || retainedEventLimit > 1_024
                    || globalSubscriptionLimit < 1 || globalSubscriptionLimit > 256
                    || perOwnerSubscriptionLimit < 1
                    || perOwnerSubscriptionLimit > globalSubscriptionLimit
                    || maxQueueCapacity < 1 || maxQueueCapacity > 256
                    || maxDispatchBatch < 1 || maxDispatchBatch > 256) {
                throw new IllegalArgumentException("event runtime limits are invalid");
            }
            this.retainedEventLimit = retainedEventLimit;
            this.globalSubscriptionLimit = globalSubscriptionLimit;
            this.perOwnerSubscriptionLimit = perOwnerSubscriptionLimit;
            this.maxQueueCapacity = maxQueueCapacity;
            this.maxDispatchBatch = maxDispatchBatch;
        }

        public int getRetainedEventLimit() {
            return retainedEventLimit;
        }

        public int getGlobalSubscriptionLimit() {
            return globalSubscriptionLimit;
        }

        public int getPerOwnerSubscriptionLimit() {
            return perOwnerSubscriptionLimit;
        }

        public int getMaxQueueCapacity() {
            return maxQueueCapacity;
        }

        public int getMaxDispatchBatch() {
            return maxDispatchBatch;
        }
    }

    public static final class TrustedPublication {
        private final String topicId;
        private final String schemaId;
        private final String payloadDigest;

        private TrustedPublication(String topicId, String schemaId, String payloadDigest) {
            this.topicId = requireMetadata(topicId, "topicId", 96);
            this.schemaId = requireMetadata(schemaId, "schemaId", 96);
            this.payloadDigest = requireDigest(payloadDigest);
        }

        public static TrustedPublication fromRuntimePolicy(
                String topicId,
                String schemaId,
                String payloadDigest) {
            return new TrustedPublication(topicId, schemaId, payloadDigest);
        }

        public String getTopicId() {
            return topicId;
        }

        public String getSchemaId() {
            return schemaId;
        }

        public String getPayloadDigest() {
            return payloadDigest;
        }
    }

    public static final class TrustedSubscription {
        private final String clientSubscriptionId;
        private final String ownerFingerprint;
        private final List<String> topicIds;
        private final long afterSequence;
        private final int queueCapacity;

        private TrustedSubscription(
                String clientSubscriptionId,
                String ownerFingerprint,
                List<String> topicIds,
                long afterSequence,
                int queueCapacity) {
            this.clientSubscriptionId = requireMetadata(
                    clientSubscriptionId,
                    "clientSubscriptionId",
                    128);
            this.ownerFingerprint = requireOwner(ownerFingerprint);
            if (topicIds == null || topicIds.isEmpty() || topicIds.size() > 8) {
                throw new IllegalArgumentException("topicIds must contain 1..8 values");
            }
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (String topicId : topicIds) {
                unique.add(requireMetadata(topicId, "topicId", 96));
            }
            if (unique.size() != topicIds.size()) {
                throw new IllegalArgumentException("topicIds must be unique");
            }
            if (afterSequence < 0) {
                throw new IllegalArgumentException("afterSequence must be non-negative");
            }
            if (queueCapacity < 1 || queueCapacity > 256) {
                throw new IllegalArgumentException("queueCapacity is out of range");
            }
            this.topicIds = Collections.unmodifiableList(new ArrayList<>(unique));
            this.afterSequence = afterSequence;
            this.queueCapacity = queueCapacity;
        }

        public static TrustedSubscription fromRuntimePolicy(
                String clientSubscriptionId,
                String ownerFingerprint,
                List<String> topicIds,
                long afterSequence,
                int queueCapacity) {
            return new TrustedSubscription(
                    clientSubscriptionId,
                    ownerFingerprint,
                    topicIds,
                    afterSequence,
                    queueCapacity);
        }

        public String getClientSubscriptionId() {
            return clientSubscriptionId;
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public List<String> getTopicIds() {
            return topicIds;
        }

        public long getAfterSequence() {
            return afterSequence;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        boolean matches(TrustedSubscription other) {
            return clientSubscriptionId.equals(other.clientSubscriptionId)
                    && ownerFingerprint.equals(other.ownerFingerprint)
                    && topicIds.equals(other.topicIds)
                    && afterSequence == other.afterSequence
                    && queueCapacity == other.queueCapacity;
        }
    }

    public static final class EventEnvelope {
        private final String topicId;
        private final long sequence;
        private final long publishedAtElapsedRealtimeMs;
        private final String schemaId;
        private final String payloadDigest;

        EventEnvelope(
                String topicId,
                long sequence,
                long publishedAtElapsedRealtimeMs,
                String schemaId,
                String payloadDigest) {
            this.topicId = topicId;
            this.sequence = sequence;
            this.publishedAtElapsedRealtimeMs = publishedAtElapsedRealtimeMs;
            this.schemaId = schemaId;
            this.payloadDigest = payloadDigest;
        }

        public String getTopicId() {
            return topicId;
        }

        public long getSequence() {
            return sequence;
        }

        public long getPublishedAtElapsedRealtimeMs() {
            return publishedAtElapsedRealtimeMs;
        }

        public String getSchemaId() {
            return schemaId;
        }

        public String getPayloadDigest() {
            return payloadDigest;
        }
    }

    public static final class OverflowSignal {
        private final long firstDroppedSequence;
        private final long lastDroppedSequence;
        private final long droppedCount;
        private final long firstAvailableSequence;
        private final long latestSequence;

        OverflowSignal(
                long firstDroppedSequence,
                long lastDroppedSequence,
                long droppedCount,
                long firstAvailableSequence,
                long latestSequence) {
            this.firstDroppedSequence = firstDroppedSequence;
            this.lastDroppedSequence = lastDroppedSequence;
            this.droppedCount = droppedCount;
            this.firstAvailableSequence = firstAvailableSequence;
            this.latestSequence = latestSequence;
        }

        public long getFirstDroppedSequence() {
            return firstDroppedSequence;
        }

        public long getLastDroppedSequence() {
            return lastDroppedSequence;
        }

        public long getDroppedCount() {
            return droppedCount;
        }

        public long getFirstAvailableSequence() {
            return firstAvailableSequence;
        }

        public long getLatestSequence() {
            return latestSequence;
        }
    }

    public static final class PublishResult {
        private final PublishOutcome outcome;
        private final EventEnvelope event;

        PublishResult(PublishOutcome outcome, EventEnvelope event) {
            this.outcome = outcome;
            this.event = event;
        }

        public PublishOutcome getOutcome() {
            return outcome;
        }

        public EventEnvelope getEvent() {
            return event;
        }
    }

    public static final class SubscribeResult {
        private final SubscribeOutcome outcome;
        private final SubscriptionSnapshot subscription;

        SubscribeResult(SubscribeOutcome outcome, SubscriptionSnapshot subscription) {
            this.outcome = outcome;
            this.subscription = subscription;
        }

        public SubscribeOutcome getOutcome() {
            return outcome;
        }

        public SubscriptionSnapshot getSubscription() {
            return subscription;
        }
    }

    public static final class DispatchResult {
        private final DispatchOutcome outcome;
        private final int deliveredEventCount;
        private final boolean overflowDelivered;
        private final SubscriptionSnapshot subscription;

        DispatchResult(
                DispatchOutcome outcome,
                int deliveredEventCount,
                boolean overflowDelivered,
                SubscriptionSnapshot subscription) {
            this.outcome = outcome;
            this.deliveredEventCount = deliveredEventCount;
            this.overflowDelivered = overflowDelivered;
            this.subscription = subscription;
        }

        public DispatchOutcome getOutcome() {
            return outcome;
        }

        public int getDeliveredEventCount() {
            return deliveredEventCount;
        }

        public boolean isOverflowDelivered() {
            return overflowDelivered;
        }

        public SubscriptionSnapshot getSubscription() {
            return subscription;
        }
    }

    public static final class CancelResult {
        private final CancelOutcome outcome;
        private final SubscriptionSnapshot subscription;

        CancelResult(CancelOutcome outcome, SubscriptionSnapshot subscription) {
            this.outcome = outcome;
            this.subscription = subscription;
        }

        public CancelOutcome getOutcome() {
            return outcome;
        }

        public SubscriptionSnapshot getSubscription() {
            return subscription;
        }
    }

    public static final class SubscriptionSnapshot {
        private final String subscriptionId;
        private final String clientSubscriptionId;
        private final List<String> topicIds;
        private final long requestedAfterSequence;
        private final long lastDeliveredSequence;
        private final int queuedEventCount;
        private final long pendingOverflowCount;
        private final boolean active;

        SubscriptionSnapshot(
                String subscriptionId,
                String clientSubscriptionId,
                List<String> topicIds,
                long requestedAfterSequence,
                long lastDeliveredSequence,
                int queuedEventCount,
                long pendingOverflowCount,
                boolean active) {
            this.subscriptionId = subscriptionId;
            this.clientSubscriptionId = clientSubscriptionId;
            this.topicIds = Collections.unmodifiableList(new ArrayList<>(topicIds));
            this.requestedAfterSequence = requestedAfterSequence;
            this.lastDeliveredSequence = lastDeliveredSequence;
            this.queuedEventCount = queuedEventCount;
            this.pendingOverflowCount = pendingOverflowCount;
            this.active = active;
        }

        public String getSubscriptionId() {
            return subscriptionId;
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

        public long getLastDeliveredSequence() {
            return lastDeliveredSequence;
        }

        public int getQueuedEventCount() {
            return queuedEventCount;
        }

        public long getPendingOverflowCount() {
            return pendingOverflowCount;
        }

        public boolean isActive() {
            return active;
        }
    }

    public static final class Snapshot {
        private final long latestSequence;
        private final long earliestRetainedSequence;
        private final int retainedEventCount;
        private final int activeSubscriptionCount;
        private final int queuedDeliveryCount;
        private final long publishedCount;
        private final long deliveredCount;
        private final long overflowSignalCount;
        private final long observerFailureCount;
        private final long cancelledCount;
        private final long retentionEvictionCount;
        private final boolean cursorPersistenceWired;
        private final boolean productionBrokerWired;
        private final boolean hardwareAccessed;

        Snapshot(
                long latestSequence,
                long earliestRetainedSequence,
                int retainedEventCount,
                int activeSubscriptionCount,
                int queuedDeliveryCount,
                long publishedCount,
                long deliveredCount,
                long overflowSignalCount,
                long observerFailureCount,
                long cancelledCount,
                long retentionEvictionCount,
                boolean cursorPersistenceWired,
                boolean productionBrokerWired,
                boolean hardwareAccessed) {
            this.latestSequence = latestSequence;
            this.earliestRetainedSequence = earliestRetainedSequence;
            this.retainedEventCount = retainedEventCount;
            this.activeSubscriptionCount = activeSubscriptionCount;
            this.queuedDeliveryCount = queuedDeliveryCount;
            this.publishedCount = publishedCount;
            this.deliveredCount = deliveredCount;
            this.overflowSignalCount = overflowSignalCount;
            this.observerFailureCount = observerFailureCount;
            this.cancelledCount = cancelledCount;
            this.retentionEvictionCount = retentionEvictionCount;
            this.cursorPersistenceWired = cursorPersistenceWired;
            this.productionBrokerWired = productionBrokerWired;
            this.hardwareAccessed = hardwareAccessed;
        }

        public long getLatestSequence() {
            return latestSequence;
        }

        public long getEarliestRetainedSequence() {
            return earliestRetainedSequence;
        }

        public int getRetainedEventCount() {
            return retainedEventCount;
        }

        public int getActiveSubscriptionCount() {
            return activeSubscriptionCount;
        }

        public int getQueuedDeliveryCount() {
            return queuedDeliveryCount;
        }

        public long getPublishedCount() {
            return publishedCount;
        }

        public long getDeliveredCount() {
            return deliveredCount;
        }

        public long getOverflowSignalCount() {
            return overflowSignalCount;
        }

        public long getObserverFailureCount() {
            return observerFailureCount;
        }

        public long getCancelledCount() {
            return cancelledCount;
        }

        public long getRetentionEvictionCount() {
            return retentionEvictionCount;
        }

        public boolean isCursorPersistenceWired() {
            return cursorPersistenceWired;
        }

        public boolean isProductionBrokerWired() {
            return productionBrokerWired;
        }

        public boolean isHardwareAccessed() {
            return hardwareAccessed;
        }
    }

    private final class SubscriptionRecord {
        final String subscriptionId;
        final TrustedSubscription request;
        final EventObserver observer;
        final Deque<EventEnvelope> pending = new ArrayDeque<>();
        long lastDeliveredSequence;
        long pendingOverflowCount;
        long firstDroppedSequence;
        long lastDroppedSequence;

        SubscriptionRecord(
                String subscriptionId,
                TrustedSubscription request,
                EventObserver observer) {
            this.subscriptionId = subscriptionId;
            this.request = request;
            this.observer = observer;
            this.lastDeliveredSequence = request.getAfterSequence();
        }

        void markOverflow(long firstDropped, long lastDropped) {
            if (firstDropped > lastDropped) {
                return;
            }
            if (pendingOverflowCount == 0) {
                firstDroppedSequence = firstDropped;
            }
            lastDroppedSequence = lastDropped;
            pendingOverflowCount += lastDropped - firstDropped + 1;
        }

        OverflowSignal overflowSignal(long currentLatestSequence) {
            long firstAvailable = pending.isEmpty()
                    ? currentLatestSequence + 1
                    : pending.peekFirst().getSequence();
            return new OverflowSignal(
                    firstDroppedSequence,
                    lastDroppedSequence,
                    pendingOverflowCount,
                    firstAvailable,
                    currentLatestSequence);
        }

        void clearOverflow() {
            pendingOverflowCount = 0;
            firstDroppedSequence = 0;
            lastDroppedSequence = 0;
        }

        SubscriptionSnapshot snapshot() {
            return new SubscriptionSnapshot(
                    subscriptionId,
                    request.getClientSubscriptionId(),
                    request.getTopicIds(),
                    request.getAfterSequence(),
                    lastDeliveredSequence,
                    pending.size(),
                    pendingOverflowCount,
                    activeById.containsKey(subscriptionId));
        }
    }

    private static final class CancelledRecord {
        final String ownerFingerprint;

        CancelledRecord(String ownerFingerprint) {
            this.ownerFingerprint = ownerFingerprint;
        }
    }
}
