package com.centralbrain.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.centralbrain.sdk.event.ICentralBrainSessionEvents;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;

import java.util.Set;

import org.junit.Test;

public final class RuntimeContractV2Test {
    @Test
    public void aggregateIdentityPreservesFrozenWireVersionsAndBounds() {
        assertEquals(2, RuntimeContractV2.AGGREGATE_VERSION);
        assertEquals(
                ICentralBrainSessionRuntime.INTERFACE_VERSION,
                RuntimeContractV2.SESSION_WIRE_VERSION);
        assertEquals(
                ICentralBrainSessionEvents.INTERFACE_VERSION,
                RuntimeContractV2.EVENT_WIRE_VERSION);
        assertEquals(1, RuntimeContractV2.PLAN_DTO_VERSION);
        assertEquals(1, RuntimeContractV2.EFFECT_DTO_VERSION);
        assertEquals(50, RuntimeContractV2.SESSION_PAGE_ITEMS);
        assertEquals(100, RuntimeContractV2.EVENT_PAGE_ITEMS);
        assertEquals(256, RuntimeContractV2.CURSOR_CHARS);
        assertEquals(64, RuntimeContractV2.REPLAY_PAGE_LIMIT);
    }

    @Test
    public void stableFacadeErrorsRemainUniqueAndAliased() {
        Set<String> errors = Set.of(
                RuntimeContractV2.ERROR_NOT_CONNECTED,
                RuntimeContractV2.ERROR_PROTOCOL_MISMATCH,
                RuntimeContractV2.ERROR_TRANSPORT,
                RuntimeContractV2.ERROR_SUBSCRIPTION,
                RuntimeContractV2.ERROR_CLOSED);
        assertEquals(5, errors.size());
        assertEquals(RuntimeContractV2.ERROR_NOT_CONNECTED, ScenarioClient.ERROR_NOT_CONNECTED);
        assertEquals(
                RuntimeContractV2.ERROR_PROTOCOL_MISMATCH,
                ScenarioClient.ERROR_PROTOCOL_MISMATCH);
        assertEquals(RuntimeContractV2.ERROR_TRANSPORT, ScenarioClient.ERROR_TRANSPORT);
        assertEquals(RuntimeContractV2.ERROR_SUBSCRIPTION, ScenarioClient.ERROR_SUBSCRIPTION);
        assertEquals(RuntimeContractV2.ERROR_CLOSED, ScenarioClient.ERROR_CLOSED);
    }

    @Test
    public void eventCursorEvolutionIsFailClosedAndSeparatelyVersioned() {
        assertFalse(RuntimeContractV2.EVENT_V1_TERMINAL_RESUME_CURSOR);
        assertTrue(RuntimeContractV2.EVENT_V2_CURSOR_ACK_REQUIRED);
        assertFalse(RuntimeContractV2.EVENT_V2_INTERFACE_PUBLISHED);
        assertFalse(RuntimeContractV2.SCENARIO_EXECUTION_ENABLED);
    }
}
