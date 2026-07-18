package com.centralbrain.runtime.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public final class InProcessDurableEventBrokerTest {
    private static final String OWNER_A = "a".repeat(64);
    private static final String OWNER_B = "b".repeat(64);
    private static final String SUBJECT_A = "c".repeat(64);
    private static final String SUBJECT_B = "d".repeat(64);
    private static final String IDENTITY_DIGEST = "e".repeat(64);
    private static final String POLICY_DIGEST = "f".repeat(64);

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void fixedTopicsEnforcePayloadTypeAndFilterKind() {
        try {
            new EventBroker.PublishRequest(
                    "publish.mismatch",
                    EventBroker.TASK_STATE_TOPIC,
                    new EventBroker.PolicyDecisionPayload(
                            SUBJECT_A,
                            EventBroker.PolicyDecision.ALLOW));
            fail("mismatched payload type must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("typed topic"));
        }

        try {
            new EventBroker.SubscriptionRequest(
                    "sub.invalid-filter",
                    EventBroker.TASK_STATE_TOPIC,
                    0,
                    new EventBroker.EventFilter(
                            EnumSet.of(EventBroker.EventKind.MODEL_HEALTHY),
                            Collections.emptySet()));
            fail("filter kind from another topic must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("filter kind"));
        }
        assertEquals(5, EventBroker.TASK_STATE_TOPIC.getSupportedKinds().size());
        assertEquals(EventBroker.TaskStatePayload.class,
                EventBroker.TASK_STATE_TOPIC.getPayloadClass());
    }

    @Test
    public void publicationAppendsBeforeConsumerNotification() {
        Harness harness = harness(4, 4, 2, 4);
        AtomicReference<EventBroker.EventPage> pageSeenByConsumer = new AtomicReference<>();
        EventBroker.SubscribeResult subscribed = harness.broker.subscribe(
                subscription("sub.append", 0, EventBroker.EventFilter.all()),
                event -> pageSeenByConsumer.set(harness.broker.replay(
                        new EventBroker.ReplayRequest(
                                EventBroker.TASK_STATE_TOPIC,
                                0,
                                4,
                                EventBroker.EventFilter.all()),
                        evidence(
                                OWNER_A,
                                EventBroker.Operation.REPLAY,
                                EventBroker.TASK_STATE_TOPIC,
                                harness.now.get()))),
                evidence(
                        OWNER_A,
                        EventBroker.Operation.SUBSCRIBE,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(EventBroker.SubscribeCode.SUBSCRIBED, subscribed.getCode());

        EventBroker.PublishResult result = harness.broker.publish(
                taskPublication("publish.append", SUBJECT_A, EventBroker.TaskState.RUNNING),
                evidence(
                        OWNER_B,
                        EventBroker.Operation.PUBLISH,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));

        assertEquals(EventBroker.PublishCode.PUBLISHED, result.getCode());
        assertEquals(1, result.getEvent().getCursor());
        assertEquals(1, result.getDeliveredCount());
        assertEquals(EventBroker.ReplayCode.OK, pageSeenByConsumer.get().getCode());
        assertEquals(1, pageSeenByConsumer.get().getEvents().size());
        assertEquals(result.getEvent().getEventDigest(),
                pageSeenByConsumer.get().getEvents().get(0).getEventDigest());
        assertTrue(harness.broker.snapshot().isAppendBeforeNotify());
    }

    @Test
    public void boundedReplayReportsGapFutureFilterAndNextCursor() {
        Harness harness = harness(3, 4, 2, 1);
        publish(harness, "publish.1", SUBJECT_A, EventBroker.TaskState.CREATED);
        publish(harness, "publish.2", SUBJECT_B, EventBroker.TaskState.RUNNING);
        publish(harness, "publish.3", SUBJECT_A, EventBroker.TaskState.WAITING);
        publish(harness, "publish.4", SUBJECT_A, EventBroker.TaskState.TERMINAL_SUCCESS);

        EventBroker.EventPage gap = replay(
                harness,
                0,
                EventBroker.EventFilter.all());
        assertEquals(EventBroker.ReplayCode.CURSOR_GAP, gap.getCode());
        assertEquals(2, gap.getEarliestAvailableCursor());
        assertTrue(gap.getEvents().isEmpty());

        EventBroker.EventFilter onlySubjectA = new EventBroker.EventFilter(
                Collections.emptySet(),
                Collections.singleton(SUBJECT_A));
        EventBroker.EventPage firstPage = replay(harness, 1, onlySubjectA);
        assertEquals(EventBroker.ReplayCode.OK, firstPage.getCode());
        assertEquals(1, firstPage.getEvents().size());
        assertEquals(3, firstPage.getNextCursor());
        assertTrue(firstPage.hasMore());

        EventBroker.EventPage secondPage = replay(
                harness,
                firstPage.getNextCursor(),
                onlySubjectA);
        assertEquals(1, secondPage.getEvents().size());
        assertEquals(4, secondPage.getNextCursor());
        assertFalse(secondPage.hasMore());

        EventBroker.EventPage future = replay(
                harness,
                5,
                EventBroker.EventFilter.all());
        assertEquals(EventBroker.ReplayCode.FUTURE_CURSOR, future.getCode());
        assertEquals(1, harness.broker.snapshot().getRetentionEvictionCount());
    }

    @Test
    public void identityPolicyAndEvidenceFailuresDoNotMutateBroker() {
        Harness harness = harness(4, 4, 2, 4);
        harness.decision.set(EventBroker.AccessDecision.DENIED);
        EventBroker.PublishResult denied = harness.broker.publish(
                taskPublication("publish.denied", SUBJECT_A, EventBroker.TaskState.CREATED),
                evidence(
                        OWNER_A,
                        EventBroker.Operation.PUBLISH,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(EventBroker.PublishCode.ACCESS_DENIED, denied.getCode());

        harness.decision.set(EventBroker.AccessDecision.UNAVAILABLE);
        EventBroker.PublishResult unavailable = harness.broker.publish(
                taskPublication("publish.unavailable", SUBJECT_A, EventBroker.TaskState.CREATED),
                evidence(
                        OWNER_A,
                        EventBroker.Operation.PUBLISH,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(EventBroker.PublishCode.AUTHORITY_UNAVAILABLE, unavailable.getCode());

        harness.decision.set(null);
        EventBroker.PublishResult nullDecision = harness.broker.publish(
                taskPublication("publish.null-authority", SUBJECT_A,
                        EventBroker.TaskState.CREATED),
                evidence(
                        OWNER_A,
                        EventBroker.Operation.PUBLISH,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(EventBroker.PublishCode.AUTHORITY_UNAVAILABLE, nullDecision.getCode());

        InProcessDurableEventBroker throwingAuthority =
                InProcessDurableEventBroker.createForContractTest(
                        new EventBroker.Limits(4, 4, 2, 4, 16),
                        harness.now::get,
                        () -> "unused-subscription-id",
                        (operation, topicId, evidence) -> {
                            throw new IllegalStateException("authority unavailable");
                        });
        EventBroker.PublishResult authorityFailure = throwingAuthority.publish(
                taskPublication("publish.throwing-authority", SUBJECT_A,
                        EventBroker.TaskState.CREATED),
                evidence(
                        OWNER_A,
                        EventBroker.Operation.PUBLISH,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(EventBroker.PublishCode.AUTHORITY_UNAVAILABLE,
                authorityFailure.getCode());

        harness.decision.set(EventBroker.AccessDecision.ALLOWED);
        EventBroker.PublishResult mismatched = harness.broker.publish(
                taskPublication("publish.mismatch", SUBJECT_A, EventBroker.TaskState.CREATED),
                evidence(
                        OWNER_A,
                        EventBroker.Operation.REPLAY,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(EventBroker.PublishCode.EVIDENCE_INVALID, mismatched.getCode());

        EventBroker.AccessEvidence expired = new EventBroker.AccessEvidence(
                OWNER_A,
                EventBroker.Operation.PUBLISH,
                EventBroker.TASK_STATE_TOPIC.getTopicId(),
                1,
                90,
                IDENTITY_DIGEST,
                POLICY_DIGEST);
        EventBroker.PublishResult expiredResult = harness.broker.publish(
                taskPublication("publish.expired", SUBJECT_A, EventBroker.TaskState.CREATED),
                expired);
        assertEquals(EventBroker.PublishCode.EVIDENCE_INVALID, expiredResult.getCode());
        assertEquals(0, harness.broker.snapshot().getPublishedCount());
        assertEquals(0, harness.broker.snapshot().getRetainedEventCount());
    }

    @Test
    public void subscriptionsAreIdempotentOwnerScopedAndCloseOnConsumerFailure() {
        Harness harness = harness(4, 2, 1, 4);
        EventBroker.SubscriptionRequest request = subscription(
                "sub.owner-a",
                0,
                EventBroker.EventFilter.all());
        EventBroker.SubscribeResult created = harness.broker.subscribe(
                request,
                event -> {
                    throw new IllegalStateException("expected callback failure");
                },
                evidence(
                        OWNER_A,
                        EventBroker.Operation.SUBSCRIBE,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        EventBroker.SubscribeResult replayed = harness.broker.subscribe(
                request,
                event -> { },
                evidence(
                        OWNER_A,
                        EventBroker.Operation.SUBSCRIBE,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(EventBroker.SubscribeCode.SUBSCRIBED, created.getCode());
        assertEquals(EventBroker.SubscribeCode.REPLAYED, replayed.getCode());
        assertEquals(created.getSubscription().getHandle().getSubscriptionId(),
                replayed.getSubscription().getHandle().getSubscriptionId());

        EventBroker.SubscribeResult conflict = harness.broker.subscribe(
                subscription(
                        "sub.owner-a",
                        0,
                        new EventBroker.EventFilter(
                                EnumSet.of(EventBroker.EventKind.TASK_RUNNING),
                                Collections.emptySet())),
                event -> { },
                evidence(
                        OWNER_A,
                        EventBroker.Operation.SUBSCRIBE,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(EventBroker.SubscribeCode.REQUEST_CONFLICT, conflict.getCode());

        EventBroker.PublishResult published = harness.broker.publish(
                taskPublication("publish.fail-consumer", SUBJECT_A, EventBroker.TaskState.RUNNING),
                evidence(
                        OWNER_B,
                        EventBroker.Operation.PUBLISH,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(0, published.getDeliveredCount());
        assertEquals(1, published.getClosedConsumerCount());
        assertEquals(0, harness.broker.snapshot().getActiveSubscriptionCount());
        assertEquals(1, harness.broker.snapshot().getCallbackFailureCount());

        EventBroker.CancelResult wrongOwner = harness.broker.cancel(
                created.getSubscription().getHandle(),
                evidence(
                        OWNER_B,
                        EventBroker.Operation.CANCEL,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(EventBroker.CancelCode.NOT_FOUND_OR_NOT_OWNER, wrongOwner.getCode());
        EventBroker.CancelResult alreadyClosed = harness.broker.cancel(
                created.getSubscription().getHandle(),
                evidence(
                        OWNER_A,
                        EventBroker.Operation.CANCEL,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(EventBroker.CancelCode.ALREADY_CANCELLED, alreadyClosed.getCode());
        assertEquals(EventSubscription.CloseReason.CALLBACK_FAILED,
                alreadyClosed.getSubscription().getCloseReason());
    }

    @Test
    public void productionMiddlewareAndHardwareBoundariesRemainClosed() {
        Harness harness = harness(4, 4, 2, 4);
        EventBroker.BrokerSnapshot snapshot = harness.broker.snapshot();

        assertTrue(snapshot.isProcessLocal());
        assertTrue(snapshot.isAppendBeforeNotify());
        assertFalse(snapshot.isDurablePersistenceWired());
        assertFalse(snapshot.isDdsTransportWired());
        assertFalse(snapshot.isProductionBrokerPublished());
        assertFalse(snapshot.isRuntimeWired());
        assertFalse(snapshot.isHardwareAccessed());
        EventBroker.PublishResult published = harness.broker.publish(
                taskPublication("publish.replay-boundary", SUBJECT_A,
                        EventBroker.TaskState.CREATED),
                evidence(
                        OWNER_A,
                        EventBroker.Operation.PUBLISH,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(EventBroker.PublishCode.PUBLISHED, published.getCode());
        assertFalse(published.getEvent().getPayloadDigest().isEmpty());
    }

    private static Harness harness(
            int retained,
            int globalSubscriptions,
            int ownerSubscriptions,
            int replayPage) {
        AtomicLong now = new AtomicLong(100);
        AtomicInteger ids = new AtomicInteger();
        AtomicReference<EventBroker.AccessDecision> decision =
                new AtomicReference<>(EventBroker.AccessDecision.ALLOWED);
        InProcessDurableEventBroker broker = InProcessDurableEventBroker.createForContractTest(
                new EventBroker.Limits(
                        retained,
                        globalSubscriptions,
                        ownerSubscriptions,
                        replayPage,
                        16),
                now::get,
                () -> "event-sub-" + ids.incrementAndGet(),
                (operation, topicId, evidence) -> decision.get());
        return new Harness(broker, now, decision);
    }

    private static EventBroker.PublishRequest<EventBroker.TaskStatePayload> taskPublication(
            String requestId,
            String subject,
            EventBroker.TaskState state) {
        return new EventBroker.PublishRequest<>(
                requestId,
                EventBroker.TASK_STATE_TOPIC,
                new EventBroker.TaskStatePayload(subject, state));
    }

    private static EventBroker.SubscriptionRequest subscription(
            String clientId,
            long afterCursor,
            EventBroker.EventFilter filter) {
        return new EventBroker.SubscriptionRequest(
                clientId,
                EventBroker.TASK_STATE_TOPIC,
                afterCursor,
                filter);
    }

    private static EventBroker.AccessEvidence evidence(
            String owner,
            EventBroker.Operation operation,
            EventBroker.Topic<?> topic,
            long now) {
        return new EventBroker.AccessEvidence(
                owner,
                operation,
                topic.getTopicId(),
                now,
                now + 30_000,
                IDENTITY_DIGEST,
                POLICY_DIGEST);
    }

    private static void publish(
            Harness harness,
            String requestId,
            String subject,
            EventBroker.TaskState state) {
        EventBroker.PublishResult result = harness.broker.publish(
                taskPublication(requestId, subject, state),
                evidence(
                        OWNER_A,
                        EventBroker.Operation.PUBLISH,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
        assertEquals(EventBroker.PublishCode.PUBLISHED, result.getCode());
        assertNotNull(result.getEvent());
    }

    private static EventBroker.EventPage replay(
            Harness harness,
            long afterCursor,
            EventBroker.EventFilter filter) {
        return harness.broker.replay(
                new EventBroker.ReplayRequest(
                        EventBroker.TASK_STATE_TOPIC,
                        afterCursor,
                        1,
                        filter),
                evidence(
                        OWNER_A,
                        EventBroker.Operation.REPLAY,
                        EventBroker.TASK_STATE_TOPIC,
                        harness.now.get()));
    }

    private static final class Harness {
        final InProcessDurableEventBroker broker;
        final AtomicLong now;
        final AtomicReference<EventBroker.AccessDecision> decision;

        Harness(
                InProcessDurableEventBroker broker,
                AtomicLong now,
                AtomicReference<EventBroker.AccessDecision> decision) {
            this.broker = broker;
            this.now = now;
            this.decision = decision;
        }
    }
}
