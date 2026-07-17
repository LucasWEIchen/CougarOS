package com.centralbrain.runtime.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.sdk.event.EventContract;
import com.centralbrain.sdk.event.EventPage;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionPage;
import com.centralbrain.sdk.session.SessionQuery;
import com.centralbrain.sdk.session.SessionRequest;

import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.Test;

public final class TransientSessionRegistryTest {
    private static final long NOW = 1_750_000_000_000L;
    private static final String OWNER_A = "a".repeat(64);
    private static final String OWNER_B = "b".repeat(64);

    @Test
    public void openIsIdempotentOwnerScopedAndDoesNotExposeUtterance() {
        TransientSessionRegistry registry = registry(4);
        SessionRequest request = request("8d595630-2255-4f4d-ac0f-26a20ee96f29", "private words");
        SessionHandle first = registry.openOwned(OWNER_A, request);
        SessionHandle replay = registry.openOwned(OWNER_A, request);

        assertEquals(first.sessionId, replay.sessionId);
        assertEquals(1, registry.size());
        assertFalse(registry.findOwned(OWNER_A, first).summary.contains("private words"));
        assertNull(registry.findOwned(OWNER_B, first));

        SessionRequest conflict = request(request.requestId, "different private words");
        assertThrows(IllegalArgumentException.class,
                () -> registry.openOwned(OWNER_A, conflict));
        assertThrows(IllegalArgumentException.class,
                () -> registry.eventsOwned(OWNER_B, first.sessionId, "", 10));
    }

    @Test
    public void listCancelAndEventReplayRemainContractValid() {
        TransientSessionRegistry registry = registry(4);
        SessionHandle handle = registry.openOwned(
                OWNER_A,
                request("8d595630-2255-4f4d-ac0f-26a20ee96f29", "I feel tired"));
        SessionQuery query = new SessionQuery();
        query.stateFilter = ICentralBrainSessionRuntime.SESSION_STATE_ANY;
        query.includeTerminal = true;
        query.pageSize = 10;

        SessionPage page = registry.listOwned(OWNER_A, query);
        assertEquals(1, page.sessions.length);
        assertEquals(ICentralBrainSessionRuntime.SESSION_STATE_CREATED, page.sessions[0].state);
        assertTrue(registry.cancelOwned(
                OWNER_A,
                handle,
                ICentralBrainSessionRuntime.CANCEL_REASON_USER).isChanged());
        assertFalse(registry.cancelOwned(
                OWNER_A,
                handle,
                ICentralBrainSessionRuntime.CANCEL_REASON_USER).isChanged());

        EventPage first = registry.eventsOwned(OWNER_A, handle.sessionId, "", 1);
        EventContract.validatePage(first);
        assertTrue(first.hasMore);
        EventPage second = registry.eventsOwned(
                OWNER_A,
                handle.sessionId,
                first.nextCursor,
                10);
        EventContract.validateCursorReplay(first, second);
        assertEquals("SessionStateChanged", second.events[0].type);
    }

    @Test
    public void activeCapacityFailsClosedAndTerminalRecordCanBeEvicted() {
        TransientSessionRegistry registry = registry(1);
        SessionHandle first = registry.openOwned(
                OWNER_A,
                request("8d595630-2255-4f4d-ac0f-26a20ee96f29", "first"));
        assertThrows(IllegalStateException.class, () -> registry.openOwned(
                OWNER_A,
                request("6af0f2f1-67d6-4f10-adb1-f93af51e7709", "second")));

        registry.cancelOwned(
                OWNER_A,
                first,
                ICentralBrainSessionRuntime.CANCEL_REASON_USER);
        SessionHandle second = registry.openOwned(
                OWNER_A,
                request("6af0f2f1-67d6-4f10-adb1-f93af51e7709", "second"));
        assertEquals(1, registry.size());
        assertNull(registry.findOwned(OWNER_A, first));
        assertEquals(ICentralBrainSessionRuntime.SESSION_STATE_CREATED,
                registry.findOwned(OWNER_A, second).state);
    }

    private static TransientSessionRegistry registry(int maxSessions) {
        Queue<String> ids = new ArrayDeque<>();
        ids.add("9bffbb6a-5a0b-41b5-a924-bcfb45be4f26");
        ids.add("37b459a6-4373-4b0b-b2a4-44df3adb2aef");
        ids.add("6af0f2f1-67d6-4f10-adb1-f93af51e7709");
        ids.add("24ab900d-aa8b-41e7-a053-39351c41f001");
        ids.add("a72e2d55-41f8-4fd5-bd8d-4bd878227bf8");
        ids.add("f3e529b4-044f-44f5-b611-1847969ca8c4");
        return TransientSessionRegistry.createForContractTest(
                maxSessions,
                2,
                () -> NOW,
                ids::remove);
    }

    private static SessionRequest request(String requestId, String utterance) {
        SessionRequest request = new SessionRequest();
        request.requestId = requestId;
        request.scenarioId = "scene.fatigue.assist.v1";
        request.utterance = utterance;
        request.source = ICentralBrainSessionRuntime.SOURCE_HMI_BUTTON;
        request.seatZone = ICentralBrainSessionRuntime.SEAT_ZONE_DRIVER;
        request.locale = "en-US";
        request.deadlineEpochMs = NOW + 60_000;
        return request;
    }
}
