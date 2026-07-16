package com.centralbrain.sdk.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class EventContractTest {
    private static final long NOW = 1_750_000_000_000L;
    private static final String SESSION_ID = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";
    private static final String ACTION_ID = "b246f4de-bec4-4b22-9019-1b94dbaf08de";
    private static final String EFFECT_ID = "08fc7600-d9b7-4c50-8b46-eabe4a8055ad";
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void acceptsTypedEventsAndBoundedPages() {
        EventPage first = initialPage(true);
        EventContract.validatePage(first);
        assertEquals(23, EventContract.eventTypes().size());
        assertEquals(3, first.nextSequence);

        EventPage next = nextPage();
        EventContract.validateCursorReplay(first, next);
        assertTrue(next.redactionApplied);
    }

    @Test
    public void rejectsUnknownVersionTypeAndPayloadMismatch() {
        RuntimeEvent unknownVersion = userMessageEvent();
        unknownVersion.schemaVersion = 2;
        expectViolation(() -> EventContract.validateEvent(unknownVersion));

        RuntimeEvent unknownType = userMessageEvent();
        unknownType.type = "ShellOutputReceived";
        expectViolation(() -> EventContract.validateEvent(unknownType));

        RuntimeEvent mismatchedPayload = actionEvent();
        mismatchedPayload.payloadKind = EventContract.PAYLOAD_MESSAGE;
        expectViolation(() -> EventContract.validateEvent(mismatchedPayload));

        RuntimeEvent mismatchedSubject = observationEvent();
        mismatchedSubject.observation.subjectType = EventContract.SUBJECT_MODEL;
        mismatchedSubject.observation.subjectId = "provider.local.test";
        expectViolation(() -> EventContract.validateEvent(mismatchedSubject));
    }

    @Test
    public void rejectsOrderingGapAndInvalidParent() {
        EventPage gap = initialPage(false);
        gap.events[1].sequence = 3;
        expectViolation(() -> EventContract.validatePage(gap));

        EventPage forwardParent = initialPage(false);
        forwardParent.events[1].parentSequence = 3;
        forwardParent.events[1].parentEventId = forwardParent.events[2].eventId;
        expectViolation(() -> EventContract.validatePage(forwardParent));

        RuntimeEvent selfParent = actionEvent();
        selfParent.parentEventId = selfParent.eventId;
        expectViolation(() -> EventContract.validateEvent(selfParent));
    }

    @Test
    public void rejectsUnsafeRedactionAndPageMarkerMismatch() {
        MessageEvent leaked = redactedAssistantMessage();
        leaked.displayText = "private model response";
        expectViolation(() -> EventContract.validateMessage(leaked));

        EventPage page = nextPage();
        page.redactionApplied = false;
        expectViolation(() -> EventContract.validatePage(page));

        MessageEvent unredacted = userMessageEvent().message;
        unredacted.redactionReason = EventContract.REDACTION_PRIVACY;
        expectViolation(() -> EventContract.validateMessage(unredacted));
    }

    @Test
    public void rejectsCursorReplayGapAndMutation() {
        EventPage first = initialPage(true);
        EventPage next = nextPage();
        next.afterSequence = 2;
        expectViolation(() -> EventContract.validateCursorReplay(first, next));

        RuntimeEvent original = actionEvent();
        RuntimeEvent changed = actionEvent();
        changed.source = EventContract.SOURCE_MODEL;
        expectViolation(() -> EventContract.validateImmutableReplay(original, changed));

        EventContract.validateImmutableReplay(original, actionEvent());
    }

    @Test
    public void rejectsOversizePageMessageAndUnknownObservationEnums() {
        EventPage page = initialPage(false);
        page.events = new RuntimeEvent[EventContract.MAX_PAGE_SIZE + 1];
        expectViolation(() -> EventContract.validatePage(page));

        MessageEvent message = userMessageEvent().message;
        message.displayText = "x".repeat(EventContract.MAX_DISPLAY_TEXT_CHARS + 1);
        expectViolation(() -> EventContract.validateMessage(message));

        ObservationEvent observation = observationEvent().observation;
        observation.quality = 99;
        expectViolation(() -> EventContract.validateObservation(observation));
    }

    static EventPage initialPage(boolean hasMore) {
        EventPage page = new EventPage();
        page.sessionId = SESSION_ID;
        page.afterSequence = 0;
        page.events = new RuntimeEvent[] {
                userMessageEvent(), actionEvent(), observationEvent()
        };
        page.nextSequence = 3;
        page.hasMore = hasMore;
        page.nextCursor = hasMore ? "event-cursor-3" : "";
        page.generatedAtEpochMs = NOW + 3;
        return page;
    }

    static EventPage nextPage() {
        EventPage page = new EventPage();
        page.sessionId = SESSION_ID;
        page.requestCursor = "event-cursor-3";
        page.afterSequence = 3;
        page.events = new RuntimeEvent[] {assistantSummaryEvent()};
        page.nextSequence = 4;
        page.redactionApplied = true;
        page.generatedAtEpochMs = NOW + 4;
        return page;
    }

    static RuntimeEvent userMessageEvent() {
        MessageEvent message = new MessageEvent();
        message.messageId = "76015ba7-a1c7-4f95-bec9-eab3eb90105c";
        message.role = EventContract.MESSAGE_USER;
        message.locale = "en-US";
        message.displayText = "I feel tired";
        message.contentDigest = DIGEST;

        RuntimeEvent event = baseEvent(
                "ee141cb0-6927-4453-ac1b-53d57921eb0c",
                1,
                "",
                0,
                "UserMessageReceived");
        event.source = EventContract.SOURCE_USER;
        event.privacyClass = EventContract.PRIVACY_SENSITIVE;
        event.payloadKind = EventContract.PAYLOAD_MESSAGE;
        event.message = message;
        return event;
    }

    static RuntimeEvent actionEvent() {
        ActionEvent action = new ActionEvent();
        action.actionId = ACTION_ID;
        action.nodeId = "apply-hvac";
        action.capabilityId = "vehicle.hvac.temperature";
        action.state = EventContract.ACTION_PROPOSED;
        action.actionDigest = DIGEST;
        action.required = true;

        RuntimeEvent event = baseEvent(
                "cc8a7193-3d10-47da-bcea-0583b369c407",
                2,
                "ee141cb0-6927-4453-ac1b-53d57921eb0c",
                1,
                "ActionProposed");
        event.source = EventContract.SOURCE_GRAPH;
        event.payloadKind = EventContract.PAYLOAD_ACTION;
        event.action = action;
        return event;
    }

    static RuntimeEvent observationEvent() {
        ObservationEvent observation = new ObservationEvent();
        observation.observationId = "1fdcae20-fe42-4aca-bf1c-79f1a25a7f51";
        observation.subjectType = EventContract.SUBJECT_EFFECT;
        observation.subjectId = EFFECT_ID;
        observation.outcome = EventContract.OUTCOME_OBSERVED;
        observation.quality = EventContract.QUALITY_FRESH;
        observation.evidenceDigest = DIGEST;

        RuntimeEvent event = baseEvent(
                "32e55938-ec71-4fd4-9530-5bdb9d333818",
                3,
                "cc8a7193-3d10-47da-bcea-0583b369c407",
                2,
                "EffectObserved");
        event.source = EventContract.SOURCE_ADAPTER;
        event.payloadKind = EventContract.PAYLOAD_OBSERVATION;
        event.observation = observation;
        return event;
    }

    static RuntimeEvent assistantSummaryEvent() {
        RuntimeEvent event = baseEvent(
                "ebef8dbe-4ea4-4ba0-984c-f3a39ec473eb",
                4,
                "32e55938-ec71-4fd4-9530-5bdb9d333818",
                3,
                "AssistantSummaryCreated");
        event.source = EventContract.SOURCE_RUNTIME;
        event.privacyClass = EventContract.PRIVACY_RESTRICTED;
        event.payloadKind = EventContract.PAYLOAD_MESSAGE;
        event.message = redactedAssistantMessage();
        return event;
    }

    private static MessageEvent redactedAssistantMessage() {
        MessageEvent message = new MessageEvent();
        message.messageId = "02f6ff8d-650d-4317-8d95-803411b82894";
        message.role = EventContract.MESSAGE_ASSISTANT;
        message.locale = "en-US";
        message.displayText = EventContract.REDACTED_DISPLAY_TEXT;
        message.contentDigest = DIGEST;
        message.redacted = true;
        message.redactionReason = EventContract.REDACTION_PRIVACY;
        return message;
    }

    private static RuntimeEvent baseEvent(
            String eventId,
            long sequence,
            String parentEventId,
            long parentSequence,
            String type) {
        RuntimeEvent event = new RuntimeEvent();
        event.eventId = eventId;
        event.sequence = sequence;
        event.sessionId = SESSION_ID;
        event.parentEventId = parentEventId;
        event.parentSequence = parentSequence;
        event.type = type;
        event.source = EventContract.SOURCE_RUNTIME;
        event.occurredAtEpochMs = NOW + sequence;
        event.privacyClass = EventContract.PRIVACY_INTERNAL;
        event.payloadDigest = DIGEST;
        event.eventDigest = DIGEST;
        return event;
    }

    private static void expectViolation(Runnable operation) {
        try {
            operation.run();
            fail("expected an Event contract violation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().startsWith("CB_EVENT_CONTRACT:"));
        }
    }
}
