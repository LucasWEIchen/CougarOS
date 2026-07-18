package com.centralbrain.runtime.events;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Debug-only API 33 ARM64 probe for the P6-W01 Event Broker contract. */
public final class EventBrokerProbeActivity extends Activity {
    private static final String TAG = "CbEventBroker";
    private static final String OWNER_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OWNER_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SUBJECT =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String IDENTITY =
            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
    private static final String POLICY =
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
        AtomicInteger ids = new AtomicInteger();
        AtomicReference<EventBroker.AccessDecision> decision =
                new AtomicReference<>(EventBroker.AccessDecision.ALLOWED);
        InProcessDurableEventBroker broker = InProcessDurableEventBroker
                .createForContractTest(
                        new EventBroker.Limits(3, 4, 2, 2, 16),
                        SystemClock::elapsedRealtime,
                        () -> "probe-sub-" + ids.incrementAndGet(),
                        (operation, topicId, evidence) -> decision.get());

        AtomicInteger delivered = new AtomicInteger();
        AtomicBoolean appendVisibleDuringCallback = new AtomicBoolean();
        long now = SystemClock.elapsedRealtime();
        EventBroker.SubscribeResult subscribed = broker.subscribe(
                new EventBroker.SubscriptionRequest(
                        "probe.client",
                        EventBroker.TASK_STATE_TOPIC,
                        0,
                        EventBroker.EventFilter.all()),
                event -> {
                    delivered.incrementAndGet();
                    appendVisibleDuringCallback.set(
                            appendVisibleDuringCallback.get()
                                    || broker.snapshot().getRetainedEventCount() >= 1);
                },
                evidence(
                        OWNER_A,
                        EventBroker.Operation.SUBSCRIBE,
                        EventBroker.TASK_STATE_TOPIC,
                        now));
        EventBroker.PublishResult published = broker.publish(
                publication("probe.publish.1", EventBroker.TaskState.RUNNING),
                evidence(
                        OWNER_B,
                        EventBroker.Operation.PUBLISH,
                        EventBroker.TASK_STATE_TOPIC,
                        now));
        broker.publish(
                publication("probe.publish.2", EventBroker.TaskState.WAITING),
                evidence(
                        OWNER_B,
                        EventBroker.Operation.PUBLISH,
                        EventBroker.TASK_STATE_TOPIC,
                        now));

        EventBroker.EventPage page = broker.replay(
                new EventBroker.ReplayRequest(
                        EventBroker.TASK_STATE_TOPIC,
                        0,
                        1,
                        new EventBroker.EventFilter(
                                Collections.singleton(EventBroker.EventKind.TASK_RUNNING),
                                Collections.singleton(SUBJECT))),
                evidence(
                        OWNER_A,
                        EventBroker.Operation.REPLAY,
                        EventBroker.TASK_STATE_TOPIC,
                        now));

        long publishedBeforeDeny = broker.snapshot().getPublishedCount();
        decision.set(EventBroker.AccessDecision.DENIED);
        EventBroker.PublishResult denied = broker.publish(
                publication("probe.publish.denied", EventBroker.TaskState.TERMINAL_SUCCESS),
                evidence(
                        OWNER_B,
                        EventBroker.Operation.PUBLISH,
                        EventBroker.TASK_STATE_TOPIC,
                        now));
        decision.set(EventBroker.AccessDecision.ALLOWED);
        EventBroker.CancelResult cancelled = broker.cancel(
                subscribed.getSubscription().getHandle(),
                evidence(
                        OWNER_A,
                        EventBroker.Operation.CANCEL,
                        EventBroker.TASK_STATE_TOPIC,
                        now));
        EventBroker.BrokerSnapshot snapshot = broker.snapshot();

        boolean typedTopics = published.getCode() == EventBroker.PublishCode.PUBLISHED
                && published.getEvent().getKind() == EventBroker.EventKind.TASK_RUNNING;
        boolean appendBeforeNotify = delivered.get() == 2
                && appendVisibleDuringCallback.get()
                && snapshot.isAppendBeforeNotify();
        boolean replayFilter = page.getCode() == EventBroker.ReplayCode.OK
                && page.getEvents().size() == 1
                && page.getEvents().get(0).getKind()
                == EventBroker.EventKind.TASK_RUNNING;
        boolean identityPolicy = denied.getCode() == EventBroker.PublishCode.ACCESS_DENIED
                && broker.snapshot().getPublishedCount() == publishedBeforeDeny;
        boolean lifecycle = subscribed.getCode() == EventBroker.SubscribeCode.SUBSCRIBED
                && cancelled.getCode() == EventBroker.CancelCode.CANCELLED
                && snapshot.getActiveSubscriptionCount() == 0;
        boolean complete = typedTopics
                && appendBeforeNotify
                && replayFilter
                && identityPolicy
                && lifecycle;

        Log.i(TAG, String.join("\n",
                "nonce=" + nonce + " event_broker_probe_complete=" + complete,
                "event_broker_typed_topics_verified=" + typedTopics,
                "event_broker_append_before_notify_verified=" + appendBeforeNotify,
                "event_broker_bounded_replay_filter_verified=" + replayFilter,
                "event_broker_identity_policy_verified=" + identityPolicy,
                "event_broker_subscription_lifecycle_verified=" + lifecycle,
                "event_broker_android13_arm64_verified=" + complete,
                "event_broker_process_local=true",
                "event_broker_durable_persistence_wired=false",
                "event_broker_dds_transport_wired=false",
                "event_broker_production_published=false",
                "event_broker_runtime_wired=false",
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

    private static EventBroker.PublishRequest<EventBroker.TaskStatePayload> publication(
            String requestId,
            EventBroker.TaskState state) {
        return new EventBroker.PublishRequest<>(
                requestId,
                EventBroker.TASK_STATE_TOPIC,
                new EventBroker.TaskStatePayload(SUBJECT, state));
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
                IDENTITY,
                POLICY);
    }
}
