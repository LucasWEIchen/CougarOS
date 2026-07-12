package com.centralbrain.runtime.events;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class BoundedEventRuntimeProbeActivity extends Activity {
    private static final String TAG = "CbEventRuntimeProbe";
    private static final String OWNER_A = repeat("a", 64);
    private static final String OWNER_B = repeat("b", 64);
    private static final String DIGEST = repeat("c", 64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            Harness primary = harness(2, 4, 2, 2);
            BoundedEventRuntime.PublishResult first = primary.runtime.publish(
                    publication(BoundedEventRuntime.TOPIC_TASK_STATE));
            primary.runtime.publish(publication(BoundedEventRuntime.TOPIC_TASK_STATE));
            primary.runtime.publish(publication(BoundedEventRuntime.TOPIC_TASK_STATE));
            BoundedEventRuntime.PublishResult fourth = primary.runtime.publish(
                    publication(BoundedEventRuntime.TOPIC_TASK_STATE));
            boolean monotonicVerified = first.getEvent().getSequence() == 1
                    && fourth.getEvent().getSequence() == 4
                    && primary.runtime.snapshot().getEarliestRetainedSequence() == 3
                    && primary.runtime.snapshot().getRetentionEvictionCount() == 2;
            boolean trustedTopicVerified = primary.runtime.publish(
                    publication("unknown.topic")).getOutcome()
                    == BoundedEventRuntime.PublishOutcome.UNKNOWN_TOPIC
                    && BoundedEventRuntime.trustedTopics().size() == 3;

            RecordingObserver observer = new RecordingObserver();
            RecordingObserver replayObserver = new RecordingObserver();
            BoundedEventRuntime.TrustedSubscription request = subscription(
                    "probe-primary",
                    OWNER_A,
                    BoundedEventRuntime.TOPIC_TASK_STATE,
                    0,
                    1);
            BoundedEventRuntime.SubscribeResult created = primary.runtime.subscribe(
                    request,
                    observer);
            BoundedEventRuntime.SubscribeResult replay = primary.runtime.subscribe(
                    request,
                    replayObserver);
            String subscriptionId = created.getSubscription().getSubscriptionId();
            boolean idempotencyVerified = created.getOutcome()
                    == BoundedEventRuntime.SubscribeOutcome.CREATED
                    && replay.getOutcome() == BoundedEventRuntime.SubscribeOutcome.REPLAYED
                    && subscriptionId.equals(replay.getSubscription().getSubscriptionId())
                    && primary.runtime.subscribe(
                            subscription(
                                    "probe-primary",
                                    OWNER_A,
                                    BoundedEventRuntime.TOPIC_TASK_STATE,
                                    0,
                                    2),
                            replayObserver).getOutcome()
                            == BoundedEventRuntime.SubscribeOutcome.CONFLICT;
            boolean ownerIsolationVerified = primary.runtime.dispatchOwned(
                    subscriptionId,
                    OWNER_B,
                    8).getOutcome()
                    == BoundedEventRuntime.DispatchOutcome.NOT_FOUND_OR_NOT_OWNER;
            BoundedEventRuntime.DispatchResult dispatch = primary.runtime.dispatchOwned(
                    subscriptionId,
                    OWNER_A,
                    8);
            boolean overflowVerified = dispatch.getOutcome()
                    == BoundedEventRuntime.DispatchOutcome.DELIVERED
                    && dispatch.isOverflowDelivered()
                    && observer.order.size() == 2
                    && "overflow:1-3".equals(observer.order.get(0))
                    && "event:4".equals(observer.order.get(1))
                    && observer.overflows.get(0).getDroppedCount() == 3;
            boolean cursorReplayVerified = observer.events.size() == 1
                    && observer.events.get(0).getSequence() == 4
                    && replayObserver.events.isEmpty()
                    && dispatch.getSubscription().getLastDeliveredSequence() == 4;

            boolean cancelVerified = primary.runtime.cancelOwned(
                    subscriptionId,
                    OWNER_B).getOutcome()
                    == BoundedEventRuntime.CancelOutcome.NOT_FOUND_OR_NOT_OWNER
                    && primary.runtime.cancelOwned(
                            subscriptionId,
                            OWNER_A).getOutcome()
                            == BoundedEventRuntime.CancelOutcome.CANCELLED
                    && primary.runtime.cancelOwned(
                            subscriptionId,
                            OWNER_A).getOutcome()
                            == BoundedEventRuntime.CancelOutcome.ALREADY_CANCELLED
                    && observer.closedCount == 1;

            Harness retry = harness(4, 4, 2, 2);
            retry.runtime.publish(publication(BoundedEventRuntime.TOPIC_MODEL_HEALTH));
            RecordingObserver retryObserver = new RecordingObserver();
            retryObserver.failNextEvent = true;
            BoundedEventRuntime.SubscribeResult retryCreated = retry.runtime.subscribe(
                    subscription(
                            "probe-retry",
                            OWNER_A,
                            BoundedEventRuntime.TOPIC_MODEL_HEALTH,
                            0,
                            2),
                    retryObserver);
            BoundedEventRuntime.DispatchResult failed = retry.runtime.dispatchOwned(
                    retryCreated.getSubscription().getSubscriptionId(),
                    OWNER_A,
                    2);
            BoundedEventRuntime.DispatchResult retried = retry.runtime.dispatchOwned(
                    retryCreated.getSubscription().getSubscriptionId(),
                    OWNER_A,
                    2);
            boolean observerRetryVerified = failed.getOutcome()
                    == BoundedEventRuntime.DispatchOutcome.OBSERVER_FAILED
                    && failed.getSubscription().getQueuedEventCount() == 1
                    && retried.getOutcome() == BoundedEventRuntime.DispatchOutcome.DELIVERED
                    && retryObserver.events.size() == 1
                    && retry.runtime.snapshot().getObserverFailureCount() == 1;

            boolean processBoundaryVerified = !primary.runtime.snapshot()
                    .isCursorPersistenceWired()
                    && !primary.runtime.snapshot().isProductionBrokerWired()
                    && !primary.runtime.snapshot().isHardwareAccessed();
            boolean contractVerified = monotonicVerified
                    && trustedTopicVerified
                    && idempotencyVerified
                    && ownerIsolationVerified
                    && overflowVerified
                    && cursorReplayVerified
                    && cancelVerified
                    && observerRetryVerified
                    && processBoundaryVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " event_runtime_probe_complete=true"
                    + " event_runtime_contract_verified=" + contractVerified
                    + " event_trusted_topic_verified=" + trustedTopicVerified
                    + " event_monotonic_sequence_verified=" + monotonicVerified
                    + " event_cursor_replay_verified=" + cursorReplayVerified
                    + " event_overflow_before_delivery_verified=" + overflowVerified
                    + " event_owner_isolation_verified=" + ownerIsolationVerified
                    + " event_subscription_idempotency_verified=" + idempotencyVerified
                    + " event_cancel_idempotency_verified=" + cancelVerified
                    + " event_observer_retry_verified=" + observerRetryVerified
                    + " event_runtime_process_only=" + processBoundaryVerified
                    + " event_cursor_persistence_wired=false"
                    + " event_broker_production_wired=false"
                    + " event_callback_binder_wired=false"
                    + " dds_runtime_active=false"
                    + " network_transport_active=false"
                    + " vehicle_bus_accessed=false"
                    + " service_dispatch_triggered=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " event_runtime_probe_complete=false"
                    + " event_runtime_process_only=true"
                    + " event_broker_production_wired=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static Harness harness(
            int retention,
            int globalSubscriptions,
            int ownerSubscriptions,
            int maxQueue) {
        AtomicLong clock = new AtomicLong(1_000);
        AtomicInteger ids = new AtomicInteger();
        return new Harness(BoundedEventRuntime.createForContractTest(
                new BoundedEventRuntime.Limits(
                        retention,
                        globalSubscriptions,
                        ownerSubscriptions,
                        maxQueue,
                        8),
                clock::incrementAndGet,
                () -> "probe-event-sub-" + ids.incrementAndGet()));
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

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }

    private static final class Harness {
        final BoundedEventRuntime runtime;

        Harness(BoundedEventRuntime runtime) {
            this.runtime = runtime;
        }
    }

    private static final class RecordingObserver implements BoundedEventRuntime.EventObserver {
        final List<BoundedEventRuntime.EventEnvelope> events = new ArrayList<>();
        final List<BoundedEventRuntime.OverflowSignal> overflows = new ArrayList<>();
        final List<String> order = new ArrayList<>();
        boolean failNextEvent;
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
