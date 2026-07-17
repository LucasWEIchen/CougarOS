package com.centralbrain.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.RemoteException;

import com.centralbrain.sdk.event.EventContract;
import com.centralbrain.sdk.event.EventPage;
import com.centralbrain.sdk.event.ICentralBrainSessionEvents;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionPage;
import com.centralbrain.sdk.session.SessionQuery;
import com.centralbrain.sdk.session.SessionRequest;
import com.centralbrain.sdk.session.SessionSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public final class SessionClientTest {
    @Test
    public void reconnectReplaysAndResubscribesWithoutDuplicateEvents() {
        FakeTransport transport = new FakeTransport();
        RecordingConnection connection = new RecordingConnection();
        SessionClient client = new SessionClient(transport, Runnable::run, connection);
        assertTrue(client.connect());

        RecordingEvents events = new RecordingEvents();
        SessionHandle handle = client.openSession(request(), events);
        assertEquals(1, events.sequences.size());
        assertEquals(1L, events.sequences.get(0).longValue());
        assertEquals(1, events.replayCompleteCount);

        transport.disconnect();
        assertEquals(1, connection.disconnectedCount);
        assertTrue(client.reconnect());
        assertEquals(2, connection.connectedCount);
        assertTrue(connection.lastReconnect);
        assertEquals(1, events.sequences.size());

        assertTrue(client.cancelSession(
                handle,
                ICentralBrainSessionRuntime.CANCEL_REASON_USER));
        assertEquals(List.of(1L, 2L), events.sequences);
        client.close();
        client.close();
        assertThrows(ScenarioClient.Failure.class, client::reconnect);
    }

    @Test
    public void stoppedObserverRejectsLateCallbackRace() {
        FakeTransport transport = new FakeTransport();
        SessionClient client = new SessionClient(
                transport,
                Runnable::run,
                new RecordingConnection());
        client.connect();
        RecordingEvents events = new RecordingEvents();
        SessionHandle handle = client.openSession(request(), events);
        ScenarioTransport.EventSink stale = transport.sink;

        client.stopObserving(handle);
        stale.onEvent(transport.event(handle.sessionId, 2, "SessionStateChanged"));
        assertEquals(List.of(1L), events.sequences);
    }

    @Test
    public void protocolMismatchFailsClosed() {
        FakeTransport transport = new FakeTransport();
        transport.sessionHash = "bad";
        RecordingConnection connection = new RecordingConnection();
        SessionClient client = new SessionClient(transport, Runnable::run, connection);

        assertTrue(client.connect());
        assertFalse(client.isConnected());
        assertEquals(ScenarioClient.ERROR_PROTOCOL_MISMATCH, connection.failureCode);
        assertTrue(transport.closed);
    }

    @Test
    public void oversizedResumeCursorIsRejectedBeforeTransport() {
        FakeTransport transport = new FakeTransport();
        SessionClient client = new SessionClient(
                transport,
                Runnable::run,
                new RecordingConnection());
        client.connect();
        SessionHandle handle = client.openSession(request(), new RecordingEvents());

        assertThrows(IllegalArgumentException.class, () -> client.observeSession(
                handle,
                "x".repeat(EventContract.MAX_CURSOR_CHARS + 1),
                new RecordingEvents()));
    }

    private static SessionRequest request() {
        SessionRequest request = new SessionRequest();
        request.requestId = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
        request.scenarioId = "scene.fatigue.assist.v1";
        request.utterance = "I feel tired";
        request.source = ICentralBrainSessionRuntime.SOURCE_HMI_BUTTON;
        request.seatZone = ICentralBrainSessionRuntime.SEAT_ZONE_DRIVER;
        request.locale = "en-US";
        request.deadlineEpochMs = System.currentTimeMillis() + 60_000;
        return request;
    }

    private static final class RecordingConnection implements ScenarioClient.ConnectionListener {
        private int connectedCount;
        private int disconnectedCount;
        private boolean lastReconnect;
        private String failureCode = "";

        @Override
        public void onConnected(ScenarioClient client, boolean reconnected) {
            connectedCount++;
            lastReconnect = reconnected;
        }

        @Override
        public void onDisconnected() {
            disconnectedCount++;
        }

        @Override
        public void onConnectionFailed(String code, String message) {
            failureCode = code;
        }
    }

    private static final class RecordingEvents implements RuntimeEventListener {
        private final List<Long> sequences = new ArrayList<>();
        private int replayCompleteCount;

        @Override
        public void onEvent(RuntimeEvent event) {
            sequences.add(event.sequence);
        }

        @Override
        public void onReplayComplete(long lastSequence) {
            replayCompleteCount++;
        }
    }

    private static final class FakeTransport implements ScenarioTransport {
        private static final long NOW = 1_750_000_000_000L;
        private Listener listener;
        private boolean connected;
        private boolean closed;
        private String sessionHash = ICentralBrainSessionRuntime.INTERFACE_HASH;
        private final Map<String, SessionSnapshot> snapshots = new LinkedHashMap<>();
        private final Map<String, List<RuntimeEvent>> events = new LinkedHashMap<>();
        private EventSink sink;
        private String sinkSession = "";

        @Override
        public void setListener(Listener listener) {
            this.listener = listener;
        }

        @Override
        public boolean connect() {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            connected = true;
            listener.onConnected();
            return true;
        }

        @Override
        public boolean reconnect() {
            return connect();
        }

        @Override
        public boolean isConnected() {
            return connected && !closed;
        }

        void disconnect() {
            connected = false;
            sink = null;
            listener.onDisconnected();
        }

        @Override
        public int getSessionProtocolVersion() {
            return ICentralBrainSessionRuntime.INTERFACE_VERSION;
        }

        @Override
        public String getSessionProtocolHash() {
            return sessionHash;
        }

        @Override
        public int getEventProtocolVersion() {
            return ICentralBrainSessionEvents.INTERFACE_VERSION;
        }

        @Override
        public String getEventProtocolHash() {
            return ICentralBrainSessionEvents.INTERFACE_HASH;
        }

        @Override
        public SessionHandle openSession(SessionRequest request) {
            String sessionId = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";
            SessionHandle handle = new SessionHandle();
            handle.sessionId = sessionId;
            handle.acceptedAtEpochMs = NOW;
            handle.expiresAtEpochMs = NOW + 60_000;
            SessionSnapshot snapshot = new SessionSnapshot();
            snapshot.sessionId = sessionId;
            snapshot.requestId = request.requestId;
            snapshot.scenarioId = request.scenarioId;
            snapshot.state = ICentralBrainSessionRuntime.SESSION_STATE_CREATED;
            snapshot.lastEventSequence = 1;
            snapshot.createdAtEpochMs = NOW;
            snapshot.updatedAtEpochMs = NOW;
            snapshot.deadlineEpochMs = NOW + 60_000;
            snapshot.summary = "accepted";
            snapshots.put(sessionId, snapshot);
            events.put(sessionId, new ArrayList<>(List.of(
                    event(sessionId, 1, "ScenarioRequested"))));
            return handle;
        }

        @Override
        public SessionSnapshot getSession(SessionHandle handle) {
            return snapshots.get(handle.sessionId);
        }

        @Override
        public SessionPage listSessions(SessionQuery query) {
            SessionPage page = new SessionPage();
            page.sessions = snapshots.values().toArray(new SessionSnapshot[0]);
            page.generatedAtEpochMs = NOW;
            return page;
        }

        @Override
        public boolean cancelSession(SessionHandle handle, int reasonCode) {
            SessionSnapshot snapshot = snapshots.get(handle.sessionId);
            if (snapshot.state == ICentralBrainSessionRuntime.SESSION_STATE_CANCELLED) {
                return false;
            }
            snapshot.state = ICentralBrainSessionRuntime.SESSION_STATE_CANCELLED;
            snapshot.updatedAtEpochMs = NOW + 1;
            snapshot.lastEventSequence = 2;
            RuntimeEvent event = event(handle.sessionId, 2, "SessionStateChanged");
            events.get(handle.sessionId).add(event);
            if (sink != null && sinkSession.equals(handle.sessionId)) {
                sink.onEvent(event);
            }
            return true;
        }

        @Override
        public EventPage getEvents(String sessionId, String cursor, int limit) {
            long after = cursor.isEmpty() ? 0 : Long.parseLong(cursor.substring(2));
            List<RuntimeEvent> all = events.get(sessionId);
            List<RuntimeEvent> selected = new ArrayList<>();
            for (RuntimeEvent event : all) {
                if (event.sequence > after && selected.size() < limit) {
                    selected.add(event);
                }
            }
            EventPage page = new EventPage();
            page.sessionId = sessionId;
            page.requestCursor = cursor;
            page.afterSequence = after;
            page.events = selected.toArray(new RuntimeEvent[0]);
            page.nextSequence = selected.isEmpty()
                    ? after : selected.get(selected.size() - 1).sequence;
            page.hasMore = page.nextSequence < all.size();
            page.nextCursor = page.hasMore ? "e:" + page.nextSequence : "";
            page.generatedAtEpochMs = NOW + 10;
            return page;
        }

        @Override
        public boolean registerSessionCallback(String sessionId, String cursor, EventSink sink) {
            this.sink = sink;
            this.sinkSession = sessionId;
            EventPage replay = getEvents(sessionId, cursor, 100);
            for (RuntimeEvent event : replay.events) {
                sink.onEvent(event);
            }
            return true;
        }

        @Override
        public boolean unregisterSessionCallback(String sessionId, EventSink sink) {
            if (this.sink == sink) {
                this.sink = null;
            }
            return true;
        }

        @Override
        public void close() {
            closed = true;
            connected = false;
            sink = null;
        }

        RuntimeEvent event(String sessionId, long sequence, String type) {
            RuntimeEvent event = new RuntimeEvent();
            event.eventId = sequence == 1
                    ? "37b459a6-4373-4b0b-b2a4-44df3adb2aef"
                    : "24ab900d-aa8b-41e7-a053-39351c41f001";
            event.sequence = sequence;
            event.sessionId = sessionId;
            event.parentEventId = sequence == 1
                    ? "" : "37b459a6-4373-4b0b-b2a4-44df3adb2aef";
            event.parentSequence = sequence == 1 ? 0 : 1;
            event.type = type;
            event.source = EventContract.SOURCE_RUNTIME;
            event.occurredAtEpochMs = NOW + sequence;
            event.privacyClass = EventContract.PRIVACY_INTERNAL;
            event.payloadDigest = "a".repeat(64);
            event.eventDigest = sequence == 1 ? "b".repeat(64) : "c".repeat(64);
            event.payloadKind = EventContract.PAYLOAD_NONE;
            return event;
        }
    }
}
