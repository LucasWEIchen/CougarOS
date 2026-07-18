package com.centralbrain.runtime.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class InProcessEventBackpressureQueueTest {
    private static final String OWNER_A = "a".repeat(64);
    private static final String OWNER_B = "b".repeat(64);
    private static final String PUBLISHER = "c".repeat(64);
    private static final String SUBJECT = "d".repeat(64);
    private static final String KEY_A = "e".repeat(64);
    private static final String KEY_B = "f".repeat(64);

    @Test
    public void dropOldUsesPriorityAndReportsDisplacedCursor() {
        Harness harness = harness(EventDeliveryQoS.OverflowPolicy.DROP_OLD, 2);
        assertEquals(EventDeliveryQoS.OfferCode.ENQUEUED,
                harness.offer(1, EventDeliveryQoS.Priority.BACKGROUND, KEY_A).getCode());
        assertEquals(EventDeliveryQoS.OfferCode.ENQUEUED,
                harness.offer(2, EventDeliveryQoS.Priority.HIGH, KEY_B).getCode());

        EventDeliveryQoS.OfferResult pressure = harness.offer(
                3,
                EventDeliveryQoS.Priority.NORMAL,
                KEY_A);
        assertEquals(EventDeliveryQoS.OfferCode.DROPPED_OLD_AND_ENQUEUED,
                pressure.getCode());
        assertEquals(1, pressure.getDisplacedCursor());
        assertEquals(1, pressure.getDisplacedCount());
        assertTrue(pressure.getSnapshot().isReplayRequired());
        assertEquals(1, pressure.getSnapshot().getDroppedOldCount());

        List<Long> delivered = new ArrayList<>();
        EventDeliveryQoS.DrainResult drained = harness.queue.drainOwned(
                OWNER_A,
                2,
                event -> delivered.add(event.getCursor()));
        assertEquals(EventDeliveryQoS.DrainCode.DRAINED, drained.getCode());
        assertEquals(List.of(2L, 3L), delivered);
    }

    @Test
    public void coalesceRequiresMatchingKeyAndNeverCoalescesCritical() {
        Harness harness = harness(EventDeliveryQoS.OverflowPolicy.COALESCE, 2);
        harness.offer(1, EventDeliveryQoS.Priority.NORMAL, KEY_A);
        harness.offer(2, EventDeliveryQoS.Priority.NORMAL, KEY_B);

        EventDeliveryQoS.OfferResult coalesced = harness.offer(
                3,
                EventDeliveryQoS.Priority.HIGH,
                KEY_A);
        assertEquals(EventDeliveryQoS.OfferCode.COALESCED, coalesced.getCode());
        assertEquals(1, coalesced.getDisplacedCursor());
        assertEquals(1, coalesced.getSnapshot().getCoalescedCount());

        EventDeliveryQoS.OfferResult critical = harness.offerCritical(
                4,
                EventDeliveryQoS.Priority.CRITICAL,
                harness.now.get() + 10_000);
        assertEquals(EventDeliveryQoS.OfferCode.REJECTED_QUEUE_FULL, critical.getCode());
        assertEquals(0, critical.getSnapshot().getQueuedCriticalCount());
        assertEquals(1, critical.getSnapshot().getRejectedCount());
        assertTrue(critical.getSnapshot().isCriticalNoSilentDrop());
    }

    @Test
    public void rejectAndDisconnectAreExplicitAndRequireReplay() {
        Harness rejecting = harness(EventDeliveryQoS.OverflowPolicy.REJECT, 1);
        rejecting.offer(1, EventDeliveryQoS.Priority.NORMAL, KEY_A);
        EventDeliveryQoS.OfferResult rejected = rejecting.offer(
                2,
                EventDeliveryQoS.Priority.NORMAL,
                KEY_B);
        assertEquals(EventDeliveryQoS.OfferCode.REJECTED_QUEUE_FULL, rejected.getCode());
        assertEquals(0, rejected.getReplayAfterCursor());
        assertTrue(rejected.getSnapshot().isReplayRequired());
        assertEquals(1, rejected.getSnapshot().getQueuedCount());

        Harness disconnecting = harness(EventDeliveryQoS.OverflowPolicy.DISCONNECT, 1);
        disconnecting.offer(1, EventDeliveryQoS.Priority.NORMAL, KEY_A);
        EventDeliveryQoS.OfferResult disconnected = disconnecting.offer(
                2,
                EventDeliveryQoS.Priority.NORMAL,
                KEY_B);
        assertEquals(EventDeliveryQoS.OfferCode.DISCONNECTED_REPLAY_REQUIRED,
                disconnected.getCode());
        assertEquals(1, disconnected.getDisplacedCount());
        assertEquals(EventDeliveryQoS.DisconnectReason.QUEUE_FULL,
                disconnected.getSnapshot().getDisconnectReason());
        assertEquals(0, disconnected.getSnapshot().getQueuedCount());
        assertEquals(EventDeliveryQoS.DrainCode.SUBSCRIPTION_DISCONNECTED,
                disconnecting.queue.drainOwned(OWNER_A, 1, event -> { }).getCode());
    }

    @Test
    public void criticalEventsNeverSilentlyDropOrExpire() {
        Harness protectedQueue = harness(EventDeliveryQoS.OverflowPolicy.DROP_OLD, 1);
        protectedQueue.offerCritical(
                1,
                EventDeliveryQoS.Priority.CRITICAL,
                protectedQueue.now.get() + 10_000);
        EventDeliveryQoS.OfferResult cannotDisplaceCritical = protectedQueue.offer(
                2,
                EventDeliveryQoS.Priority.CRITICAL,
                KEY_A);
        assertEquals(EventDeliveryQoS.OfferCode.REJECTED_QUEUE_FULL,
                cannotDisplaceCritical.getCode());
        assertEquals(1, cannotDisplaceCritical.getSnapshot().getQueuedCriticalCount());
        assertEquals(0, cannotDisplaceCritical.getSnapshot().getDroppedOldCount());

        Harness expiring = harness(EventDeliveryQoS.OverflowPolicy.REJECT, 2);
        expiring.offerCritical(
                1,
                EventDeliveryQoS.Priority.CRITICAL,
                expiring.now.get() + 1);
        expiring.now.addAndGet(2);
        EventDeliveryQoS.DrainResult expired = expiring.queue.drainOwned(
                OWNER_A,
                1,
                event -> { });
        assertEquals(EventDeliveryQoS.DrainCode.DISCONNECTED_REPLAY_REQUIRED,
                expired.getCode());
        assertEquals(1, expired.getExpiredCount());
        assertEquals(1, expired.getDiscardedCount());
        assertEquals(EventDeliveryQoS.DisconnectReason.CRITICAL_DEADLINE_EXPIRED,
                expired.getSnapshot().getDisconnectReason());
        assertTrue(expired.getSnapshot().isReplayRequired());
    }

    @Test
    public void deadlineIdempotencyOwnershipAndConsumerFailureFailClosed() {
        Harness harness = harness(EventDeliveryQoS.OverflowPolicy.REJECT, 2);
        EventDeliveryQoS.DeliveryRequest expiredRequest = harness.request(
                "request.expired",
                1,
                EventDeliveryQoS.DeliveryClass.STANDARD,
                EventDeliveryQoS.Priority.NORMAL,
                harness.now.get(),
                KEY_A);
        assertEquals(EventDeliveryQoS.OfferCode.REJECTED_DEADLINE,
                harness.queue.offer(expiredRequest).getCode());
        assertEquals(EventDeliveryQoS.OfferCode.REPLAYED,
                harness.queue.offer(expiredRequest).getCode());
        assertEquals(EventDeliveryQoS.OfferCode.REQUEST_CONFLICT,
                harness.queue.offer(harness.request(
                        "request.expired",
                        2,
                        EventDeliveryQoS.DeliveryClass.STANDARD,
                        EventDeliveryQoS.Priority.NORMAL,
                        harness.now.get() + 10_000,
                        KEY_A)).getCode());

        harness.offer(3, EventDeliveryQoS.Priority.NORMAL, KEY_A);
        assertEquals(EventDeliveryQoS.DrainCode.NOT_FOUND_OR_NOT_OWNER,
                harness.queue.drainOwned(OWNER_B, 1, event -> { }).getCode());
        assertNull(harness.queue.snapshotOwned(OWNER_B));

        EventDeliveryQoS.DrainResult failed = harness.queue.drainOwned(
                OWNER_A,
                1,
                event -> {
                    throw new IllegalStateException("synthetic consumer failure");
                });
        assertEquals(EventDeliveryQoS.DrainCode.CONSUMER_FAILED, failed.getCode());
        assertEquals(1, failed.getSnapshot().getQueuedCount());
        assertEquals(1, failed.getSnapshot().getConsumerFailureCount());
        assertEquals(EventDeliveryQoS.DrainCode.DRAINED,
                harness.queue.drainOwned(OWNER_A, 1, event -> { }).getCode());
    }

    @Test
    public void consumerQueuesAreIsolatedAndProductionBoundariesRemainClosed() {
        Harness disconnected = harness(EventDeliveryQoS.OverflowPolicy.DISCONNECT, 1);
        Harness healthy = harness(EventDeliveryQoS.OverflowPolicy.REJECT, 2);
        disconnected.offer(1, EventDeliveryQoS.Priority.NORMAL, KEY_A);
        disconnected.offer(2, EventDeliveryQoS.Priority.NORMAL, KEY_B);
        healthy.offer(1, EventDeliveryQoS.Priority.NORMAL, KEY_A);

        EventDeliveryQoS.QueueSnapshot healthySnapshot = healthy.queue.snapshotOwned(OWNER_A);
        assertEquals(1, healthySnapshot.getQueuedCount());
        assertEquals(EventDeliveryQoS.DisconnectReason.NONE,
                healthySnapshot.getDisconnectReason());
        assertTrue(healthySnapshot.isCriticalNoSilentDrop());
        assertTrue(healthySnapshot.isProcessLocal());
        assertFalse(healthySnapshot.isBrokerWired());
        assertFalse(healthySnapshot.isDurablePersistenceWired());
        assertFalse(healthySnapshot.isProductionMiddlewareWired());
        assertFalse(healthySnapshot.isHardwareAccessed());
    }

    private static Harness harness(
            EventDeliveryQoS.OverflowPolicy policy,
            int capacity) {
        AtomicLong now = new AtomicLong(1_000);
        EventSubscription.Handle handle = new EventSubscription.Handle(
                "event-qos-sub-" + policy.name().toLowerCase(),
                OWNER_A,
                EventBroker.TASK_STATE_TOPIC.getTopicId());
        InProcessEventBackpressureQueue queue =
                InProcessEventBackpressureQueue.createForContractTest(
                        handle,
                        0,
                        new EventDeliveryQoS.QueueConfig(capacity, capacity, policy),
                        now::get);
        return new Harness(queue, now);
    }

    private static EventBroker.EventRecord event(long cursor) {
        return new EventBroker.EventRecord(
                EventBroker.TASK_STATE_TOPIC,
                cursor,
                1_000 + cursor,
                PUBLISHER,
                new EventBroker.TaskStatePayload(
                        SUBJECT,
                        EventBroker.TaskState.RUNNING));
    }

    private static final class Harness {
        final InProcessEventBackpressureQueue queue;
        final AtomicLong now;

        Harness(InProcessEventBackpressureQueue queue, AtomicLong now) {
            this.queue = queue;
            this.now = now;
        }

        EventDeliveryQoS.OfferResult offer(
                long cursor,
                EventDeliveryQoS.Priority priority,
                String key) {
            return queue.offer(request(
                    "request." + cursor,
                    cursor,
                    EventDeliveryQoS.DeliveryClass.STANDARD,
                    priority,
                    now.get() + 10_000,
                    key));
        }

        EventDeliveryQoS.OfferResult offerCritical(
                long cursor,
                EventDeliveryQoS.Priority priority,
                long deadline) {
            return queue.offer(request(
                    "request.critical." + cursor,
                    cursor,
                    EventDeliveryQoS.DeliveryClass.CRITICAL_ACTION_OBSERVATION,
                    priority,
                    deadline,
                    null));
        }

        EventDeliveryQoS.DeliveryRequest request(
                String requestId,
                long cursor,
                EventDeliveryQoS.DeliveryClass deliveryClass,
                EventDeliveryQoS.Priority priority,
                long deadline,
                String key) {
            return new EventDeliveryQoS.DeliveryRequest(
                    requestId,
                    event(cursor),
                    deliveryClass,
                    priority,
                    deadline,
                    key);
        }
    }
}
