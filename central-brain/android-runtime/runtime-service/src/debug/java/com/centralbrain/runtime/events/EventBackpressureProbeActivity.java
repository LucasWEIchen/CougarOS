package com.centralbrain.runtime.events;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.atomic.AtomicLong;

/** Debug-only API 33 ARM64 probe for the P6-W02 Event Backpressure/QoS contract. */
public final class EventBackpressureProbeActivity extends Activity {
    private static final String TAG = "CbEventQoS";
    private static final String OWNER =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PUBLISHER =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SUBJECT =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String KEY_A =
            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
    private static final String KEY_B =
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        runProbe();
        finish();
    }

    private void runProbe() {
        String nonce = getIntent().getStringExtra("nonce");
        if (nonce == null || nonce.isEmpty()) {
            nonce = "missing";
        }
        AtomicLong now = new AtomicLong(1_000);

        InProcessEventBackpressureQueue drop = queue(
                "drop",
                EventDeliveryQoS.OverflowPolicy.DROP_OLD,
                1,
                now);
        drop.offer(standard("drop.1", 1, EventDeliveryQoS.Priority.BACKGROUND,
                now.get() + 10_000, KEY_A));
        EventDeliveryQoS.OfferResult dropped = drop.offer(standard(
                "drop.2",
                2,
                EventDeliveryQoS.Priority.HIGH,
                now.get() + 10_000,
                KEY_B));

        InProcessEventBackpressureQueue coalesce = queue(
                "coalesce",
                EventDeliveryQoS.OverflowPolicy.COALESCE,
                1,
                now);
        coalesce.offer(standard("coalesce.1", 1, EventDeliveryQoS.Priority.NORMAL,
                now.get() + 10_000, KEY_A));
        EventDeliveryQoS.OfferResult coalesced = coalesce.offer(standard(
                "coalesce.2",
                2,
                EventDeliveryQoS.Priority.HIGH,
                now.get() + 10_000,
                KEY_A));

        InProcessEventBackpressureQueue reject = queue(
                "reject",
                EventDeliveryQoS.OverflowPolicy.REJECT,
                1,
                now);
        reject.offer(standard("reject.1", 1, EventDeliveryQoS.Priority.NORMAL,
                now.get() + 10_000, KEY_A));
        EventDeliveryQoS.OfferResult rejected = reject.offer(standard(
                "reject.2",
                2,
                EventDeliveryQoS.Priority.NORMAL,
                now.get() + 10_000,
                KEY_B));

        InProcessEventBackpressureQueue disconnect = queue(
                "disconnect",
                EventDeliveryQoS.OverflowPolicy.DISCONNECT,
                1,
                now);
        disconnect.offer(standard("disconnect.1", 1, EventDeliveryQoS.Priority.NORMAL,
                now.get() + 10_000, KEY_A));
        EventDeliveryQoS.OfferResult disconnected = disconnect.offer(standard(
                "disconnect.2",
                2,
                EventDeliveryQoS.Priority.NORMAL,
                now.get() + 10_000,
                KEY_B));

        InProcessEventBackpressureQueue critical = queue(
                "critical",
                EventDeliveryQoS.OverflowPolicy.DROP_OLD,
                1,
                now);
        critical.offer(critical("critical.1", 1, now.get() + 1));
        EventDeliveryQoS.OfferResult protectedResult = critical.offer(standard(
                "critical.2",
                2,
                EventDeliveryQoS.Priority.CRITICAL,
                now.get() + 10_000,
                KEY_A));
        now.addAndGet(2);
        EventDeliveryQoS.DrainResult criticalExpired = critical.drainOwned(
                OWNER,
                1,
                event -> { });

        InProcessEventBackpressureQueue failedConsumer = queue(
                "consumer-a",
                EventDeliveryQoS.OverflowPolicy.REJECT,
                1,
                now);
        InProcessEventBackpressureQueue healthyConsumer = queue(
                "consumer-b",
                EventDeliveryQoS.OverflowPolicy.REJECT,
                1,
                now);
        failedConsumer.offer(standard("consumer.a", 1, EventDeliveryQoS.Priority.NORMAL,
                now.get() + 10_000, KEY_A));
        healthyConsumer.offer(standard("consumer.b", 1, EventDeliveryQoS.Priority.NORMAL,
                now.get() + 10_000, KEY_A));
        EventDeliveryQoS.DrainResult failed = failedConsumer.drainOwned(
                OWNER,
                1,
                event -> {
                    throw new IllegalStateException("synthetic probe consumer failure");
                });
        EventDeliveryQoS.DrainResult healthy = healthyConsumer.drainOwned(
                OWNER,
                1,
                event -> { });

        boolean policies = dropped.getCode()
                == EventDeliveryQoS.OfferCode.DROPPED_OLD_AND_ENQUEUED
                && coalesced.getCode() == EventDeliveryQoS.OfferCode.COALESCED
                && rejected.getCode() == EventDeliveryQoS.OfferCode.REJECTED_QUEUE_FULL
                && disconnected.getCode()
                == EventDeliveryQoS.OfferCode.DISCONNECTED_REPLAY_REQUIRED;
        boolean criticalNoSilentDrop = protectedResult.getCode()
                == EventDeliveryQoS.OfferCode.REJECTED_QUEUE_FULL
                && protectedResult.getSnapshot().getQueuedCriticalCount() == 1
                && criticalExpired.getCode()
                == EventDeliveryQoS.DrainCode.DISCONNECTED_REPLAY_REQUIRED
                && criticalExpired.getSnapshot().isReplayRequired();
        boolean deadlinePriority = dropped.getDisplacedCursor() == 1
                && dropped.getAcceptedEvent().getCursor() == 2
                && criticalExpired.getExpiredCount() == 1;
        boolean consumerIsolation = failed.getCode()
                == EventDeliveryQoS.DrainCode.CONSUMER_FAILED
                && failed.getSnapshot().getQueuedCount() == 1
                && healthy.getCode() == EventDeliveryQoS.DrainCode.DRAINED
                && healthy.getSnapshot().getQueuedCount() == 0;
        boolean complete = policies
                && criticalNoSilentDrop
                && deadlinePriority
                && consumerIsolation;

        Log.i(TAG, String.join("\n",
                "nonce=" + nonce + " event_qos_probe_complete=" + complete,
                "event_qos_policies_verified=" + policies,
                "event_qos_critical_no_silent_drop_verified=" + criticalNoSilentDrop,
                "event_qos_deadline_priority_verified=" + deadlinePriority,
                "event_qos_consumer_isolation_verified=" + consumerIsolation,
                "event_qos_android13_arm64_verified=" + complete,
                "event_qos_process_local=true",
                "event_qos_broker_wired=false",
                "event_qos_durable_persistence_wired=false",
                "event_qos_production_middleware_wired=false",
                "graph_execution_enabled=false",
                "effect_dispatch_enabled=false",
                "vehicle_readback_accessed=false",
                "model_invoked=false",
                "npu_accessed=false",
                "network_accessed=false",
                "hardware_accessed=false",
                "production_ready=false",
                "target_hardware_validated=false"));
    }

    private static InProcessEventBackpressureQueue queue(
            String id,
            EventDeliveryQoS.OverflowPolicy policy,
            int capacity,
            AtomicLong now) {
        return InProcessEventBackpressureQueue.createForContractTest(
                new EventSubscription.Handle(
                        "event-qos-probe-" + id,
                        OWNER,
                        EventBroker.TASK_STATE_TOPIC.getTopicId()),
                0,
                new EventDeliveryQoS.QueueConfig(capacity, capacity, policy),
                now::get);
    }

    private static EventDeliveryQoS.DeliveryRequest standard(
            String requestId,
            long cursor,
            EventDeliveryQoS.Priority priority,
            long deadline,
            String key) {
        return new EventDeliveryQoS.DeliveryRequest(
                requestId,
                event(cursor),
                EventDeliveryQoS.DeliveryClass.STANDARD,
                priority,
                deadline,
                key);
    }

    private static EventDeliveryQoS.DeliveryRequest critical(
            String requestId,
            long cursor,
            long deadline) {
        return new EventDeliveryQoS.DeliveryRequest(
                requestId,
                event(cursor),
                EventDeliveryQoS.DeliveryClass.CRITICAL_ACTION_OBSERVATION,
                EventDeliveryQoS.Priority.CRITICAL,
                deadline,
                null);
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
}
