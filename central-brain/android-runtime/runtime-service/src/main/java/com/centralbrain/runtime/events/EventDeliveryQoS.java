package com.centralbrain.runtime.events;

import java.util.Objects;

/** Immutable process-local Event delivery QoS contract. */
public final class EventDeliveryQoS {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_QUEUE_CAPACITY = 256;
    public static final int MAX_DELIVERY_BATCH = 64;
    public static final long MAX_DEADLINE_WINDOW_MS = 300_000L;

    private EventDeliveryQoS() { }

    public enum OverflowPolicy {
        DROP_OLD,
        COALESCE,
        REJECT,
        DISCONNECT
    }

    public enum DeliveryClass {
        STANDARD,
        CRITICAL_ACTION_OBSERVATION
    }

    public enum Priority {
        BACKGROUND(0),
        NORMAL(1),
        HIGH(2),
        CRITICAL(3);

        private final int rank;

        Priority(int rank) {
            this.rank = rank;
        }

        int getRank() {
            return rank;
        }
    }

    public enum OfferCode {
        ENQUEUED,
        REPLAYED,
        DROPPED_OLD_AND_ENQUEUED,
        COALESCED,
        REQUEST_CONFLICT,
        REJECTED_QUEUE_FULL,
        REJECTED_DEADLINE,
        REJECTED_CURSOR,
        DISCONNECTED_REPLAY_REQUIRED,
        SUBSCRIPTION_DISCONNECTED
    }

    public enum DrainCode {
        DRAINED,
        DRAINED_WITH_EXPIRED,
        EMPTY,
        EXPIRED_REPLAY_REQUIRED,
        CONSUMER_FAILED,
        DISCONNECTED_REPLAY_REQUIRED,
        SUBSCRIPTION_DISCONNECTED,
        NOT_FOUND_OR_NOT_OWNER
    }

    public enum DisconnectReason {
        NONE,
        QUEUE_FULL,
        CRITICAL_DEADLINE_EXPIRED
    }

    public static final class QueueConfig {
        private final int capacity;
        private final int maxDeliveryBatch;
        private final OverflowPolicy overflowPolicy;

        public QueueConfig(
                int capacity,
                int maxDeliveryBatch,
                OverflowPolicy overflowPolicy) {
            if (capacity < 1 || capacity > MAX_QUEUE_CAPACITY
                    || maxDeliveryBatch < 1 || maxDeliveryBatch > MAX_DELIVERY_BATCH) {
                throw new IllegalArgumentException("Event delivery QoS limits are invalid");
            }
            this.capacity = capacity;
            this.maxDeliveryBatch = maxDeliveryBatch;
            this.overflowPolicy = Objects.requireNonNull(
                    overflowPolicy,
                    "overflowPolicy");
        }

        public int getCapacity() {
            return capacity;
        }

        public int getMaxDeliveryBatch() {
            return maxDeliveryBatch;
        }

        public OverflowPolicy getOverflowPolicy() {
            return overflowPolicy;
        }
    }

    public static final class DeliveryRequest {
        private final String requestId;
        private final EventBroker.EventRecord event;
        private final DeliveryClass deliveryClass;
        private final Priority priority;
        private final long deadlineElapsedRealtimeMs;
        private final String coalesceKeyDigest;

        public DeliveryRequest(
                String requestId,
                EventBroker.EventRecord event,
                DeliveryClass deliveryClass,
                Priority priority,
                long deadlineElapsedRealtimeMs,
                String coalesceKeyDigest) {
            this.requestId = EventBroker.requireMetadata(requestId, "requestId", 128);
            this.event = Objects.requireNonNull(event, "event");
            this.deliveryClass = Objects.requireNonNull(deliveryClass, "deliveryClass");
            this.priority = Objects.requireNonNull(priority, "priority");
            if (deadlineElapsedRealtimeMs < 1) {
                throw new IllegalArgumentException("deadlineElapsedRealtimeMs is invalid");
            }
            this.deadlineElapsedRealtimeMs = deadlineElapsedRealtimeMs;
            this.coalesceKeyDigest = coalesceKeyDigest == null
                    ? null
                    : EventBroker.requireDigest(coalesceKeyDigest, "coalesceKeyDigest");
            if (deliveryClass == DeliveryClass.CRITICAL_ACTION_OBSERVATION
                    && coalesceKeyDigest != null) {
                throw new IllegalArgumentException("critical delivery cannot have a coalesce key");
            }
        }

        public String getRequestId() {
            return requestId;
        }

        public EventBroker.EventRecord getEvent() {
            return event;
        }

        public DeliveryClass getDeliveryClass() {
            return deliveryClass;
        }

        public Priority getPriority() {
            return priority;
        }

        public long getDeadlineElapsedRealtimeMs() {
            return deadlineElapsedRealtimeMs;
        }

        public String getCoalesceKeyDigest() {
            return coalesceKeyDigest;
        }

        String requestDigest() {
            return EventBroker.digest("event-delivery|" + requestId + "|"
                    + event.getEventDigest() + "|" + deliveryClass.name() + "|"
                    + priority.name() + "|" + deadlineElapsedRealtimeMs + "|"
                    + (coalesceKeyDigest == null ? "none" : coalesceKeyDigest));
        }
    }

    public static final class OfferResult {
        private final OfferCode code;
        private final EventBroker.EventRecord acceptedEvent;
        private final long displacedCursor;
        private final int displacedCount;
        private final long replayAfterCursor;
        private final QueueSnapshot snapshot;

        OfferResult(
                OfferCode code,
                EventBroker.EventRecord acceptedEvent,
                long displacedCursor,
                int displacedCount,
                long replayAfterCursor,
                QueueSnapshot snapshot) {
            this.code = Objects.requireNonNull(code, "code");
            this.acceptedEvent = acceptedEvent;
            this.displacedCursor = displacedCursor;
            this.displacedCount = displacedCount;
            this.replayAfterCursor = replayAfterCursor;
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }

        public OfferCode getCode() {
            return code;
        }

        public EventBroker.EventRecord getAcceptedEvent() {
            return acceptedEvent;
        }

        public long getDisplacedCursor() {
            return displacedCursor;
        }

        public int getDisplacedCount() {
            return displacedCount;
        }

        public long getReplayAfterCursor() {
            return replayAfterCursor;
        }

        public QueueSnapshot getSnapshot() {
            return snapshot;
        }
    }

    public static final class DrainResult {
        private final DrainCode code;
        private final int deliveredCount;
        private final int expiredCount;
        private final int discardedCount;
        private final long replayAfterCursor;
        private final QueueSnapshot snapshot;

        DrainResult(
                DrainCode code,
                int deliveredCount,
                int expiredCount,
                int discardedCount,
                long replayAfterCursor,
                QueueSnapshot snapshot) {
            this.code = Objects.requireNonNull(code, "code");
            this.deliveredCount = deliveredCount;
            this.expiredCount = expiredCount;
            this.discardedCount = discardedCount;
            this.replayAfterCursor = replayAfterCursor;
            this.snapshot = snapshot;
        }

        public DrainCode getCode() {
            return code;
        }

        public int getDeliveredCount() {
            return deliveredCount;
        }

        public int getExpiredCount() {
            return expiredCount;
        }

        public int getDiscardedCount() {
            return discardedCount;
        }

        public long getReplayAfterCursor() {
            return replayAfterCursor;
        }

        public QueueSnapshot getSnapshot() {
            return snapshot;
        }
    }

    public static final class QueueSnapshot {
        private final int queuedCount;
        private final int queuedCriticalCount;
        private final long lastDeliveredCursor;
        private final long latestAcceptedCursor;
        private final long acceptedCount;
        private final long deliveredCount;
        private final long droppedOldCount;
        private final long coalescedCount;
        private final long rejectedCount;
        private final long expiredCount;
        private final long consumerFailureCount;
        private final long disconnectCount;
        private final long discardedOnDisconnectCount;
        private final boolean replayRequired;
        private final DisconnectReason disconnectReason;

        QueueSnapshot(
                int queuedCount,
                int queuedCriticalCount,
                long lastDeliveredCursor,
                long latestAcceptedCursor,
                long acceptedCount,
                long deliveredCount,
                long droppedOldCount,
                long coalescedCount,
                long rejectedCount,
                long expiredCount,
                long consumerFailureCount,
                long disconnectCount,
                long discardedOnDisconnectCount,
                boolean replayRequired,
                DisconnectReason disconnectReason) {
            this.queuedCount = queuedCount;
            this.queuedCriticalCount = queuedCriticalCount;
            this.lastDeliveredCursor = lastDeliveredCursor;
            this.latestAcceptedCursor = latestAcceptedCursor;
            this.acceptedCount = acceptedCount;
            this.deliveredCount = deliveredCount;
            this.droppedOldCount = droppedOldCount;
            this.coalescedCount = coalescedCount;
            this.rejectedCount = rejectedCount;
            this.expiredCount = expiredCount;
            this.consumerFailureCount = consumerFailureCount;
            this.disconnectCount = disconnectCount;
            this.discardedOnDisconnectCount = discardedOnDisconnectCount;
            this.replayRequired = replayRequired;
            this.disconnectReason = Objects.requireNonNull(
                    disconnectReason,
                    "disconnectReason");
        }

        public int getQueuedCount() {
            return queuedCount;
        }

        public int getQueuedCriticalCount() {
            return queuedCriticalCount;
        }

        public long getLastDeliveredCursor() {
            return lastDeliveredCursor;
        }

        public long getLatestAcceptedCursor() {
            return latestAcceptedCursor;
        }

        public long getAcceptedCount() {
            return acceptedCount;
        }

        public long getDeliveredCount() {
            return deliveredCount;
        }

        public long getDroppedOldCount() {
            return droppedOldCount;
        }

        public long getCoalescedCount() {
            return coalescedCount;
        }

        public long getRejectedCount() {
            return rejectedCount;
        }

        public long getExpiredCount() {
            return expiredCount;
        }

        public long getConsumerFailureCount() {
            return consumerFailureCount;
        }

        public long getDisconnectCount() {
            return disconnectCount;
        }

        public long getDiscardedOnDisconnectCount() {
            return discardedOnDisconnectCount;
        }

        public boolean isReplayRequired() {
            return replayRequired;
        }

        public DisconnectReason getDisconnectReason() {
            return disconnectReason;
        }

        public boolean isCriticalNoSilentDrop() {
            return true;
        }

        public boolean isProcessLocal() {
            return true;
        }

        public boolean isBrokerWired() {
            return false;
        }

        public boolean isDurablePersistenceWired() {
            return false;
        }

        public boolean isProductionMiddlewareWired() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }
    }
}
