package com.centralbrain.runtime.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class SessionEventCursorCodecTest {
    private static final String OWNER = "a".repeat(64);
    private static final String OTHER_OWNER = "b".repeat(64);
    private static final String SESSION = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";
    private static final String OTHER_SESSION = "8d595630-2255-4f4d-ac0f-26a20ee96f29";

    @Test
    public void roundTripsOwnerSessionAndSequence() {
        SessionEventCursorCodec codec = new SessionEventCursorCodec();
        String cursor = codec.encode(OWNER, SESSION, 42);
        assertEquals(42, codec.decode(OWNER, SESSION, cursor, false));
    }

    @Test
    public void rejectsOwnerAndSessionReplay() {
        SessionEventCursorCodec codec = new SessionEventCursorCodec();
        String cursor = codec.encode(OWNER, SESSION, 7);
        assertThrows(SecurityException.class,
                () -> codec.decode(OTHER_OWNER, SESSION, cursor, false));
        assertThrows(SecurityException.class,
                () -> codec.decode(OWNER, OTHER_SESSION, cursor, false));
    }

    @Test
    public void permitsBoundedLegacyMigrationOnlyWhenRequested() {
        SessionEventCursorCodec codec = new SessionEventCursorCodec();
        assertEquals(0, codec.decode(OWNER, SESSION, "", true));
        assertEquals(5, codec.decode(OWNER, SESSION, "e:5", true));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(OWNER, SESSION, "e:5", false));
    }
}
