package com.centralbrain.sdk.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class EventV2ContractTest {
    private static final String SESSION = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";
    private static final String CLIENT = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
    private static final String CURSOR_0 = "ev2:0:" + "a".repeat(64);
    private static final String CURSOR_1 = "ev2:1:" + "b".repeat(64);

    @Test
    public void terminalPageAlwaysCarriesResumeCursor() {
        EventPageV2 page = page(CURSOR_0, 0, new RuntimeEvent[] {event(1)}, CURSOR_1, 1);
        EventV2Contract.validatePage(page);
        assertEquals(1, EventV2Contract.cursorSequence(page.resumeCursor, false));

        page.resumeCursor = "";
        assertThrows(IllegalArgumentException.class, () -> EventV2Contract.validatePage(page));
    }

    @Test
    public void acceptsLegacyReadCursorButRequiresV2AckCursor() {
        assertEquals(4, EventV2Contract.cursorSequence("e:4", true));
        assertThrows(
                IllegalArgumentException.class,
                () -> EventV2Contract.cursorSequence("e:4", false));
        assertThrows(
                IllegalArgumentException.class,
                () -> EventV2Contract.cursorSequence("ev2:4:bad", true));
    }

    @Test
    public void subscriptionAndAckBindCursorSequence() {
        EventSubscriptionRequest subscription = new EventSubscriptionRequest();
        subscription.clientSubscriptionId = CLIENT;
        subscription.sessionId = SESSION;
        subscription.resumeCursor = CURSOR_0;
        subscription.resumeSequence = 0;
        subscription.queueCapacity = 32;
        EventV2Contract.validateSubscriptionRequest(subscription);

        subscription.resumeSequence = 1;
        assertThrows(
                IllegalArgumentException.class,
                () -> EventV2Contract.validateSubscriptionRequest(subscription));

        EventAckRequest ack = new EventAckRequest();
        ack.subscriptionId = "event-v2-1";
        ack.sessionId = SESSION;
        ack.resumeCursor = CURSOR_1;
        ack.acknowledgedSequence = 1;
        EventV2Contract.validateAckRequest(ack);
        ack.acknowledgedSequence = 2;
        assertThrows(IllegalArgumentException.class, () -> EventV2Contract.validateAckRequest(ack));
    }

    @Test
    public void rejectsNonContiguousAndCrossSessionPage() {
        EventPageV2 nonContiguous = page(
                CURSOR_0,
                0,
                new RuntimeEvent[] {event(2)},
                CURSOR_1,
                1);
        assertThrows(
                IllegalArgumentException.class,
                () -> EventV2Contract.validatePage(nonContiguous));

        EventPageV2 page = page(CURSOR_0, 0, new RuntimeEvent[] {event(1)}, CURSOR_1, 1);
        page.events[0].sessionId = CLIENT;
        EventPageV2 crossSession = page;
        assertThrows(
                IllegalArgumentException.class,
                () -> EventV2Contract.validatePage(crossSession));
    }

    private static EventPageV2 page(
            String requestCursor,
            long after,
            RuntimeEvent[] events,
            String resumeCursor,
            long resumeSequence) {
        EventPageV2 page = new EventPageV2();
        page.sessionId = SESSION;
        page.requestCursor = requestCursor;
        page.afterSequence = after;
        page.events = events;
        page.resumeCursor = resumeCursor;
        page.resumeSequence = resumeSequence;
        page.generatedAtEpochMs = 1_750_000_000_100L;
        return page;
    }

    private static RuntimeEvent event(long sequence) {
        RuntimeEvent event = new RuntimeEvent();
        event.eventId = "37b459a6-4373-4b0b-b2a4-44df3adb2aef";
        event.sequence = sequence;
        event.sessionId = SESSION;
        event.type = "ScenarioRequested";
        event.source = EventContract.SOURCE_SCENARIO;
        event.occurredAtEpochMs = 1_750_000_000_000L + sequence;
        event.privacyClass = EventContract.PRIVACY_INTERNAL;
        event.payloadDigest = "c".repeat(64);
        event.eventDigest = "d".repeat(64);
        event.payloadKind = EventContract.PAYLOAD_NONE;
        return event;
    }
}
