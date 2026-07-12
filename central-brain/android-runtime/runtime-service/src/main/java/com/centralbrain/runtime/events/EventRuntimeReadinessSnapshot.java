package com.centralbrain.runtime.events;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable production visibility for the currently blocked Event runtime. */
public final class EventRuntimeReadinessSnapshot {
    public enum Blocker {
        DURABLE_PUBLISHER_SEQUENCE_MISSING,
        EVENT_RUNTIME_NOT_WIRED,
        EVENT_REPOSITORY_NOT_WIRED,
        CALLBACK_BINDER_NOT_DEFINED,
        BROKER_NOT_CONFIGURED,
        MIDDLEWARE_CHAIN_NOT_WIRED
    }

    private static final EventRuntimeReadinessSnapshot CURRENT = createCurrent();

    private final List<Blocker> blockers;

    private EventRuntimeReadinessSnapshot(List<Blocker> blockers) {
        this.blockers = Collections.unmodifiableList(blockers);
    }

    public static EventRuntimeReadinessSnapshot current() {
        return CURRENT;
    }

    private static EventRuntimeReadinessSnapshot createCurrent() {
        Set<String> trustedTopics = BoundedEventRuntime.trustedTopics();
        if (trustedTopics.size() != 3
                || !trustedTopics.contains(BoundedEventRuntime.TOPIC_TASK_STATE)
                || !trustedTopics.contains(BoundedEventRuntime.TOPIC_POLICY_DECISION)
                || !trustedTopics.contains(BoundedEventRuntime.TOPIC_MODEL_HEALTH)) {
            throw new IllegalStateException("trusted Event topic baseline is inconsistent");
        }
        return new EventRuntimeReadinessSnapshot(Arrays.asList(
                Blocker.DURABLE_PUBLISHER_SEQUENCE_MISSING,
                Blocker.EVENT_RUNTIME_NOT_WIRED,
                Blocker.EVENT_REPOSITORY_NOT_WIRED,
                Blocker.CALLBACK_BINDER_NOT_DEFINED,
                Blocker.BROKER_NOT_CONFIGURED,
                Blocker.MIDDLEWARE_CHAIN_NOT_WIRED));
    }

    public boolean isActivationAllowed() {
        return false;
    }

    public boolean isBoundedEventRuntimeImplementationAvailable() {
        return true;
    }

    public boolean isEventCursorSchemaReady() {
        return true;
    }

    public boolean isEventRepositoryImplementationAvailable() {
        return true;
    }

    public boolean isDurableEventSourceAvailable() {
        return false;
    }

    public boolean isEventRuntimeProductionWired() {
        return false;
    }

    public boolean isEventRepositoryProductionWired() {
        return false;
    }

    public boolean isEventCursorPersistenceWired() {
        return false;
    }

    public boolean isCallbackBinderWired() {
        return false;
    }

    public boolean isProductionBrokerWired() {
        return false;
    }

    public boolean isMiddlewareChainWired() {
        return false;
    }

    public boolean isRawEventPayloadPersisted() {
        return false;
    }

    public boolean isDdsRuntimeActive() {
        return false;
    }

    public boolean isNetworkTransportActive() {
        return false;
    }

    public boolean isVehicleBusAccessed() {
        return false;
    }

    public boolean isHardwareAccessed() {
        return false;
    }

    public int getTrustedTopicCount() {
        return BoundedEventRuntime.trustedTopics().size();
    }

    public List<Blocker> getBlockers() {
        return blockers;
    }

    public String getBlockersCsv() {
        return blockers.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public String diagnosticDetail() {
        return "event_runtime_activation_allowed=false"
                + ";bounded_event_runtime_implementation_available=true"
                + ";event_cursor_schema_ready=true"
                + ";event_repository_implementation_available=true"
                + ";trusted_event_topic_count=" + getTrustedTopicCount()
                + ";durable_event_source_available=false"
                + ";event_runtime_production_wired=false"
                + ";event_cursor_repository_production_wired=false"
                + ";event_cursor_persistence_wired=false"
                + ";event_callback_binder_wired=false"
                + ";event_broker_production_wired=false"
                + ";event_middleware_chain_wired=false"
                + ";raw_event_payload_persisted=false"
                + ";dds_runtime_active=false"
                + ";network_transport_active=false"
                + ";vehicle_bus_accessed=false"
                + ";blockers=" + getBlockersCsv()
                + ";service_dispatch_triggered=false"
                + ";hardware_accessed=false";
    }
}
