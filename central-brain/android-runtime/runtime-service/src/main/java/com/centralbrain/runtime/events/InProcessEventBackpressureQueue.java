package com.centralbrain.runtime.events;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Bounded per-subscription pressure queue. It is not wired to the production broker. */
public final class InProcessEventBackpressureQueue {
    private static final int MAX_REQUEST_TOMBSTONES = 128;

    private final EventSubscription.Handle subscription;
    private final EventDeliveryQoS.QueueConfig config;
    private final LongSupplier elapsedRealtimeMs;
    private final List<QueuedDelivery> queued = new ArrayList<>();
    private final LinkedHashMap<String, OfferReplay> replayByRequest = new LinkedHashMap<>();

    private long lastDeliveredCursor;
    private long latestAcceptedCursor;
    private long acceptedCount;
    private long deliveredCount;
    private long droppedOldCount;
    private long coalescedCount;
    private long rejectedCount;
    private long expiredCount;
    private long consumerFailureCount;
    private long disconnectCount;
    private long discardedOnDisconnectCount;
    private boolean replayRequired;
    private EventDeliveryQoS.DisconnectReason disconnectReason =
            EventDeliveryQoS.DisconnectReason.NONE;
    private int callbackDepth;

    private InProcessEventBackpressureQueue(
            EventSubscription.Handle subscription,
            long resumeAfterCursor,
            EventDeliveryQoS.QueueConfig config,
            LongSupplier elapsedRealtimeMs) {
        this.subscription = Objects.requireNonNull(subscription, "subscription");
        if (resumeAfterCursor < 0) {
            throw new IllegalArgumentException("resumeAfterCursor must be non-negative");
        }
        this.lastDeliveredCursor = resumeAfterCursor;
        this.latestAcceptedCursor = resumeAfterCursor;
        this.config = Objects.requireNonNull(config, "config");
        this.elapsedRealtimeMs = Objects.requireNonNull(elapsedRealtimeMs, "elapsedRealtimeMs");
    }

    public static InProcessEventBackpressureQueue createForContractTest(
            EventSubscription.Handle subscription,
            long resumeAfterCursor,
            EventDeliveryQoS.QueueConfig config,
            LongSupplier elapsedRealtimeMs) {
        return new InProcessEventBackpressureQueue(
                subscription,
                resumeAfterCursor,
                config,
                elapsedRealtimeMs);
    }

    public synchronized EventDeliveryQoS.OfferResult offer(
            EventDeliveryQoS.DeliveryRequest request) {
        rejectCallbackReentrancy();
        Objects.requireNonNull(request, "request");
        validateTopic(request.getEvent());

        String requestDigest = request.requestDigest();
        OfferReplay replay = replayByRequest.get(request.getRequestId());
        if (replay != null) {
            if (!replay.requestDigest.equals(requestDigest)) {
                return result(
                        EventDeliveryQoS.OfferCode.REQUEST_CONFLICT,
                        null,
                        -1,
                        0);
            }
            return result(
                    EventDeliveryQoS.OfferCode.REPLAYED,
                    replay.acceptedEvent,
                    replay.displacedCursor,
                    replay.displacedCount);
        }

        EventDeliveryQoS.OfferCode code;
        EventBroker.EventRecord acceptedEvent = null;
        long displacedCursor = -1;
        int displacedCount = 0;
        long now = now();
        if (isDisconnected()) {
            code = EventDeliveryQoS.OfferCode.SUBSCRIPTION_DISCONNECTED;
            rejectedCount++;
        } else if (request.getDeadlineElapsedRealtimeMs() <= now
                || request.getDeadlineElapsedRealtimeMs() - now
                > EventDeliveryQoS.MAX_DEADLINE_WINDOW_MS) {
            code = EventDeliveryQoS.OfferCode.REJECTED_DEADLINE;
            rejectedCount++;
            replayRequired = true;
        } else if (request.getEvent().getCursor() <= latestAcceptedCursor) {
            code = EventDeliveryQoS.OfferCode.REJECTED_CURSOR;
            rejectedCount++;
            replayRequired = true;
        } else if (queued.size() < config.getCapacity()) {
            enqueue(request);
            code = EventDeliveryQoS.OfferCode.ENQUEUED;
            acceptedEvent = request.getEvent();
        } else {
            PressureDecision pressure = applyPressure(request);
            code = pressure.code;
            acceptedEvent = pressure.acceptedEvent;
            displacedCursor = pressure.displacedCursor;
            displacedCount = pressure.displacedCount;
        }

        OfferReplay stored = new OfferReplay(
                requestDigest,
                acceptedEvent,
                displacedCursor,
                displacedCount);
        replayByRequest.put(request.getRequestId(), stored);
        trimReplay();
        return result(code, acceptedEvent, displacedCursor, displacedCount);
    }

    public synchronized EventDeliveryQoS.DrainResult drainOwned(
            String ownerFingerprint,
            int maxEvents,
            EventBroker.EventConsumer consumer) {
        rejectCallbackReentrancy();
        EventBroker.requireDigest(ownerFingerprint, "ownerFingerprint");
        Objects.requireNonNull(consumer, "consumer");
        if (!subscription.getOwnerFingerprint().equals(ownerFingerprint)) {
            return new EventDeliveryQoS.DrainResult(
                    EventDeliveryQoS.DrainCode.NOT_FOUND_OR_NOT_OWNER,
                    0,
                    0,
                    0,
                    lastDeliveredCursor,
                    null);
        }
        if (maxEvents < 1 || maxEvents > config.getMaxDeliveryBatch()) {
            throw new IllegalArgumentException("maxEvents exceeds queue QoS");
        }
        if (isDisconnected()) {
            return drainResult(
                    EventDeliveryQoS.DrainCode.SUBSCRIPTION_DISCONNECTED,
                    0,
                    0,
                    0);
        }

        int delivered = 0;
        int expired = 0;
        while (delivered < maxEvents && !queued.isEmpty()) {
            QueuedDelivery head = queued.get(0);
            if (head.request.getDeadlineElapsedRealtimeMs() <= now()) {
                if (head.isCritical()) {
                    expiredCount++;
                    expired++;
                    int discarded = disconnect(
                            EventDeliveryQoS.DisconnectReason.CRITICAL_DEADLINE_EXPIRED);
                    return drainResult(
                            EventDeliveryQoS.DrainCode.DISCONNECTED_REPLAY_REQUIRED,
                            delivered,
                            expired,
                            discarded);
                }
                queued.remove(0);
                expiredCount++;
                expired++;
                replayRequired = true;
                continue;
            }

            try {
                callbackDepth++;
                consumer.onEvent(head.request.getEvent());
            } catch (RuntimeException consumerFailure) {
                consumerFailureCount++;
                return drainResult(
                        EventDeliveryQoS.DrainCode.CONSUMER_FAILED,
                        delivered,
                        expired,
                        0);
            } finally {
                callbackDepth--;
            }
            queued.remove(0);
            lastDeliveredCursor = head.request.getEvent().getCursor();
            delivered++;
            deliveredCount++;
        }

        EventDeliveryQoS.DrainCode code;
        if (delivered > 0 && expired > 0) {
            code = EventDeliveryQoS.DrainCode.DRAINED_WITH_EXPIRED;
        } else if (delivered > 0) {
            code = EventDeliveryQoS.DrainCode.DRAINED;
        } else if (expired > 0) {
            code = EventDeliveryQoS.DrainCode.EXPIRED_REPLAY_REQUIRED;
        } else {
            code = EventDeliveryQoS.DrainCode.EMPTY;
        }
        return drainResult(code, delivered, expired, 0);
    }

    public synchronized EventDeliveryQoS.QueueSnapshot snapshotOwned(String ownerFingerprint) {
        EventBroker.requireDigest(ownerFingerprint, "ownerFingerprint");
        return subscription.getOwnerFingerprint().equals(ownerFingerprint) ? snapshot() : null;
    }

    private PressureDecision applyPressure(EventDeliveryQoS.DeliveryRequest request) {
        switch (config.getOverflowPolicy()) {
            case DROP_OLD:
                return dropOld(request);
            case COALESCE:
                return coalesce(request);
            case REJECT:
                rejectedCount++;
                replayRequired = true;
                return PressureDecision.rejected(EventDeliveryQoS.OfferCode.REJECTED_QUEUE_FULL);
            case DISCONNECT:
                int discarded = disconnect(EventDeliveryQoS.DisconnectReason.QUEUE_FULL);
                rejectedCount++;
                return new PressureDecision(
                        EventDeliveryQoS.OfferCode.DISCONNECTED_REPLAY_REQUIRED,
                        null,
                        -1,
                        discarded);
            default:
                throw new IllegalStateException("unknown overflow policy");
        }
    }

    private PressureDecision dropOld(EventDeliveryQoS.DeliveryRequest incoming) {
        int candidateIndex = -1;
        int candidateRank = Integer.MAX_VALUE;
        for (int index = 0; index < queued.size(); index++) {
            QueuedDelivery candidate = queued.get(index);
            int rank = candidate.request.getPriority().getRank();
            if (candidate.isCritical()
                    || rank > incoming.getPriority().getRank()
                    || rank >= candidateRank) {
                continue;
            }
            candidateIndex = index;
            candidateRank = rank;
        }
        if (candidateIndex < 0) {
            rejectedCount++;
            replayRequired = true;
            return PressureDecision.rejected(EventDeliveryQoS.OfferCode.REJECTED_QUEUE_FULL);
        }
        QueuedDelivery displaced = queued.remove(candidateIndex);
        enqueue(incoming);
        droppedOldCount++;
        replayRequired = true;
        return new PressureDecision(
                EventDeliveryQoS.OfferCode.DROPPED_OLD_AND_ENQUEUED,
                incoming.getEvent(),
                displaced.request.getEvent().getCursor(),
                1);
    }

    private PressureDecision coalesce(EventDeliveryQoS.DeliveryRequest incoming) {
        if (incoming.getDeliveryClass()
                == EventDeliveryQoS.DeliveryClass.CRITICAL_ACTION_OBSERVATION
                || incoming.getCoalesceKeyDigest() == null) {
            rejectedCount++;
            replayRequired = true;
            return PressureDecision.rejected(EventDeliveryQoS.OfferCode.REJECTED_QUEUE_FULL);
        }
        for (int index = queued.size() - 1; index >= 0; index--) {
            QueuedDelivery candidate = queued.get(index);
            if (candidate.isCritical()
                    || !incoming.getCoalesceKeyDigest().equals(
                    candidate.request.getCoalesceKeyDigest())
                    || candidate.request.getPriority().getRank()
                    > incoming.getPriority().getRank()) {
                continue;
            }
            queued.remove(index);
            enqueue(incoming);
            coalescedCount++;
            replayRequired = true;
            return new PressureDecision(
                    EventDeliveryQoS.OfferCode.COALESCED,
                    incoming.getEvent(),
                    candidate.request.getEvent().getCursor(),
                    1);
        }
        rejectedCount++;
        replayRequired = true;
        return PressureDecision.rejected(EventDeliveryQoS.OfferCode.REJECTED_QUEUE_FULL);
    }

    private void enqueue(EventDeliveryQoS.DeliveryRequest request) {
        queued.add(new QueuedDelivery(request));
        latestAcceptedCursor = request.getEvent().getCursor();
        acceptedCount++;
    }

    private int disconnect(EventDeliveryQoS.DisconnectReason reason) {
        int discarded = queued.size();
        queued.clear();
        disconnectedReason(reason);
        discardedOnDisconnectCount += discarded;
        return discarded;
    }

    private void disconnectedReason(EventDeliveryQoS.DisconnectReason reason) {
        disconnectReason = Objects.requireNonNull(reason, "reason");
        replayRequired = true;
        disconnectCount++;
    }

    private boolean isDisconnected() {
        return disconnectReason != EventDeliveryQoS.DisconnectReason.NONE;
    }

    private EventDeliveryQoS.OfferResult result(
            EventDeliveryQoS.OfferCode code,
            EventBroker.EventRecord acceptedEvent,
            long displacedCursor,
            int displacedCount) {
        return new EventDeliveryQoS.OfferResult(
                code,
                acceptedEvent,
                displacedCursor,
                displacedCount,
                lastDeliveredCursor,
                snapshot());
    }

    private EventDeliveryQoS.DrainResult drainResult(
            EventDeliveryQoS.DrainCode code,
            int delivered,
            int expired,
            int discarded) {
        return new EventDeliveryQoS.DrainResult(
                code,
                delivered,
                expired,
                discarded,
                lastDeliveredCursor,
                snapshot());
    }

    private EventDeliveryQoS.QueueSnapshot snapshot() {
        int criticalCount = 0;
        for (QueuedDelivery delivery : queued) {
            if (delivery.isCritical()) {
                criticalCount++;
            }
        }
        return new EventDeliveryQoS.QueueSnapshot(
                queued.size(),
                criticalCount,
                lastDeliveredCursor,
                latestAcceptedCursor,
                acceptedCount,
                deliveredCount,
                droppedOldCount,
                coalescedCount,
                rejectedCount,
                expiredCount,
                consumerFailureCount,
                disconnectCount,
                discardedOnDisconnectCount,
                replayRequired,
                disconnectReason);
    }

    private void validateTopic(EventBroker.EventRecord event) {
        if (!subscription.getTopicId().equals(event.getTopicId())) {
            throw new IllegalArgumentException("event topic does not match subscription");
        }
    }

    private long now() {
        long value = elapsedRealtimeMs.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("elapsed realtime must be non-negative");
        }
        return value;
    }

    private void trimReplay() {
        while (replayByRequest.size() > MAX_REQUEST_TOMBSTONES) {
            Iterator<Map.Entry<String, OfferReplay>> iterator =
                    replayByRequest.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private void rejectCallbackReentrancy() {
        if (callbackDepth != 0) {
            throw new IllegalStateException("Event consumer cannot mutate delivery queue");
        }
    }

    private static final class QueuedDelivery {
        final EventDeliveryQoS.DeliveryRequest request;

        QueuedDelivery(EventDeliveryQoS.DeliveryRequest request) {
            this.request = request;
        }

        boolean isCritical() {
            return request.getDeliveryClass()
                    == EventDeliveryQoS.DeliveryClass.CRITICAL_ACTION_OBSERVATION;
        }
    }

    private static final class OfferReplay {
        final String requestDigest;
        final EventBroker.EventRecord acceptedEvent;
        final long displacedCursor;
        final int displacedCount;

        OfferReplay(
                String requestDigest,
                EventBroker.EventRecord acceptedEvent,
                long displacedCursor,
                int displacedCount) {
            this.requestDigest = requestDigest;
            this.acceptedEvent = acceptedEvent;
            this.displacedCursor = displacedCursor;
            this.displacedCount = displacedCount;
        }
    }

    private static final class PressureDecision {
        final EventDeliveryQoS.OfferCode code;
        final EventBroker.EventRecord acceptedEvent;
        final long displacedCursor;
        final int displacedCount;

        PressureDecision(
                EventDeliveryQoS.OfferCode code,
                EventBroker.EventRecord acceptedEvent,
                long displacedCursor,
                int displacedCount) {
            this.code = code;
            this.acceptedEvent = acceptedEvent;
            this.displacedCursor = displacedCursor;
            this.displacedCount = displacedCount;
        }

        static PressureDecision rejected(EventDeliveryQoS.OfferCode code) {
            return new PressureDecision(code, null, -1, 0);
        }
    }
}
