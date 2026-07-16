package com.centralbrain.sdk.session;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bounded validation for the Stage 2 Session AIDL V1 contract. */
public final class SessionContract {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_REQUEST_ID_CHARS = 36;
    public static final int MAX_SESSION_ID_CHARS = 36;
    public static final int MAX_SCENARIO_ID_CHARS = 96;
    public static final int MAX_UTTERANCE_CHARS = 1024;
    public static final int MAX_LOCALE_CHARS = 32;
    public static final int MAX_SUMMARY_CHARS = 512;
    public static final int MAX_CURSOR_CHARS = 256;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    public static final long MAX_DEADLINE_FUTURE_MS = 5 * 60 * 1000L;

    private static final Pattern SCENARIO_ID =
            Pattern.compile("[a-z][a-z0-9]*(?:[.][a-z0-9][a-z0-9_-]*){2,7}");
    private static final Pattern LANGUAGE_TAG =
            Pattern.compile("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*");

    private SessionContract() {}

    public static void validateRequest(SessionRequest request, long nowEpochMs) {
        requireNotNull(request, "request");
        requireVersion(request.schemaVersion, "request.schemaVersion");
        requireUuid(request.requestId, MAX_REQUEST_ID_CHARS, "request.requestId");
        requireBounded(request.scenarioId, MAX_SCENARIO_ID_CHARS, "request.scenarioId");
        requireBounded(request.utterance, MAX_UTTERANCE_CHARS, "request.utterance");
        if (request.scenarioId.isEmpty() && request.utterance.isEmpty()) {
            throw violation("request must contain scenarioId or utterance");
        }
        if (!request.scenarioId.isEmpty() && !SCENARIO_ID.matcher(request.scenarioId).matches()) {
            throw violation("request.scenarioId is not an allowlisted identifier shape");
        }
        if (!isSource(request.source)) {
            throw violation("request.source is unknown");
        }
        if (!isSeatZone(request.seatZone)) {
            throw violation("request.seatZone is unknown");
        }
        requireBounded(request.locale, MAX_LOCALE_CHARS, "request.locale");
        if (request.locale.isEmpty() || !LANGUAGE_TAG.matcher(request.locale).matches()
                || Locale.forLanguageTag(request.locale).getLanguage().isEmpty()) {
            throw violation("request.locale is not a bounded BCP-47 language tag");
        }
        if (nowEpochMs <= 0 || request.deadlineEpochMs <= nowEpochMs
                || request.deadlineEpochMs - nowEpochMs > MAX_DEADLINE_FUTURE_MS) {
            throw violation("request.deadlineEpochMs is outside the admission window");
        }
        if (request.clientContextVersion < 0) {
            throw violation("request.clientContextVersion is negative");
        }
    }

    public static void validateHandle(SessionHandle handle) {
        requireNotNull(handle, "handle");
        requireVersion(handle.schemaVersion, "handle.schemaVersion");
        requireUuid(handle.sessionId, MAX_SESSION_ID_CHARS, "handle.sessionId");
        if (handle.acceptedAtEpochMs <= 0 || handle.expiresAtEpochMs < handle.acceptedAtEpochMs) {
            throw violation("handle timestamps are invalid");
        }
    }

    public static void validateSnapshot(SessionSnapshot snapshot) {
        requireNotNull(snapshot, "snapshot");
        requireVersion(snapshot.schemaVersion, "snapshot.schemaVersion");
        requireUuid(snapshot.sessionId, MAX_SESSION_ID_CHARS, "snapshot.sessionId");
        requireUuid(snapshot.requestId, MAX_REQUEST_ID_CHARS, "snapshot.requestId");
        requireBounded(snapshot.scenarioId, MAX_SCENARIO_ID_CHARS, "snapshot.scenarioId");
        if (!snapshot.scenarioId.isEmpty() && !SCENARIO_ID.matcher(snapshot.scenarioId).matches()) {
            throw violation("snapshot.scenarioId is not an allowlisted identifier shape");
        }
        if (!isSessionState(snapshot.state)) {
            throw violation("snapshot.state is unknown");
        }
        if (snapshot.activePlanRevision < 0 || snapshot.lastEventSequence < 0) {
            throw violation("snapshot revision or event sequence is negative");
        }
        if (snapshot.createdAtEpochMs <= 0 || snapshot.updatedAtEpochMs < snapshot.createdAtEpochMs
                || snapshot.deadlineEpochMs < snapshot.createdAtEpochMs) {
            throw violation("snapshot timestamps are invalid");
        }
        requireBounded(snapshot.summary, MAX_SUMMARY_CHARS, "snapshot.summary");
    }

    public static void validateQuery(SessionQuery query) {
        requireNotNull(query, "query");
        requireVersion(query.schemaVersion, "query.schemaVersion");
        if (query.stateFilter != ICentralBrainSessionRuntime.SESSION_STATE_ANY
                && !isSessionState(query.stateFilter)) {
            throw violation("query.stateFilter is unknown");
        }
        requireBounded(query.cursor, MAX_CURSOR_CHARS, "query.cursor");
        if (query.pageSize < 1 || query.pageSize > MAX_PAGE_SIZE) {
            throw violation("query.pageSize is outside 1.." + MAX_PAGE_SIZE);
        }
    }

    public static void validatePage(SessionPage page) {
        requireNotNull(page, "page");
        requireVersion(page.schemaVersion, "page.schemaVersion");
        if (page.sessions == null || page.sessions.length > MAX_PAGE_SIZE) {
            throw violation("page.sessions exceeds the bounded page size");
        }
        for (SessionSnapshot snapshot : page.sessions) {
            validateSnapshot(snapshot);
        }
        requireBounded(page.nextCursor, MAX_CURSOR_CHARS, "page.nextCursor");
        if (page.hasMore && page.nextCursor.isEmpty()) {
            throw violation("page.nextCursor is required when hasMore is true");
        }
        if (page.generatedAtEpochMs <= 0) {
            throw violation("page.generatedAtEpochMs is invalid");
        }
    }

    public static boolean isTerminalState(int state) {
        return state == ICentralBrainSessionRuntime.SESSION_STATE_COMPLETED
                || state == ICentralBrainSessionRuntime.SESSION_STATE_FAILED
                || state == ICentralBrainSessionRuntime.SESSION_STATE_CANCELLED;
    }

    public static boolean isCancelReason(int reason) {
        return reason >= ICentralBrainSessionRuntime.CANCEL_REASON_USER
                && reason <= ICentralBrainSessionRuntime.CANCEL_REASON_CALLER_GONE;
    }

    private static boolean isSource(int source) {
        return source >= ICentralBrainSessionRuntime.SOURCE_HMI_BUTTON
                && source <= ICentralBrainSessionRuntime.SOURCE_API;
    }

    private static boolean isSeatZone(int seatZone) {
        return seatZone >= ICentralBrainSessionRuntime.SEAT_ZONE_UNSPECIFIED
                && seatZone <= ICentralBrainSessionRuntime.SEAT_ZONE_CABIN;
    }

    private static boolean isSessionState(int state) {
        return state >= ICentralBrainSessionRuntime.SESSION_STATE_CREATED
                && state <= ICentralBrainSessionRuntime.SESSION_STATE_CANCELLED;
    }

    private static void requireVersion(int version, String field) {
        if (version != SCHEMA_VERSION) {
            throw violation(field + " is unsupported");
        }
    }

    private static void requireUuid(String value, int maxChars, String field) {
        requireBounded(value, maxChars, field);
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
        return new IllegalArgumentException("CB_SESSION_CONTRACT: " + message);
    }
}
