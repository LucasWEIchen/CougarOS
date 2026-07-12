package com.centralbrain.runtime.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

public final class EventRuntimeReadinessSnapshotTest {
    @Test
    public void currentSnapshotIsImmutableAndFailClosed() {
        EventRuntimeReadinessSnapshot current = EventRuntimeReadinessSnapshot.current();
        assertSame(current, EventRuntimeReadinessSnapshot.current());
        assertFalse(current.isActivationAllowed());
        assertTrue(current.isBoundedEventRuntimeImplementationAvailable());
        assertTrue(current.isEventCursorSchemaReady());
        assertTrue(current.isEventRepositoryImplementationAvailable());
        assertEquals(3, current.getTrustedTopicCount());
        assertFalse(current.isDurableEventSourceAvailable());
        assertFalse(current.isEventRuntimeProductionWired());
        assertFalse(current.isEventRepositoryProductionWired());
        assertFalse(current.isEventCursorPersistenceWired());
        assertFalse(current.isCallbackBinderWired());
        assertFalse(current.isProductionBrokerWired());
        assertFalse(current.isMiddlewareChainWired());
        assertFalse(current.isRawEventPayloadPersisted());
        assertFalse(current.isDdsRuntimeActive());
        assertFalse(current.isNetworkTransportActive());
        assertFalse(current.isVehicleBusAccessed());
        assertFalse(current.isHardwareAccessed());
    }

    @Test
    public void blockersRemainOrderedAndImmutable() {
        EventRuntimeReadinessSnapshot current = EventRuntimeReadinessSnapshot.current();
        assertEquals(Arrays.asList(
                        EventRuntimeReadinessSnapshot.Blocker
                                .DURABLE_PUBLISHER_SEQUENCE_MISSING,
                        EventRuntimeReadinessSnapshot.Blocker.EVENT_RUNTIME_NOT_WIRED,
                        EventRuntimeReadinessSnapshot.Blocker.EVENT_REPOSITORY_NOT_WIRED,
                        EventRuntimeReadinessSnapshot.Blocker.CALLBACK_BINDER_NOT_DEFINED,
                        EventRuntimeReadinessSnapshot.Blocker.BROKER_NOT_CONFIGURED,
                        EventRuntimeReadinessSnapshot.Blocker.MIDDLEWARE_CHAIN_NOT_WIRED),
                current.getBlockers());
        try {
            current.getBlockers().clear();
            fail("blockers must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void diagnosticDetailSeparatesAvailabilityFromActivation() {
        String detail = EventRuntimeReadinessSnapshot.current().diagnosticDetail();
        assertTrue(detail.contains("event_runtime_activation_allowed=false"));
        assertTrue(detail.contains("bounded_event_runtime_implementation_available=true"));
        assertTrue(detail.contains("event_cursor_schema_ready=true"));
        assertTrue(detail.contains("event_repository_implementation_available=true"));
        assertTrue(detail.contains("trusted_event_topic_count=3"));
        assertTrue(detail.contains("durable_event_source_available=false"));
        assertTrue(detail.contains("event_cursor_repository_production_wired=false"));
        assertTrue(detail.contains("event_cursor_persistence_wired=false"));
        assertTrue(detail.contains("event_broker_production_wired=false"));
        assertTrue(detail.contains("DURABLE_PUBLISHER_SEQUENCE_MISSING"));
        assertTrue(detail.contains("MIDDLEWARE_CHAIN_NOT_WIRED"));
        assertTrue(detail.contains("service_dispatch_triggered=false"));
        assertTrue(detail.contains("hardware_accessed=false"));
    }
}
