package com.centralbrain.sdk.event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bounded validation for the Stage 2 Session Event AIDL V1 contract. */
public final class EventContract {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_EVENT_TYPE_CHARS = 64;
    public static final int MAX_NODE_ID_CHARS = 64;
    public static final int MAX_CAPABILITY_ID_CHARS = 96;
    public static final int MAX_SUBJECT_ID_CHARS = 96;
    public static final int MAX_LOCALE_CHARS = 32;
    public static final int MAX_DISPLAY_TEXT_CHARS = 1024;
    public static final int MAX_CURSOR_CHARS = 256;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String REDACTED_DISPLAY_TEXT = "[REDACTED]";

    public static final int PAYLOAD_NONE = 0;
    public static final int PAYLOAD_ACTION = 1;
    public static final int PAYLOAD_OBSERVATION = 2;
    public static final int PAYLOAD_MESSAGE = 3;

    public static final int SOURCE_USER = 1;
    public static final int SOURCE_HMI = 2;
    public static final int SOURCE_RUNTIME = 3;
    public static final int SOURCE_GOVERNANCE = 4;
    public static final int SOURCE_SCENARIO = 5;
    public static final int SOURCE_GRAPH = 6;
    public static final int SOURCE_ADAPTER = 7;
    public static final int SOURCE_MODEL = 8;
    public static final int SOURCE_TOOL = 9;
    public static final int SOURCE_SYSTEM = 10;

    public static final int PRIVACY_PUBLIC = 1;
    public static final int PRIVACY_INTERNAL = 2;
    public static final int PRIVACY_SENSITIVE = 3;
    public static final int PRIVACY_RESTRICTED = 4;

    public static final int ACTION_PROPOSED = 1;
    public static final int ACTION_AUTHORIZED = 2;
    public static final int ACTION_REJECTED = 3;

    public static final int SUBJECT_CONTEXT = 1;
    public static final int SUBJECT_PLAN = 2;
    public static final int SUBJECT_ACTION = 3;
    public static final int SUBJECT_EFFECT = 4;
    public static final int SUBJECT_TOOL = 5;
    public static final int SUBJECT_MODEL = 6;
    public static final int SUBJECT_COMPENSATION = 7;

    public static final int OUTCOME_OBSERVED = 1;
    public static final int OUTCOME_VERIFIED = 2;
    public static final int OUTCOME_MISMATCH = 3;
    public static final int OUTCOME_UNAVAILABLE = 4;
    public static final int OUTCOME_FAILED = 5;

    public static final int QUALITY_FRESH = 1;
    public static final int QUALITY_STALE = 2;
    public static final int QUALITY_CONFLICT = 3;
    public static final int QUALITY_UNAVAILABLE = 4;

    public static final int MESSAGE_USER = 1;
    public static final int MESSAGE_ASSISTANT = 2;
    public static final int MESSAGE_SYSTEM = 3;

    public static final int REDACTION_NONE = 0;
    public static final int REDACTION_POLICY = 1;
    public static final int REDACTION_PRIVACY = 2;
    public static final int REDACTION_UNAVAILABLE = 3;

    private static final Pattern LOCAL_ID =
            Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern QUALIFIED_ID =
            Pattern.compile("[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern LANGUAGE_TAG =
            Pattern.compile("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private static final Set<String> EVENT_TYPES = Set.of(
            "UserMessageReceived",
            "ScenarioRequested",
            "ContextCaptured",
            "PlanCompiled",
            "ActionProposed",
            "ActionAuthorized",
            "ActionRejected",
            "ApprovalRequested",
            "ApprovalResolved",
            "ApprovalExpired",
            "EffectPrepared",
            "EffectDispatched",
            "EffectObserved",
            "EffectVerified",
            "EffectFailed",
            "ToolInvoked",
            "ToolObserved",
            "ModelRequested",
            "ModelObserved",
            "CompensationStarted",
            "CompensationObserved",
            "AssistantSummaryCreated",
            "SessionStateChanged");

    private static final Set<String> ACTION_EVENT_TYPES = Set.of(
            "ActionProposed", "ActionAuthorized", "ActionRejected");
    private static final Set<String> OBSERVATION_EVENT_TYPES = Set.of(
            "EffectObserved",
            "EffectVerified",
            "EffectFailed",
            "ToolObserved",
            "ModelObserved",
            "CompensationObserved");
    private static final Set<String> MESSAGE_EVENT_TYPES = Set.of(
            "UserMessageReceived", "AssistantSummaryCreated");

    private EventContract() {}

    public static Set<String> eventTypes() {
        return Set.copyOf(EVENT_TYPES);
    }

    public static void validateEvent(RuntimeEvent event) {
        requireNotNull(event, "event");
        requireVersion(event.schemaVersion, "event.schemaVersion");
        requireUuid(event.eventId, "event.eventId");
        if (event.sequence < 1) {
            throw violation("event.sequence must be positive");
        }
        requireUuid(event.sessionId, "event.sessionId");
        validateParent(event);
        requireBounded(event.type, MAX_EVENT_TYPE_CHARS, "event.type");
        if (!EVENT_TYPES.contains(event.type)) {
            throw violation("event.type is not allowlisted");
        }
        if (event.source < SOURCE_USER || event.source > SOURCE_SYSTEM) {
            throw violation("event.source is unknown");
        }
        if (event.occurredAtEpochMs <= 0) {
            throw violation("event.occurredAtEpochMs is invalid");
        }
        if (event.privacyClass < PRIVACY_PUBLIC
                || event.privacyClass > PRIVACY_RESTRICTED) {
            throw violation("event.privacyClass is unknown");
        }
        requireDigest(event.payloadDigest, "event.payloadDigest");
        requireDigest(event.eventDigest, "event.eventDigest");
        validatePayload(event);
    }

    public static void validateAction(ActionEvent action) {
        requireNotNull(action, "action");
        requireVersion(action.schemaVersion, "action.schemaVersion");
        requireUuid(action.actionId, "action.actionId");
        requireIdentifier(action.nodeId, MAX_NODE_ID_CHARS, LOCAL_ID, "action.nodeId");
        requireIdentifier(
                action.capabilityId,
                MAX_CAPABILITY_ID_CHARS,
                QUALIFIED_ID,
                "action.capabilityId");
        if (action.state < ACTION_PROPOSED || action.state > ACTION_REJECTED) {
            throw violation("action.state is unknown");
        }
        requireDigest(action.actionDigest, "action.actionDigest");
    }

    public static void validateObservation(ObservationEvent observation) {
        requireNotNull(observation, "observation");
        requireVersion(observation.schemaVersion, "observation.schemaVersion");
        requireUuid(observation.observationId, "observation.observationId");
        if (observation.subjectType < SUBJECT_CONTEXT
                || observation.subjectType > SUBJECT_COMPENSATION) {
            throw violation("observation.subjectType is unknown");
        }
        requireBounded(
                observation.subjectId,
                MAX_SUBJECT_ID_CHARS,
                "observation.subjectId");
        if (observation.subjectType == SUBJECT_TOOL
                || observation.subjectType == SUBJECT_MODEL) {
            if (!QUALIFIED_ID.matcher(observation.subjectId).matches()) {
                throw violation("observation.subjectId is not a qualified identifier");
            }
        } else {
            requireUuid(observation.subjectId, "observation.subjectId");
        }
        if (observation.outcome < OUTCOME_OBSERVED || observation.outcome > OUTCOME_FAILED) {
            throw violation("observation.outcome is unknown");
        }
        if (observation.quality < QUALITY_FRESH
                || observation.quality > QUALITY_UNAVAILABLE) {
            throw violation("observation.quality is unknown");
        }
        requireDigest(observation.evidenceDigest, "observation.evidenceDigest");
    }

    public static void validateMessage(MessageEvent message) {
        requireNotNull(message, "message");
        requireVersion(message.schemaVersion, "message.schemaVersion");
        requireUuid(message.messageId, "message.messageId");
        if (message.role < MESSAGE_USER || message.role > MESSAGE_SYSTEM) {
            throw violation("message.role is unknown");
        }
        requireBounded(message.locale, MAX_LOCALE_CHARS, "message.locale");
        if (!LANGUAGE_TAG.matcher(message.locale).matches()
                || Locale.forLanguageTag(message.locale).getLanguage().isEmpty()) {
            throw violation("message.locale is not a bounded BCP-47 language tag");
        }
        requireBounded(
                message.displayText,
                MAX_DISPLAY_TEXT_CHARS,
                "message.displayText");
        requireDigest(message.contentDigest, "message.contentDigest");
        if (message.redacted) {
            if (!REDACTED_DISPLAY_TEXT.equals(message.displayText)
                    || message.redactionReason < REDACTION_POLICY
                    || message.redactionReason > REDACTION_UNAVAILABLE) {
                throw violation("redacted message has unsafe display text or reason");
            }
        } else if (message.displayText.isEmpty()
                || message.redactionReason != REDACTION_NONE) {
            throw violation("unredacted message has invalid text or redaction reason");
        }
    }

    public static void validatePage(EventPage page) {
        requireNotNull(page, "page");
        requireVersion(page.schemaVersion, "page.schemaVersion");
        requireUuid(page.sessionId, "page.sessionId");
        requireCursor(page.requestCursor, "page.requestCursor");
        if (page.afterSequence < 0) {
            throw violation("page.afterSequence is negative");
        }
        if (page.afterSequence == 0 && !page.requestCursor.isEmpty()) {
            throw violation("initial page must use an empty request cursor");
        }
        if (page.afterSequence > 0 && page.requestCursor.isEmpty()) {
            throw violation("replay page requires a request cursor");
        }
        if (page.events == null || page.events.length > MAX_PAGE_SIZE) {
            throw violation("page.events exceeds " + MAX_PAGE_SIZE);
        }
        if (page.generatedAtEpochMs <= 0) {
            throw violation("page.generatedAtEpochMs is invalid");
        }

        long expectedSequence = page.afterSequence + 1;
        Map<Long, String> eventIdBySequence = new HashMap<>();
        Set<String> eventIds = new HashSet<>();
        boolean redactionFound = false;
        for (RuntimeEvent event : page.events) {
            validateEvent(event);
            if (!page.sessionId.equals(event.sessionId)) {
                throw violation("page contains an event from another session");
            }
            if (event.occurredAtEpochMs > page.generatedAtEpochMs) {
                throw violation("page event occurs after page generation");
            }
            if (event.sequence != expectedSequence) {
                throw violation("page event sequence is not contiguous and ordered");
            }
            if (!eventIds.add(event.eventId)) {
                throw violation("page contains a duplicate eventId");
            }
            if (event.parentSequence > page.afterSequence) {
                String expectedParent = eventIdBySequence.get(event.parentSequence);
                if (!event.parentEventId.equals(expectedParent)) {
                    throw violation("event parent is missing or ordered after the child");
                }
            }
            eventIdBySequence.put(event.sequence, event.eventId);
            if (event.payloadKind == PAYLOAD_MESSAGE && event.message.redacted) {
                redactionFound = true;
            }
            expectedSequence++;
        }

        long expectedNext = page.events.length == 0
                ? page.afterSequence
                : page.events[page.events.length - 1].sequence;
        if (page.nextSequence != expectedNext) {
            throw violation("page.nextSequence does not match the delivered cursor");
        }
        requireCursor(page.nextCursor, "page.nextCursor");
        if (page.hasMore) {
            if (page.events.length == 0 || page.nextCursor.isEmpty()) {
                throw violation("page.nextCursor is required when hasMore is true");
            }
        } else if (!page.nextCursor.isEmpty()) {
            throw violation("terminal page must not expose a next cursor");
        }
        if (page.redactionApplied != redactionFound) {
            throw violation("page.redactionApplied does not match message payloads");
        }
    }

    public static void validateCursorReplay(EventPage previous, EventPage next) {
        validatePage(previous);
        validatePage(next);
        if (!previous.hasMore) {
            throw violation("terminal page cannot be replayed forward");
        }
        if (!previous.sessionId.equals(next.sessionId)
                || previous.nextSequence != next.afterSequence
                || !previous.nextCursor.equals(next.requestCursor)
                || next.generatedAtEpochMs < previous.generatedAtEpochMs) {
            throw violation("cursor replay does not continue the previous page");
        }
    }

    public static void validateImmutableReplay(RuntimeEvent original, RuntimeEvent replay) {
        validateEvent(original);
        validateEvent(replay);
        if (!sameRuntimeMetadata(original, replay)
                || !sameAction(original.action, replay.action)
                || !sameObservation(original.observation, replay.observation)
                || !sameMessage(original.message, replay.message)) {
            throw violation("event identity was mutated during replay");
        }
    }

    private static void validateParent(RuntimeEvent event) {
        requireBounded(event.parentEventId, 36, "event.parentEventId");
        if (event.sequence == 1) {
            if (!event.parentEventId.isEmpty() || event.parentSequence != 0) {
                throw violation("root event must not contain a parent");
            }
            return;
        }
        requireUuid(event.parentEventId, "event.parentEventId");
        if (event.parentEventId.equals(event.eventId)
                || event.parentSequence < 1
                || event.parentSequence >= event.sequence) {
            throw violation("event parent identity or sequence is invalid");
        }
    }

    private static void validatePayload(RuntimeEvent event) {
        int nonNullPayloads = (event.action == null ? 0 : 1)
                + (event.observation == null ? 0 : 1)
                + (event.message == null ? 0 : 1);
        if (event.payloadKind == PAYLOAD_NONE) {
            if (nonNullPayloads != 0
                    || ACTION_EVENT_TYPES.contains(event.type)
                    || OBSERVATION_EVENT_TYPES.contains(event.type)
                    || MESSAGE_EVENT_TYPES.contains(event.type)) {
                throw violation("event requires a typed payload");
            }
            return;
        }
        if (nonNullPayloads != 1) {
            throw violation("event must contain exactly one typed payload");
        }
        if (event.payloadKind == PAYLOAD_ACTION
                && event.action != null
                && ACTION_EVENT_TYPES.contains(event.type)) {
            validateAction(event.action);
            int expected = "ActionProposed".equals(event.type)
                    ? ACTION_PROPOSED
                    : ("ActionAuthorized".equals(event.type)
                            ? ACTION_AUTHORIZED : ACTION_REJECTED);
            if (event.action.state != expected) {
                throw violation("action payload state does not match event type");
            }
            return;
        }
        if (event.payloadKind == PAYLOAD_OBSERVATION
                && event.observation != null
                && OBSERVATION_EVENT_TYPES.contains(event.type)) {
            validateObservation(event.observation);
            int expectedSubject = expectedObservationSubject(event.type);
            if (event.observation.subjectType != expectedSubject) {
                throw violation("observation subject does not match event type");
            }
            if ("EffectVerified".equals(event.type)
                    && event.observation.outcome != OUTCOME_VERIFIED) {
                throw violation("verified event requires VERIFIED outcome");
            }
            if ("EffectFailed".equals(event.type)
                    && event.observation.outcome != OUTCOME_FAILED) {
                throw violation("failed event requires FAILED outcome");
            }
            return;
        }
        if (event.payloadKind == PAYLOAD_MESSAGE
                && event.message != null
                && MESSAGE_EVENT_TYPES.contains(event.type)) {
            validateMessage(event.message);
            if (("UserMessageReceived".equals(event.type)
                            && event.message.role != MESSAGE_USER)
                    || ("AssistantSummaryCreated".equals(event.type)
                            && event.message.role != MESSAGE_ASSISTANT)) {
                throw violation("message role does not match event type");
            }
            return;
        }
        throw violation("event payload kind does not match event type");
    }

    private static int expectedObservationSubject(String eventType) {
        if (eventType.startsWith("Effect")) {
            return SUBJECT_EFFECT;
        }
        if ("ToolObserved".equals(eventType)) {
            return SUBJECT_TOOL;
        }
        if ("ModelObserved".equals(eventType)) {
            return SUBJECT_MODEL;
        }
        return SUBJECT_COMPENSATION;
    }

    private static boolean sameRuntimeMetadata(RuntimeEvent left, RuntimeEvent right) {
        return left.schemaVersion == right.schemaVersion
                && left.sequence == right.sequence
                && left.parentSequence == right.parentSequence
                && left.source == right.source
                && left.occurredAtEpochMs == right.occurredAtEpochMs
                && left.privacyClass == right.privacyClass
                && left.payloadKind == right.payloadKind
                && Objects.equals(left.eventId, right.eventId)
                && Objects.equals(left.sessionId, right.sessionId)
                && Objects.equals(left.parentEventId, right.parentEventId)
                && Objects.equals(left.type, right.type)
                && Objects.equals(left.payloadDigest, right.payloadDigest)
                && Objects.equals(left.eventDigest, right.eventDigest);
    }

    private static boolean sameAction(ActionEvent left, ActionEvent right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.schemaVersion == right.schemaVersion
                && left.state == right.state
                && left.required == right.required
                && Objects.equals(left.actionId, right.actionId)
                && Objects.equals(left.nodeId, right.nodeId)
                && Objects.equals(left.capabilityId, right.capabilityId)
                && Objects.equals(left.actionDigest, right.actionDigest);
    }

    private static boolean sameObservation(ObservationEvent left, ObservationEvent right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.schemaVersion == right.schemaVersion
                && left.subjectType == right.subjectType
                && left.outcome == right.outcome
                && left.quality == right.quality
                && left.terminal == right.terminal
                && Objects.equals(left.observationId, right.observationId)
                && Objects.equals(left.subjectId, right.subjectId)
                && Objects.equals(left.evidenceDigest, right.evidenceDigest);
    }

    private static boolean sameMessage(MessageEvent left, MessageEvent right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.schemaVersion == right.schemaVersion
                && left.role == right.role
                && left.redacted == right.redacted
                && left.redactionReason == right.redactionReason
                && Objects.equals(left.messageId, right.messageId)
                && Objects.equals(left.locale, right.locale)
                && Objects.equals(left.displayText, right.displayText)
                && Objects.equals(left.contentDigest, right.contentDigest);
    }

    private static void requireVersion(int version, String field) {
        if (version != SCHEMA_VERSION) {
            throw violation(field + " is unsupported");
        }
    }

    private static void requireUuid(String value, String field) {
        requireBounded(value, 36, field);
        if (value.length() != 36) {
            throw violation(field + " is not a canonical UUID");
        }
        try {
            if (!UUID.fromString(value).toString().equals(value.toLowerCase(Locale.ROOT))) {
                throw violation(field + " is not a canonical UUID");
            }
        } catch (IllegalArgumentException invalid) {
            throw violation(field + " is not a canonical UUID");
        }
    }

    private static void requireDigest(String value, String field) {
        requireBounded(value, 64, field);
        if (!SHA_256.matcher(value).matches()) {
            throw violation(field + " is not a lowercase SHA-256 digest");
        }
    }

    private static void requireCursor(String value, String field) {
        requireBounded(value, MAX_CURSOR_CHARS, field);
    }

    private static void requireIdentifier(
            String value, int maxChars, Pattern pattern, String field) {
        requireBounded(value, maxChars, field);
        if (value.isEmpty() || !pattern.matcher(value).matches()) {
            throw violation(field + " has an invalid identifier shape");
        }
    }

    private static void requireBounded(String value, int maxChars, String field) {
        if (value == null) {
            throw violation(field + " is null");
        }
        if (value.length() > maxChars) {
            throw violation(field + " exceeds " + maxChars + " characters");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) && character != '\n' && character != '\t') {
                throw violation(field + " contains a forbidden control character");
            }
        }
    }

    private static void requireNotNull(Object value, String field) {
        if (value == null) {
            throw violation(field + " is null");
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_EVENT_CONTRACT: " + message);
    }
}
