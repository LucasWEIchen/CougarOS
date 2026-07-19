package com.centralbrain.runtime.session;

import com.centralbrain.runtime.persistence.DurableDigest;
import com.centralbrain.sdk.event.EventV2Contract;

/** Owner/session-bound opaque cursor codec for Event V2. */
final class SessionEventCursorCodec {
    private static final String DOMAIN = "central-brain-session-event-cursor-v2";

    String encode(String ownerFingerprint, String sessionId, long sequence) {
        requireOwner(ownerFingerprint);
        requireSession(sessionId);
        if (sequence < 0) {
            throw new IllegalArgumentException("CB_EVENT_V2: cursor sequence is negative");
        }
        return "ev2:" + sequence + ':' + DurableDigest.sha256(
                DOMAIN,
                ownerFingerprint,
                sessionId,
                Long.toString(sequence));
    }

    long decode(String ownerFingerprint, String sessionId, String cursor, boolean allowLegacy) {
        requireOwner(ownerFingerprint);
        requireSession(sessionId);
        long sequence = EventV2Contract.cursorSequence(cursor, allowLegacy);
        if (cursor == null || cursor.isEmpty() || cursor.startsWith("e:")) {
            return sequence;
        }
        String expected = encode(ownerFingerprint, sessionId, sequence);
        if (!expected.equals(cursor)) {
            throw new SecurityException("CB_EVENT_V2: cursor owner or session mismatch");
        }
        return sequence;
    }

    private static void requireOwner(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new SecurityException("CB_EVENT_V2: durable owner fingerprint is required");
        }
    }

    private static void requireSession(String value) {
        if (value == null
                || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("CB_EVENT_V2: sessionId is invalid");
        }
    }
}
