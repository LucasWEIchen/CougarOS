package com.centralbrain.runtime.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class BoundedEventRuntimeTest {
    private static final String OWNER_A = "a".repeat(64);
    private static final String OWNER_B = "b".repeat(64);
    private static final String DIGEST = "c".repeat(64);

    @Test
    public void trustedTopicsUseMonotonicBoundedSequence() {
        Harness harness = harness(2, 4, 2, 2);
        BoundedEventRuntime.PublishResult first = harness.runtime.publish(publication(
                BoundedEventRuntime.TOPIC_TASK_STATE));
        BoundedEventRuntime.PublishResult second = harness.runtime.publish(publication(
                BoundedEventRuntime.TOPIC_POLICY_DECISION));
        BoundedEventRuntime.PublishResult third = harness.runtime.publish(publication(
                BoundedEventRuntime.TOPIC_MODEL_HEALTH));

        assertEquals(BoundedEventRuntime.PublishOutcome.PUBLISHED, first.getOutcome());
        assertEquals(1, first.getEvent().getSequence());
        assertEquals(2, second.getEvent().getSequence());
        assertEquals(3, third.getEvent().getSequence());
        assertEquals(3, harness.runtime.snapshot().getLatestSequence());
        assertEquals(2, harness.runtime.snapshot().getEarliestRetainedSequence());
        assertEquals(2, harness.runtime.snapshot().getRetainedEventCount());
        assertEquals(1, harness.runtime.snapshot().getRetentionEvictionCount());
        assertEquals(BoundedEventRuntime.PublishOutcome.UNKNOWN_TOPIC,
                harness.runtime.publish(publication("unknown.topic")).getOutcome());
        assertEquals(3, harness.runtime.snapshot().getPublishedCount());
    }

    @Test
    public void subscriptionReplayIsIdempotentAndOwnerScoped() {
        Harness harness = harness(8, 2, 1, 4);
        harness.runtime.publish(publication(BoundedEventRuntime.TOPIC_TASK_STATE));
        RecordingObserver original = new RecordingObserver();
        RecordingObserver replayObserver = new RecordingObserver();
        BoundedEventRuntime.TrustedSubscription request = subscription(
                "client-a",
                OWNER_A,
                BoundedEventRuntime.TOPIC_TASK_STATE,
                0,
                2);
        BoundedEventRuntime.SubscribeResult created = harness.runtime.subscribe(
                request,
                original);
        BoundedEventRuntime.SubscribeResult replay = harness.runtime.subscribe(
                request,
                replayObserver);

        assertEquals(BoundedEventRuntime.SubscribeOutcome.CREATED, created.getOutcome());
        assertEquals(BoundedEventRuntime.SubscribeOutcome.REPLAYED, replay.getOutcome());
        assertEquals(created.getSubscription().getSubscriptionId(),
                replay.getSubscription().getSubscriptionId());
        assertEquals(BoundedEventRuntime.SubscribeOutcome.CONFLICT,
                harness.runtime.subscribe(
                        subscription(
                                "client-a",
                                OWNER_A,
                                BoundedEventRuntime.TOPIC_TASK_STATE,
                                0,
                                1),
                        replayObserver).getOutcome());
        assertEquals(BoundedEventRuntime.SubscribeOutcome.OWNER_LIMIT,
                harness.runtime.subscribe(
                        subscription(
                                "client-b",
                                OWNER_A,
                                BoundedEventRuntime.TOPIC_TASK_STATE,
                                0,
                                2),
                        replayObserver).getOutcome());

        BoundedEventRuntime.DispatchResult dispatch = harness.runtime.dispatchOwned(
                created.getSubscription().getSubscriptionId(),
                OWNER_A,
                4);
        assertEquals(BoundedEventRuntime.DispatchOutcome.DELIVERED, dispatch.getOutcome());
        assertEquals(1, original.events.size());
        assertEquals(0, replayObserver.events.size());
        assertNull(harness.runtime.findOwned(
                created.getSubscription().getSubscriptionId(),
                OWNER_B));
    }

    @Test
    public void cursorAndQueueOverflowPrecedeRetainedEvent() {
        Harness harness = harness(2, 4, 2, 1);
        for (int index = 0; index < 4; index++) {
            harness.runtime.publish(publication(BoundedEventRuntime.TOPIC_TASK_STATE));
        }
        RecordingObserver observer = new RecordingObserver();
        BoundedEventRuntime.SubscribeResult created = harness.runtime.subscribe(
                subscription(
                        "overflow",
                        OWNER_A,
                        BoundedEventRuntime.TOPIC_TASK_STATE,
                        0,
                        1),
                observer);
        assertEquals(3, created.getSubscription().getPendingOverflowCount());
        assertEquals(1, created.getSubscription().getQueuedEventCount());

        BoundedEventRuntime.DispatchResult dispatch = harness.runtime.dispatchOwned(
                created.getSubscription().getSubscriptionId(),
                OWNER_A,
                4);
        assertEquals(BoundedEventRuntime.DispatchOutcome.DELIVERED, dispatch.getOutcome());
        assertTrue(dispatch.isOverflowDelivered());
        assertEquals(List.of("overflow:1-3", "event:4"), observer.order);
        assertEquals(3, observer.overflows.get(0).getDroppedCount());
        assertEquals(4, observer.overflows.get(0).getFirstAvailableSequence());
        assertEquals(0, dispatch.getSubscription().getQueuedEventCount());
    }

    @Test
    public void observerFailureRetainsEventForRetry() {
        Harness harness = harness(4, 4, 2, 2);
        harness.runtime.publish(publication(BoundedEventRuntime.TOPIC_MODEL_HEALTH));
        RecordingObserver observer = new RecordingObserver();
        observer.failNextEvent = true;
        BoundedEventRuntime.SubscribeResult created = harness.runtime.subscribe(
                subscription(
                        "retry",
                        OWNER_A,
                        BoundedEventRuntime.TOPIC_MODEL_HEALTH,
                        0,
                        2),
                observer);
        BoundedEventRuntime.DispatchResult failed = harness.runtime.dispatchOwned(
                created.getSubscription().getSubscriptionId(),
                OWNER_A,
                2);
        assertEquals(BoundedEventRuntime.DispatchOutcome.OBSERVER_FAILED, failed.getOutcome());
        assertEquals(1, failed.getSubscription().getQueuedEventCount());

        BoundedEventRuntime.DispatchResult retried = harness.runtime.dispatchOwned(
                created.getSubscription().getSubscriptionId(),
                OWNER_A,
                2);
        assertEquals(BoundedEventRuntime.DispatchOutcome.DELIVERED, retried.getOutcome());
        assertEquals(1, observer.events.size());
        assertEquals(1, harness.runtime.snapshot().getObserverFailureCount());
        assertEquals(1, harness.runtime.snapshot().getDeliveredCount());
    }

    @Test
    public void observerReentrantMutationFailsClosedWithoutQueueCorruption() {
        Harness harness = harness(4, 4, 2, 2);
        harness.runtime.publish(publication(BoundedEventRuntime.TOPIC_TASK_STATE));
        RecordingObserver observer = new RecordingObserver();
        observer.reentrantRuntime = harness.runtime;
        BoundedEventRuntime.SubscribeResult created = harness.runtime.subscribe(
                subscription(
                        "reentrant",
                        OWNER_A,
                        BoundedEventRuntime.TOPIC_TASK_STATE,
                        0,
                        2),
                observer);
        BoundedEventRuntime.DispatchResult failed = harness.runtime.dispatchOwned(
                created.getSubscription().getSubscriptionId(),
                OWNER_A,
                2);
        assertEquals(BoundedEventRuntime.DispatchOutcome.OBSERVER_FAILED, failed.getOutcome());
        assertEquals(1, failed.getSubscription().getQueuedEventCount());
        assertEquals(1, harness.runtime.snapshot().getPublishedCount());

        observer.reentrantRuntime = null;
        assertEquals(BoundedEventRuntime.DispatchOutcome.DELIVERED,
                harness.runtime.dispatchOwned(
                        created.getSubscription().getSubscriptionId(),
                        OWNER_A,
                        2).getOutcome());
        assertEquals(1, observer.events.size());
    }

    @Test
    public void cancellationIsOwnerIsolatedAndIdempotent() {
        Harness harness = harness(4, 4, 2, 2);
        RecordingObserver observer = new RecordingObserver();
        BoundedEventRuntime.SubscribeResult created = harness.runtime.subscribe(
                subscription(
                        "cancel",
                        OWNER_A,
                        BoundedEventRuntime.TOPIC_POLICY_DECISION,
                        0,
                        2),
                observer);
        String subscriptionId = created.getSubscription().getSubscriptionId();
        assertEquals(BoundedEventRuntime.CancelOutcome.NOT_FOUND_OR_NOT_OWNER,
                harness.runtime.cancelOwned(subscriptionId, OWNER_B).getOutcome());
        BoundedEventRuntime.CancelResult cancelled = harness.runtime.cancelOwned(
                subscriptionId,
                OWNER_A);
        assertEquals(BoundedEventRuntime.CancelOutcome.CANCELLED, cancelled.getOutcome());
        assertFalse(cancelled.getSubscription().isActive());
        assertEquals(1, observer.closedCount);
        assertEquals(BoundedEventRuntime.CancelOutcome.ALREADY_CANCELLED,
                harness.runtime.cancelOwned(subscriptionId, OWNER_A).getOutcome());
        assertEquals(BoundedEventRuntime.CancelOutcome.NOT_FOUND_OR_NOT_OWNER,
                harness.runtime.cancelOwned(subscriptionId, OWNER_B).getOutcome());
        assertEquals(1, harness.runtime.snapshot().getCancelledCount());
    }

    @Test
    public void invalidTopicFutureCursorAndQueueLimitFailClosed() {
        Harness harness = harness(4, 4, 2, 2);
        RecordingObserver observer = new RecordingObserver();
        assertEquals(BoundedEventRuntime.SubscribeOutcome.UNKNOWN_TOPIC,
                harness.runtime.subscribe(
                        subscription("unknown", OWNER_A, "unknown.topic", 0, 2),
                        observer).getOutcome());
        assertEquals(BoundedEventRuntime.SubscribeOutcome.FUTURE_CURSOR,
                harness.runtime.subscribe(
                        subscription(
                                "future",
                                OWNER_A,
                                BoundedEventRuntime.TOPIC_TASK_STATE,
                                1,
                                2),
                        observer).getOutcome());
        try {
            harness.runtime.subscribe(
                    subscription(
                            "too-large",
                            OWNER_A,
                            BoundedEventRuntime.TOPIC_TASK_STATE,
                            0,
                            3),
                    observer);
            fail("queue capacity above the runtime limit must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("runtime limit"));
        }
        assertFalse(harness.runtime.snapshot().isCursorPersistenceWired());
        assertFalse(harness.runtime.snapshot().isProductionBrokerWired());
        assertFalse(harness.runtime.snapshot().isHardwareAccessed());
    }

    private static Harness harness(
            int retention,
            int globalSubscriptions,
            int ownerSubscriptions,
            int maxQueue) {
        AtomicLong clock = new AtomicLong(1_000);
        AtomicInteger ids = new AtomicInteger();
        BoundedEventRuntime runtime = BoundedEventRuntime.createForContractTest(
                new BoundedEventRuntime.Limits(
                        retention,
                        globalSubscriptions,
                        ownerSubscriptions,
                        maxQueue,
                        8),
                clock::incrementAndGet,
                () -> "event-sub-" + ids.incrementAndGet());
        return new Harness(runtime, clock);
    }

    private static BoundedEventRuntime.TrustedPublication publication(String topicId) {
        return BoundedEventRuntime.TrustedPublication.fromRuntimePolicy(
                topicId,
                "central.event.v1",
                DIGEST);
    }

    private static BoundedEventRuntime.TrustedSubscription subscription(
            String clientId,
            String owner,
            String topic,
            long afterSequence,
            int queueCapacity) {
        return BoundedEventRuntime.TrustedSubscription.fromRuntimePolicy(
                clientId,
                owner,
                Collections.singletonList(topic),
                afterSequence,
                queueCapacity);
    }

    private static final class Harness {
        final BoundedEventRuntime runtime;
        final AtomicLong clock;

        Harness(BoundedEventRuntime runtime, AtomicLong clock) {
            this.runtime = runtime;
            this.clock = clock;
        }
    }

    private static final class RecordingObserver implements BoundedEventRuntime.EventObserver {
        final List<BoundedEventRuntime.EventEnvelope> events = new ArrayList<>();
        final List<BoundedEventRuntime.OverflowSignal> overflows = new ArrayList<>();
        final List<String> order = new ArrayList<>();
        boolean failNextEvent;
        BoundedEventRuntime reentrantRuntime;
        int closedCount;

        @Override
        public void onOverflow(BoundedEventRuntime.OverflowSignal overflow) {
            overflows.add(overflow);
            order.add("overflow:" + overflow.getFirstDroppedSequence()
                    + "-" + overflow.getLastDroppedSequence());
        }

        @Override
        public void onEvent(BoundedEventRuntime.EventEnvelope event) {
            if (failNextEvent) {
                failNextEvent = false;
                throw new IllegalStateException("synthetic observer failure");
            }
            if (reentrantRuntime != null) {
                reentrantRuntime.publish(publication(BoundedEventRuntime.TOPIC_TASK_STATE));
            }
            events.add(event);
            order.add("event:" + event.getSequence());
        }

        @Override
        public void onClosed(
                String subscriptionId,
                BoundedEventRuntime.CloseReason reason) {
            closedCount++;
        }
    }
}
