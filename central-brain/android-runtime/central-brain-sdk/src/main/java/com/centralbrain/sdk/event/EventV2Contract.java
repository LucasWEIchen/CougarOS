package com.centralbrain.sdk.event;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded validation for the independent Event V2 cursor/ACK contract. */
public final class EventV2Contract {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_CURSOR_CHARS = 256;
    public static final int MAX_CLIENT_SUBSCRIPTION_ID_CHARS = 96;

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Pattern LEGACY_CURSOR = Pattern.compile("e:([1-9][0-9]*)");
    private static final Pattern V2_CURSOR = Pattern.compile("ev2:([0-9]+):([0-9a-f]{64})");

    private EventV2Contract() {}

    public static void validatePage(EventPageV2 page) {
        require(page != null, "page is required");
        require(page.schemaVersion == SCHEMA_VERSION, "page schemaVersion is unsupported");
        requireUuid(page.sessionId, "page.sessionId");
        long requested = cursorSequence(page.requestCursor, true);
        require(page.afterSequence == requested, "page request cursor does not match sequence");
        require(page.events != null && page.events.length <= EventContract.MAX_PAGE_SIZE,
                "page events exceed limit");
        require(page.generatedAtEpochMs > 0, "page generated time is invalid");

        long expected = page.afterSequence + 1;
        boolean redaction = false;
        for (RuntimeEvent event : page.events) {
            EventContract.validateEvent(event);
            require(page.sessionId.equals(event.sessionId), "page contains another session");
            require(event.sequence == expected, "page event sequence is not contiguous");
            require(event.occurredAtEpochMs <= page.generatedAtEpochMs,
                    "page event occurs after generation");
            redaction |= event.payloadKind == EventContract.PAYLOAD_MESSAGE
                    && event.message != null
                    && event.message.redacted;
            expected++;
        }
        long delivered = page.events.length == 0
                ? page.afterSequence : page.events[page.events.length - 1].sequence;
        require(page.resumeSequence == delivered, "page resume sequence is not delivered sequence");
        require(cursorSequence(page.resumeCursor, false) == page.resumeSequence,
                "page resume cursor does not match sequence");
        require(page.redactionApplied == redaction,
                "page redaction marker does not match payloads");
        if (page.hasMore) {
            require(page.events.length > 0, "page with more events must make progress");
        }
    }

    public static void validateSubscriptionRequest(EventSubscriptionRequest request) {
        require(request != null, "subscription request is required");
        require(request.schemaVersion == SCHEMA_VERSION,
                "subscription request schemaVersion is unsupported");
        requireUuid(request.clientSubscriptionId, "clientSubscriptionId");
        requireUuid(request.sessionId, "sessionId");
        require(request.resumeSequence == cursorSequence(request.resumeCursor, true),
                "subscription cursor does not match sequence");
        require(request.queueCapacity >= 1
                        && request.queueCapacity <= ICentralBrainSessionEventsV2.MAX_QUEUE_CAPACITY,
                "subscription queueCapacity is out of range");
    }

    public static void validateSubscriptionHandle(EventSubscriptionHandle handle) {
        require(handle != null, "subscription handle is required");
        require(handle.schemaVersion == SCHEMA_VERSION,
                "subscription handle schemaVersion is unsupported");
        requireBoundedId(handle.subscriptionId, 128, "subscriptionId");
        requireUuid(handle.clientSubscriptionId, "clientSubscriptionId");
        requireUuid(handle.sessionId, "sessionId");
        require(cursorSequence(handle.resumeCursor, false) == handle.acknowledgedSequence,
                "subscription handle cursor does not match ACK");
        require(handle.state >= ICentralBrainSessionEventsV2.SUBSCRIPTION_STATE_ACTIVE
                        && handle.state <= ICentralBrainSessionEventsV2.SUBSCRIPTION_STATE_CANCELLED,
                "subscription state is unknown");
        require(handle.updatedAtEpochMs >= 0, "subscription update time is invalid");
    }

    public static void validateAckRequest(EventAckRequest request) {
        require(request != null, "ACK request is required");
        require(request.schemaVersion == SCHEMA_VERSION, "ACK schemaVersion is unsupported");
        requireBoundedId(request.subscriptionId, 128, "subscriptionId");
        requireUuid(request.sessionId, "sessionId");
        require(cursorSequence(request.resumeCursor, false) == request.acknowledgedSequence,
                "ACK cursor does not match sequence");
    }

    public static void validateAckResult(EventAckResult result) {
        require(result != null, "ACK result is required");
        require(result.schemaVersion == SCHEMA_VERSION,
                "ACK result schemaVersion is unsupported");
        requireBoundedId(result.subscriptionId, 128, "subscriptionId");
        require(result.outcome >= ICentralBrainSessionEventsV2.ACK_APPLIED
                        && result.outcome <= ICentralBrainSessionEventsV2.ACK_SOURCE_REGRESSION,
                "ACK outcome is unknown");
        require(result.acknowledgedSequence >= 0, "ACK result sequence is negative");
        require(cursorSequence(result.resumeCursor, false) == result.acknowledgedSequence,
                "ACK result cursor does not match sequence");
    }

    public static long cursorSequence(String cursor, boolean allowInitialOrLegacy) {
        require(cursor != null && cursor.length() <= MAX_CURSOR_CHARS, "cursor is invalid");
        if (cursor.isEmpty()) {
            require(allowInitialOrLegacy, "V2 resume cursor is required");
            return 0;
        }
        Matcher v2 = V2_CURSOR.matcher(cursor);
        if (v2.matches()) {
            return parseSequence(v2.group(1));
        }
        Matcher legacy = LEGACY_CURSOR.matcher(cursor);
        require(allowInitialOrLegacy && legacy.matches(), "cursor format is invalid");
        return parseSequence(legacy.group(1));
    }

    private static long parseSequence(String value) {
        try {
            long sequence = Long.parseLong(value);
            require(sequence >= 0, "cursor sequence is negative");
            return sequence;
        } catch (NumberFormatException failure) {
            throw violation("cursor sequence is invalid");
        }
    }

    private static void requireUuid(String value, String name) {
        require(value != null && UUID_PATTERN.matcher(value).matches(), name + " is invalid");
    }

    private static void requireBoundedId(String value, int limit, String name) {
        require(value != null
                        && !value.isEmpty()
                        && value.length() <= limit
                        && value.matches("[A-Za-z0-9._:-]+"),
                name + " is invalid");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw violation(message);
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_EVENT_V2_CONTRACT: " + message);
    }
}
