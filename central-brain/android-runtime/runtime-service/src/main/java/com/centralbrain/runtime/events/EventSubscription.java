package com.centralbrain.runtime.events;

import java.util.Objects;

/** Immutable owner-scoped Event subscription snapshot. */
public final class EventSubscription {
    public enum CloseReason {
        ACTIVE,
        OWNER_CANCELLED,
        CALLBACK_FAILED
    }

    private final Handle handle;
    private final String clientSubscriptionId;
    private final String ownerFingerprint;
    private final String topicId;
    private final long requestedAfterCursor;
    private final long lastDeliveredCursor;
    private final EventBroker.EventFilter filter;
    private final CloseReason closeReason;

    EventSubscription(
            Handle handle,
            String clientSubscriptionId,
            String ownerFingerprint,
            String topicId,
            long requestedAfterCursor,
            long lastDeliveredCursor,
            EventBroker.EventFilter filter,
            CloseReason closeReason) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.clientSubscriptionId = clientSubscriptionId;
        this.ownerFingerprint = ownerFingerprint;
        this.topicId = topicId;
        this.requestedAfterCursor = requestedAfterCursor;
        this.lastDeliveredCursor = lastDeliveredCursor;
        this.filter = Objects.requireNonNull(filter, "filter");
        this.closeReason = Objects.requireNonNull(closeReason, "closeReason");
    }

    public Handle getHandle() {
        return handle;
    }

    public String getClientSubscriptionId() {
        return clientSubscriptionId;
    }

    public String getOwnerFingerprint() {
        return ownerFingerprint;
    }

    public String getTopicId() {
        return topicId;
    }

    public long getRequestedAfterCursor() {
        return requestedAfterCursor;
    }

    public long getLastDeliveredCursor() {
        return lastDeliveredCursor;
    }

    public EventBroker.EventFilter getFilter() {
        return filter;
    }

    public CloseReason getCloseReason() {
        return closeReason;
    }

    public boolean isActive() {
        return closeReason == CloseReason.ACTIVE;
    }

    public static final class Handle {
        private final String subscriptionId;
        private final String ownerFingerprint;
        private final String topicId;

        Handle(String subscriptionId, String ownerFingerprint, String topicId) {
            this.subscriptionId = EventBroker.requireMetadata(
                    subscriptionId,
                    "subscriptionId",
                    128);
            this.ownerFingerprint = EventBroker.requireDigest(
                    ownerFingerprint,
                    "ownerFingerprint");
            this.topicId = EventBroker.requireMetadata(topicId, "topicId", 96);
        }

        public String getSubscriptionId() {
            return subscriptionId;
        }

        public String getOwnerFingerprint() {
            return ownerFingerprint;
        }

        public String getTopicId() {
            return topicId;
        }
    }
}
