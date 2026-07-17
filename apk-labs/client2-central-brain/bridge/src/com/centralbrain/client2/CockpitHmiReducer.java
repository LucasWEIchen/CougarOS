package com.centralbrain.client2;

import com.centralbrain.sdk.event.EventContract;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.SessionContract;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionSnapshot;

import java.util.Objects;

/** The only state transition authority for the Client2 cockpit overlay. */
public final class CockpitHmiReducer {
    private CockpitHmiReducer() {}

    public static CockpitHmiState reduce(CockpitHmiState current, Event event) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(event, "event");
        CockpitHmiState.Builder next = CockpitHmiState.builder(current);
        switch (event.type) {
            case PANEL_VISIBILITY:
                next.panelVisibility = event.flag
                        ? CockpitHmiState.PanelVisibility.VISIBLE
                        : CockpitHmiState.PanelVisibility.HIDDEN;
                if (!event.flag) {
                    next.deviceDrawer = CockpitHmiState.DeviceDrawer.CLOSED;
                }
                return next.buildNext();
            case SURFACE_SELECTED:
                next.surfaceStage = event.surfaceStage;
                next.deviceDrawer = CockpitHmiState.DeviceDrawer.CLOSED;
                return next.buildNext();
            case DRAWER_SELECTED:
                next.deviceDrawer = event.deviceDrawer;
                return next.buildNext();
            case HVAC_DESIRED_CHANGED:
                CockpitHvacState changed = current.getHvacState().desiredChanged(event.hvacIntent);
                if (changed == current.getHvacState()) {
                    return current;
                }
                next.hvacState = changed;
                next.deviceDrawer = CockpitHmiState.DeviceDrawer.HVAC;
                return next.buildNext();
            case HVAC_MANUAL_SUBMITTED:
                next.hvacState = current.getHvacState().submitted(event.hvacRevision);
                next.connectionState = CockpitHmiState.ConnectionState.CONNECTING;
                next.surfaceStage = CockpitHmiState.SurfaceStage.PLAN;
                next.deviceDrawer = CockpitHmiState.DeviceDrawer.HVAC;
                next.uiScenarioId = "manual.hvac";
                next.canonicalScenarioId = "";
                next.handleSchemaVersion = SessionContract.SCHEMA_VERSION;
                next.sessionId = "";
                next.acceptedAtEpochMs = 0;
                next.expiresAtEpochMs = 0;
                next.sessionState = 0;
                next.lastEventSequence = 0;
                next.resumeCursor = "";
                next.snapshotSummary = "";
                next.assistantDisplayText = "";
                next.lastEventType = "";
                next.errorCode = "";
                next.errorMessage = "";
                next.replayComplete = false;
                next.terminal = false;
                return next.buildNext();
            case SEAT_SAFETY_CONTEXT_CHANGED:
                next.seatState = current.getSeatState().safetyContextChanged(event.seatSafetyContext);
                return next.buildNext();
            case SEAT_DESIRED_CHANGED:
                CockpitSeatState seatChanged = current.getSeatState().desiredChanged(event.seatIntent);
                if (seatChanged == current.getSeatState()) {
                    return current;
                }
                next.seatState = seatChanged;
                next.deviceDrawer = CockpitHmiState.DeviceDrawer.SEAT;
                return next.buildNext();
            case SEAT_MANUAL_SUBMITTED:
                next.seatState = current.getSeatState().submitted(event.seatRevision);
                next.connectionState = CockpitHmiState.ConnectionState.CONNECTING;
                next.surfaceStage = CockpitHmiState.SurfaceStage.PLAN;
                next.deviceDrawer = CockpitHmiState.DeviceDrawer.SEAT;
                next.uiScenarioId = "manual.seat";
                next.canonicalScenarioId = "";
                next.handleSchemaVersion = SessionContract.SCHEMA_VERSION;
                next.sessionId = "";
                next.acceptedAtEpochMs = 0;
                next.expiresAtEpochMs = 0;
                next.sessionState = 0;
                next.lastEventSequence = 0;
                next.resumeCursor = "";
                next.snapshotSummary = "";
                next.assistantDisplayText = "";
                next.lastEventType = "";
                next.errorCode = "";
                next.errorMessage = "";
                next.replayComplete = false;
                next.terminal = false;
                return next.buildNext();
            case SCENARIO_SUBMITTED:
                next.connectionState = CockpitHmiState.ConnectionState.CONNECTING;
                next.surfaceStage = CockpitHmiState.SurfaceStage.PLAN;
                next.deviceDrawer = CockpitHmiState.DeviceDrawer.CLOSED;
                next.uiScenarioId = event.uiScenarioId;
                next.canonicalScenarioId = "";
                next.handleSchemaVersion = SessionContract.SCHEMA_VERSION;
                next.sessionId = "";
                next.acceptedAtEpochMs = 0;
                next.expiresAtEpochMs = 0;
                next.sessionState = 0;
                next.lastEventSequence = 0;
                next.resumeCursor = "";
                next.snapshotSummary = "";
                next.assistantDisplayText = "";
                next.lastEventType = "";
                next.errorCode = "";
                next.errorMessage = "";
                next.replayComplete = false;
                next.terminal = false;
                return next.buildNext();
            case CONNECTION_CHANGED:
                next.connectionState = event.flag
                        ? CockpitHmiState.ConnectionState.CONNECTED
                        : CockpitHmiState.ConnectionState.RECONNECTING;
                next.errorCode = "";
                next.errorMessage = "";
                return next.buildNext();
            case SESSION_OPENED:
                if (event.handle == null) {
                    return current;
                }
                next.handleSchemaVersion = event.handle.schemaVersion;
                next.sessionId = event.handle.sessionId;
                next.acceptedAtEpochMs = event.handle.acceptedAtEpochMs;
                next.expiresAtEpochMs = event.handle.expiresAtEpochMs;
                next.canonicalScenarioId = event.canonicalScenarioId;
                next.connectionState = CockpitHmiState.ConnectionState.CONNECTED;
                if ("manual.hvac".equals(current.getUiScenarioId())) {
                    next.hvacState = current.getHvacState().requestAccepted();
                } else if ("manual.seat".equals(current.getUiScenarioId())) {
                    next.seatState = current.getSeatState().requestAccepted();
                }
                next.errorCode = "";
                next.errorMessage = "";
                return next.buildNext();
            case SNAPSHOT:
                if (!sameSession(current, event.sessionId)) {
                    return current;
                }
                next.canonicalScenarioId = event.canonicalScenarioId;
                next.sessionState = event.sessionState;
                next.snapshotSummary = event.text;
                next.terminal = event.terminal;
                next.connectionState = event.terminal
                        ? CockpitHmiState.ConnectionState.CLOSED
                        : CockpitHmiState.ConnectionState.CONNECTED;
                if ("manual.hvac".equals(current.getUiScenarioId())) {
                    next.hvacState = current.getHvacState().requestAccepted();
                } else if ("manual.seat".equals(current.getUiScenarioId())) {
                    next.seatState = current.getSeatState().requestAccepted();
                }
                next.errorCode = "";
                next.errorMessage = "";
                return next.buildNext();
            case RUNTIME_EVENT:
                if (!sameSession(current, event.sessionId)
                        || event.sequence <= current.getLastEventSequence()) {
                    return current;
                }
                if (current.getLastEventSequence() > 0
                        && event.sequence != current.getLastEventSequence() + 1) {
                    next.connectionState = CockpitHmiState.ConnectionState.FAILED;
                    next.errorCode = "CB_HMI_EVENT_GAP";
                    next.errorMessage = "event sequence gap";
                    return next.buildNext();
                }
                next.lastEventSequence = event.sequence;
                next.lastEventType = event.eventType;
                if (!event.text.isEmpty()) {
                    next.assistantDisplayText = event.text;
                }
                return next.buildNext();
            case REPLAY_COMPLETE:
                if (!sameSession(current, event.sessionId)
                        || event.sequence < current.getLastEventSequence()) {
                    return current;
                }
                next.replayComplete = true;
                next.connectionState = current.isTerminal()
                        ? CockpitHmiState.ConnectionState.CLOSED
                        : CockpitHmiState.ConnectionState.CONNECTED;
                next.errorCode = "";
                next.errorMessage = "";
                return next.buildNext();
            case OVERFLOW:
                if (!sameSession(current, event.sessionId)) {
                    return current;
                }
                next.resumeCursor = event.resumeCursor;
                next.replayComplete = false;
                next.connectionState = CockpitHmiState.ConnectionState.RECONNECTING;
                return next.buildNext();
            case STREAM_CLOSED:
                if (event.sessionId.length() > 0 && !sameSession(current, event.sessionId)) {
                    return current;
                }
                next.resumeCursor = event.resumeCursor;
                next.connectionState = CockpitHmiState.ConnectionState.CLOSED;
                next.terminal = true;
                next.errorCode = "CB_HMI_STREAM_CLOSED";
                next.errorMessage = "event stream closed: " + event.reasonCode;
                return next.buildNext();
            case FAILURE:
                if (event.sessionId.length() > 0 && !sameSession(current, event.sessionId)) {
                    return current;
                }
                next.connectionState = CockpitHmiState.ConnectionState.FAILED;
                next.errorCode = event.errorCode;
                next.errorMessage = event.text;
                next.replayComplete = false;
                if ("manual.hvac".equals(current.getUiScenarioId())) {
                    next.hvacState = current.getHvacState().requestFailed();
                } else if ("manual.seat".equals(current.getUiScenarioId())) {
                    next.seatState = current.getSeatState().requestFailed();
                }
                return next.buildNext();
            case DETACHED:
                next.connectionState = current.hasSession() && !current.isTerminal()
                        ? CockpitHmiState.ConnectionState.DISCONNECTED
                        : current.getConnectionState();
                return next.buildNext();
            case RESTORED:
                CockpitHmiState.Checkpoint checkpoint = event.checkpoint;
                next.panelVisibility = checkpoint.panelVisible
                        ? CockpitHmiState.PanelVisibility.VISIBLE
                        : CockpitHmiState.PanelVisibility.HIDDEN;
                next.surfaceStage = CockpitHmiState.SurfaceStage.INTENT;
                next.deviceDrawer = CockpitHmiState.DeviceDrawer.CLOSED;
                next.uiScenarioId = checkpoint.uiScenarioId;
                next.canonicalScenarioId = checkpoint.canonicalScenarioId;
                next.handleSchemaVersion = checkpoint.handleSchemaVersion;
                next.sessionId = checkpoint.sessionId;
                next.acceptedAtEpochMs = checkpoint.acceptedAtEpochMs;
                next.expiresAtEpochMs = checkpoint.expiresAtEpochMs;
                next.lastEventSequence = checkpoint.lastEventSequence;
                next.resumeCursor = checkpoint.resumeCursor;
                next.connectionState = checkpoint.hasSession()
                        ? CockpitHmiState.ConnectionState.RECONNECTING
                        : CockpitHmiState.ConnectionState.DISCONNECTED;
                next.snapshotSummary = "";
                next.assistantDisplayText = "";
                next.lastEventType = "";
                next.errorCode = "";
                next.errorMessage = "";
                next.replayComplete = false;
                next.terminal = false;
                return next.buildNext();
            default:
                throw new IllegalArgumentException("unsupported HMI event");
        }
    }

    private static boolean sameSession(CockpitHmiState state, String sessionId) {
        SessionHandle handle = state.toSessionHandle();
        return handle != null && handle.sessionId.equals(sessionId);
    }

    public static final class Event {
        private enum Type {
            PANEL_VISIBILITY,
            SURFACE_SELECTED,
            DRAWER_SELECTED,
            HVAC_DESIRED_CHANGED,
            HVAC_MANUAL_SUBMITTED,
            SEAT_SAFETY_CONTEXT_CHANGED,
            SEAT_DESIRED_CHANGED,
            SEAT_MANUAL_SUBMITTED,
            SCENARIO_SUBMITTED,
            CONNECTION_CHANGED,
            SESSION_OPENED,
            SNAPSHOT,
            RUNTIME_EVENT,
            REPLAY_COMPLETE,
            OVERFLOW,
            STREAM_CLOSED,
            FAILURE,
            DETACHED,
            RESTORED
        }

        private final Type type;
        private boolean flag;
        private CockpitHmiState.SurfaceStage surfaceStage;
        private CockpitHmiState.DeviceDrawer deviceDrawer;
        private HvacControlIntent hvacIntent;
        private long hvacRevision;
        private CockpitSeatState.SafetyContext seatSafetyContext;
        private SeatControlIntent seatIntent;
        private long seatRevision;
        private String uiScenarioId = "";
        private String canonicalScenarioId = "";
        private String sessionId = "";
        private long sequence;
        private int sessionState;
        private int reasonCode;
        private String resumeCursor = "";
        private String eventType = "";
        private String text = "";
        private String errorCode = "";
        private boolean terminal;
        private SessionHandle handle;
        private CockpitHmiState.Checkpoint checkpoint;

        private Event(Type type) {
            this.type = type;
        }

        public static Event panelVisibility(boolean visible) {
            Event event = new Event(Type.PANEL_VISIBILITY);
            event.flag = visible;
            return event;
        }

        public static Event surfaceSelected(CockpitHmiState.SurfaceStage surfaceStage) {
            Event event = new Event(Type.SURFACE_SELECTED);
            event.surfaceStage = Objects.requireNonNull(surfaceStage, "surfaceStage");
            return event;
        }

        public static Event drawerSelected(CockpitHmiState.DeviceDrawer deviceDrawer) {
            Event event = new Event(Type.DRAWER_SELECTED);
            event.deviceDrawer = Objects.requireNonNull(deviceDrawer, "deviceDrawer");
            return event;
        }

        public static Event hvacDesiredChanged(HvacControlIntent intent) {
            Event event = new Event(Type.HVAC_DESIRED_CHANGED);
            event.hvacIntent = Objects.requireNonNull(intent, "intent");
            return event;
        }

        public static Event hvacManualSubmitted(long desiredRevision) {
            Event event = new Event(Type.HVAC_MANUAL_SUBMITTED);
            event.hvacRevision = desiredRevision;
            return event;
        }

        public static Event seatSafetyContextChanged(CockpitSeatState.SafetyContext context) {
            Event event = new Event(Type.SEAT_SAFETY_CONTEXT_CHANGED);
            event.seatSafetyContext = Objects.requireNonNull(context, "context");
            return event;
        }

        public static Event seatDesiredChanged(SeatControlIntent intent) {
            Event event = new Event(Type.SEAT_DESIRED_CHANGED);
            event.seatIntent = Objects.requireNonNull(intent, "intent");
            return event;
        }

        public static Event seatManualSubmitted(long desiredRevision) {
            Event event = new Event(Type.SEAT_MANUAL_SUBMITTED);
            event.seatRevision = desiredRevision;
            return event;
        }

        public static Event scenarioSubmitted(String uiScenarioId) {
            Event event = new Event(Type.SCENARIO_SUBMITTED);
            event.uiScenarioId = bounded(uiScenarioId, 96);
            return event;
        }

        public static Event connectionChanged(boolean connected, boolean reconnected) {
            Event event = new Event(Type.CONNECTION_CHANGED);
            event.flag = connected;
            return event;
        }

        public static Event sessionOpened(SessionHandle handle, String canonicalScenarioId) {
            SessionContract.validateHandle(handle);
            Event event = new Event(Type.SESSION_OPENED);
            event.handle = copy(handle);
            event.canonicalScenarioId = bounded(canonicalScenarioId, 96);
            return event;
        }

        public static Event snapshot(SessionSnapshot snapshot) {
            SessionContract.validateSnapshot(snapshot);
            Event event = new Event(Type.SNAPSHOT);
            event.sessionId = snapshot.sessionId;
            event.canonicalScenarioId = snapshot.scenarioId;
            event.sessionState = snapshot.state;
            event.text = snapshot.summary;
            event.terminal = SessionContract.isTerminalState(snapshot.state);
            return event;
        }

        public static Event runtimeEvent(RuntimeEvent runtimeEvent) {
            EventContract.validateEvent(runtimeEvent);
            Event event = new Event(Type.RUNTIME_EVENT);
            event.sessionId = runtimeEvent.sessionId;
            event.sequence = runtimeEvent.sequence;
            event.eventType = runtimeEvent.type;
            if (runtimeEvent.payloadKind == EventContract.PAYLOAD_MESSAGE
                    && runtimeEvent.message != null
                    && runtimeEvent.message.role == EventContract.MESSAGE_ASSISTANT) {
                event.text = runtimeEvent.message.displayText;
            }
            return event;
        }

        public static Event replayComplete(SessionHandle handle, long lastSequence) {
            SessionContract.validateHandle(handle);
            Event event = new Event(Type.REPLAY_COMPLETE);
            event.sessionId = handle.sessionId;
            event.sequence = Math.max(0, lastSequence);
            return event;
        }

        public static Event overflow(SessionHandle handle, String resumeCursor) {
            SessionContract.validateHandle(handle);
            Event event = new Event(Type.OVERFLOW);
            event.sessionId = handle.sessionId;
            event.resumeCursor = bounded(resumeCursor, SessionContract.MAX_CURSOR_CHARS);
            return event;
        }

        public static Event streamClosed(
                SessionHandle handle,
                int reasonCode,
                String resumeCursor) {
            Event event = new Event(Type.STREAM_CLOSED);
            event.sessionId = handle == null ? "" : copy(handle).sessionId;
            event.reasonCode = reasonCode;
            event.resumeCursor = bounded(resumeCursor, SessionContract.MAX_CURSOR_CHARS);
            return event;
        }

        public static Event failure(SessionHandle handle, String code, String message) {
            Event event = new Event(Type.FAILURE);
            event.sessionId = handle == null ? "" : copy(handle).sessionId;
            event.errorCode = bounded(code, 64);
            event.text = bounded(message, 256);
            return event;
        }

        public static Event detached() {
            return new Event(Type.DETACHED);
        }

        public static Event restored(CockpitHmiState.Checkpoint checkpoint) {
            Event event = new Event(Type.RESTORED);
            event.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
            return event;
        }

        private static SessionHandle copy(SessionHandle original) {
            SessionHandle copy = new SessionHandle();
            copy.schemaVersion = original.schemaVersion;
            copy.sessionId = original.sessionId;
            copy.acceptedAtEpochMs = original.acceptedAtEpochMs;
            copy.expiresAtEpochMs = original.expiresAtEpochMs;
            return copy;
        }

        private static String bounded(String value, int maxChars) {
            String safe = value == null ? "" : value;
            return safe.length() <= maxChars ? safe : safe.substring(0, maxChars);
        }
    }
}
