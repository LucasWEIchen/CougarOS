package com.centralbrain.client2;

import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionContract;
import com.centralbrain.sdk.session.SessionHandle;

/** Immutable, render-ready Client2 HMI state. Raw user/model text is never checkpointed. */
public final class CockpitHmiState {
    public enum PanelVisibility { HIDDEN, VISIBLE }

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        FAILED,
        CLOSED
    }

    public enum SurfaceStage { INTENT, PLAN, EXECUTION, RESULT }

    public enum DeviceDrawer { CLOSED, HVAC, SEAT, ENGINEER }

    private final long revision;
    private final PanelVisibility panelVisibility;
    private final ConnectionState connectionState;
    private final SurfaceStage surfaceStage;
    private final DeviceDrawer deviceDrawer;
    private final PanelPresentationMode presentationMode;
    private final CockpitHvacState hvacState;
    private final CockpitSeatState seatState;
    private final CockpitExecutionTimeline executionTimeline;
    private final CockpitRecoveryState recoveryState;
    private final CockpitEngineerState engineerState;
    private final String uiScenarioId;
    private final String canonicalScenarioId;
    private final int handleSchemaVersion;
    private final String sessionId;
    private final long acceptedAtEpochMs;
    private final long expiresAtEpochMs;
    private final int sessionState;
    private final long lastEventSequence;
    private final String resumeCursor;
    private final String snapshotSummary;
    private final String assistantDisplayText;
    private final String lastEventType;
    private final String errorCode;
    private final String errorMessage;
    private final boolean replayComplete;
    private final boolean terminal;

    private CockpitHmiState(Builder builder) {
        revision = builder.revision;
        panelVisibility = builder.panelVisibility;
        connectionState = builder.connectionState;
        surfaceStage = builder.surfaceStage;
        deviceDrawer = builder.deviceDrawer;
        presentationMode = builder.presentationMode;
        hvacState = builder.hvacState;
        seatState = builder.seatState;
        executionTimeline = builder.executionTimeline;
        recoveryState = builder.recoveryState;
        engineerState = builder.engineerState;
        uiScenarioId = builder.uiScenarioId;
        canonicalScenarioId = builder.canonicalScenarioId;
        handleSchemaVersion = builder.handleSchemaVersion;
        sessionId = builder.sessionId;
        acceptedAtEpochMs = builder.acceptedAtEpochMs;
        expiresAtEpochMs = builder.expiresAtEpochMs;
        sessionState = builder.sessionState;
        lastEventSequence = builder.lastEventSequence;
        resumeCursor = builder.resumeCursor;
        snapshotSummary = builder.snapshotSummary;
        assistantDisplayText = builder.assistantDisplayText;
        lastEventType = builder.lastEventType;
        errorCode = builder.errorCode;
        errorMessage = builder.errorMessage;
        replayComplete = builder.replayComplete;
        terminal = builder.terminal;
    }

    public static CockpitHmiState initial() {
        return new Builder().build();
    }

    public long getRevision() {
        return revision;
    }

    public PanelVisibility getPanelVisibility() {
        return panelVisibility;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public SurfaceStage getSurfaceStage() {
        return surfaceStage;
    }

    public DeviceDrawer getDeviceDrawer() {
        return deviceDrawer;
    }

    public PanelPresentationMode getPresentationMode() {
        return presentationMode;
    }

    public CockpitHvacState getHvacState() {
        return hvacState;
    }

    public CockpitSeatState getSeatState() {
        return seatState;
    }

    public CockpitExecutionTimeline getExecutionTimeline() {
        return executionTimeline;
    }

    public CockpitRecoveryState getRecoveryState() {
        return recoveryState;
    }

    public CockpitEngineerState getEngineerState() {
        return engineerState;
    }

    public String getUiScenarioId() {
        return uiScenarioId;
    }

    public String getCanonicalScenarioId() {
        return canonicalScenarioId;
    }

    public int getSessionState() {
        return sessionState;
    }

    public long getLastEventSequence() {
        return lastEventSequence;
    }

    public String getResumeCursor() {
        return resumeCursor;
    }

    public String getLastEventType() {
        return lastEventType;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isReplayComplete() {
        return replayComplete;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean hasSession() {
        return !sessionId.isEmpty();
    }

    public boolean hasResumableSession(long nowEpochMs) {
        return hasSession() && !terminal && expiresAtEpochMs >= nowEpochMs;
    }

    public SessionHandle toSessionHandle() {
        if (!hasSession()) {
            return null;
        }
        SessionHandle handle = new SessionHandle();
        handle.schemaVersion = handleSchemaVersion;
        handle.sessionId = sessionId;
        handle.acceptedAtEpochMs = acceptedAtEpochMs;
        handle.expiresAtEpochMs = expiresAtEpochMs;
        return handle;
    }

    public String renderText() {
        if (!errorMessage.isEmpty()) {
            return errorCode.isEmpty()
                    ? "中央大脑暂不可用: " + errorMessage
                    : "中央大脑暂不可用 [" + errorCode + "]: " + errorMessage;
        }
        if (!assistantDisplayText.isEmpty()) {
            return assistantDisplayText;
        }
        if (!snapshotSummary.isEmpty()) {
            return snapshotSummary;
        }
        switch (connectionState) {
            case CONNECTING:
                return "正在连接中央大脑...";
            case RECONNECTING:
                return "正在恢复会话...";
            case CONNECTED:
                return replayComplete ? "会话已同步" : "正在同步会话...";
            case CLOSED:
                return "会话已关闭";
            case FAILED:
                return "中央大脑暂不可用";
            case DISCONNECTED:
            default:
                return "选择测试场景...";
        }
    }

    public Checkpoint checkpoint() {
        boolean resumable = hasSession() && !terminal;
        return new Checkpoint(
                panelVisibility == PanelVisibility.VISIBLE,
                uiScenarioId,
                resumable ? canonicalScenarioId : "",
                resumable ? handleSchemaVersion : SessionContract.SCHEMA_VERSION,
                resumable ? sessionId : "",
                resumable ? acceptedAtEpochMs : 0,
                resumable ? expiresAtEpochMs : 0,
                resumable ? lastEventSequence : 0,
                resumable ? resumeCursor : "");
    }

    static Builder builder(CockpitHmiState source) {
        return new Builder(source);
    }

    /** Bounded process-recreation state. It intentionally excludes all display text. */
    public static final class Checkpoint {
        public final boolean panelVisible;
        public final String uiScenarioId;
        public final String canonicalScenarioId;
        public final int handleSchemaVersion;
        public final String sessionId;
        public final long acceptedAtEpochMs;
        public final long expiresAtEpochMs;
        public final long lastEventSequence;
        public final String resumeCursor;

        public Checkpoint(
                boolean panelVisible,
                String uiScenarioId,
                String canonicalScenarioId,
                int handleSchemaVersion,
                String sessionId,
                long acceptedAtEpochMs,
                long expiresAtEpochMs,
                long lastEventSequence,
                String resumeCursor) {
            this.panelVisible = panelVisible;
            this.uiScenarioId = bounded(uiScenarioId, 96);
            this.canonicalScenarioId = bounded(canonicalScenarioId, 96);
            this.handleSchemaVersion = handleSchemaVersion;
            this.sessionId = bounded(sessionId, SessionContract.MAX_SESSION_ID_CHARS);
            this.acceptedAtEpochMs = acceptedAtEpochMs;
            this.expiresAtEpochMs = expiresAtEpochMs;
            this.lastEventSequence = Math.max(0, lastEventSequence);
            this.resumeCursor = bounded(resumeCursor, SessionContract.MAX_CURSOR_CHARS);
        }

        public boolean hasSession() {
            return !sessionId.isEmpty();
        }
    }

    static final class Builder {
        long revision;
        PanelVisibility panelVisibility = PanelVisibility.HIDDEN;
        ConnectionState connectionState = ConnectionState.DISCONNECTED;
        SurfaceStage surfaceStage = SurfaceStage.INTENT;
        DeviceDrawer deviceDrawer = DeviceDrawer.CLOSED;
        PanelPresentationMode presentationMode = PanelPresentationMode.MOVING_RESTRICTED;
        CockpitHvacState hvacState = CockpitHvacState.initial();
        CockpitSeatState seatState = CockpitSeatState.initial();
        CockpitExecutionTimeline executionTimeline = CockpitExecutionTimeline.initial();
        CockpitRecoveryState recoveryState = CockpitRecoveryState.initial();
        CockpitEngineerState engineerState = CockpitEngineerState.unavailable();
        String uiScenarioId = "";
        String canonicalScenarioId = "";
        int handleSchemaVersion = SessionContract.SCHEMA_VERSION;
        String sessionId = "";
        long acceptedAtEpochMs;
        long expiresAtEpochMs;
        int sessionState = ICentralBrainSessionRuntime.SESSION_STATE_CREATED;
        long lastEventSequence;
        String resumeCursor = "";
        String snapshotSummary = "";
        String assistantDisplayText = "";
        String lastEventType = "";
        String errorCode = "";
        String errorMessage = "";
        boolean replayComplete;
        boolean terminal;

        Builder() {}

        Builder(CockpitHmiState source) {
            revision = source.revision;
            panelVisibility = source.panelVisibility;
            connectionState = source.connectionState;
            surfaceStage = source.surfaceStage;
            deviceDrawer = source.deviceDrawer;
            presentationMode = source.presentationMode;
            hvacState = source.hvacState;
            seatState = source.seatState;
            executionTimeline = source.executionTimeline;
            recoveryState = source.recoveryState;
            engineerState = source.engineerState;
            uiScenarioId = source.uiScenarioId;
            canonicalScenarioId = source.canonicalScenarioId;
            handleSchemaVersion = source.handleSchemaVersion;
            sessionId = source.sessionId;
            acceptedAtEpochMs = source.acceptedAtEpochMs;
            expiresAtEpochMs = source.expiresAtEpochMs;
            sessionState = source.sessionState;
            lastEventSequence = source.lastEventSequence;
            resumeCursor = source.resumeCursor;
            snapshotSummary = source.snapshotSummary;
            assistantDisplayText = source.assistantDisplayText;
            lastEventType = source.lastEventType;
            errorCode = source.errorCode;
            errorMessage = source.errorMessage;
            replayComplete = source.replayComplete;
            terminal = source.terminal;
        }

        CockpitHmiState buildNext() {
            revision = revision == Long.MAX_VALUE ? Long.MAX_VALUE : revision + 1;
            return build();
        }

        CockpitHmiState build() {
            if (presentationMode == null) {
                throw new IllegalStateException("presentation mode missing");
            }
            if (hvacState == null) {
                throw new IllegalStateException("HVAC state missing");
            }
            if (seatState == null) {
                throw new IllegalStateException("seat state missing");
            }
            if (executionTimeline == null) {
                throw new IllegalStateException("execution timeline missing");
            }
            if (recoveryState == null) {
                throw new IllegalStateException("recovery state missing");
            }
            if (engineerState == null) {
                throw new IllegalStateException("engineer state missing");
            }
            uiScenarioId = bounded(uiScenarioId, 96);
            canonicalScenarioId = bounded(canonicalScenarioId, 96);
            sessionId = bounded(sessionId, SessionContract.MAX_SESSION_ID_CHARS);
            resumeCursor = bounded(resumeCursor, SessionContract.MAX_CURSOR_CHARS);
            snapshotSummary = bounded(snapshotSummary, SessionContract.MAX_SUMMARY_CHARS);
            assistantDisplayText = bounded(assistantDisplayText, 1024);
            lastEventType = bounded(lastEventType, 64);
            errorCode = bounded(errorCode, 64);
            errorMessage = bounded(errorMessage, 256);
            return new CockpitHmiState(this);
        }
    }

    private static String bounded(String value, int maxChars) {
        String safe = value == null ? "" : value;
        return safe.length() <= maxChars ? safe : safe.substring(0, maxChars);
    }
}
