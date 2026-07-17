package com.centralbrain.client2;

import android.app.Activity;
import android.util.Log;

import com.centralbrain.sdk.RuntimeEventListener;
import com.centralbrain.sdk.ScenarioClient;
import com.centralbrain.sdk.SessionClient;
import com.centralbrain.sdk.event.ActionEvent;
import com.centralbrain.sdk.event.EventContract;
import com.centralbrain.sdk.event.MessageEvent;
import com.centralbrain.sdk.event.ObservationEvent;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionContract;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionRequest;
import com.centralbrain.sdk.session.SessionSnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Typed Session/Event bridge embedded into the isolated Client2 debug APK as classes2.dex. */
public final class Client2ScenarioBridge {
    private static final String TAG = "CbClient2Session";
    private static final long DEADLINE_MS = 10_000L;
    private static final Object LEGACY_LOCK = new Object();
    private static final Map<String, String> SCENARIOS = scenarioAliases();

    private static Submission legacySubmission;

    private Client2ScenarioBridge() {}

    /** Lifecycle owner returned by the primary Session/Event API. */
    public interface SessionConnection extends AutoCloseable {
        boolean isConnected();

        SessionHandle getSessionHandle();

        boolean cancel();

        @Override
        void close();
    }

    /**
     * Opens the primary typed Session/Event stream. The caller owns and must close the result.
     * Returns {@code null} after a validation or initial transport failure.
     */
    public static SessionConnection openSession(
            Activity activity,
            String scenarioId,
            String userText,
            ScenarioCallback callback) {
        return openSessionInternal(activity, scenarioId, userText, callback, false, null, "");
    }

    /** Restores observation of an existing owner-scoped Session after HMI recreation. */
    public static SessionConnection resumeSession(
            Activity activity,
            String scenarioId,
            SessionHandle handle,
            String resumeCursor,
            ScenarioCallback callback) {
        if (handle == null) {
            reportRejected(callback, false, "missing session handle");
            return null;
        }
        try {
            SessionContract.validateHandle(handle);
            validateCursor(resumeCursor);
        } catch (RuntimeException failure) {
            reportRejected(callback, false, "invalid session resume state");
            return null;
        }
        return openSessionInternal(
                activity,
                scenarioId,
                "",
                callback,
                false,
                handle,
                resumeCursor);
    }

    /**
     * Stage 1 binary-compatible entry point used by the maintained Client2 smali controller.
     * New HMI code must use {@link #openSession(Activity, String, String, ScenarioCallback)}.
     */
    @Deprecated
    public static boolean submit(
            Activity activity,
            String scenarioId,
            String userText,
            ScenarioCallback callback) {
        Submission replacement = openSessionInternal(
                activity,
                scenarioId,
                userText,
                callback,
                true,
                null,
                "");
        if (replacement == null) {
            return false;
        }
        Submission previous;
        synchronized (LEGACY_LOCK) {
            previous = legacySubmission;
            legacySubmission = replacement;
        }
        if (previous != null && previous != replacement) {
            previous.close();
            Log.i(TAG, "client2_legacy_session_replaced=true"
                    + " session_event_transport_used=true"
                    + " http_transport_used=false"
                    + " hardware_accessed=false");
        }
        return true;
    }

    /** Releases the compatibility stream when a smali lifecycle hook is available. */
    public static void closeLegacySession() {
        Submission previous;
        synchronized (LEGACY_LOCK) {
            previous = legacySubmission;
            legacySubmission = null;
        }
        if (previous != null) {
            previous.close();
        }
    }

    private static Submission openSessionInternal(
            Activity activity,
            String scenarioId,
            String userText,
            ScenarioCallback callback,
            boolean legacyCompatibility,
            SessionHandle resumeHandle,
            String resumeCursor) {
        if (activity == null || callback == null || !SCENARIOS.containsKey(scenarioId)) {
            reportRejected(callback, legacyCompatibility, "unsupported scenario");
            return null;
        }
        String boundedText = userText == null ? "" : userText.trim();
        if (boundedText.length() > SessionContract.MAX_UTTERANCE_CHARS) {
            boundedText = boundedText.substring(0, SessionContract.MAX_UTTERANCE_CHARS);
        }
        Submission submission = new Submission(
                activity.getApplicationContext(),
                activity.getMainExecutor(),
                scenarioId,
                SCENARIOS.get(scenarioId),
                boundedText,
                callback,
                legacyCompatibility,
                resumeHandle,
                resumeCursor);
        return submission.start() ? submission : null;
    }

    private static void reportRejected(
            ScenarioCallback callback,
            boolean legacyCompatibility,
            String reason) {
        if (callback == null) {
            return;
        }
        callback.onSessionError(null, ScenarioClient.ERROR_SUBSCRIPTION, reason);
        if (legacyCompatibility) {
            callback.onBridgeFailure(reason);
        }
    }

    private static final class Submission implements
            SessionConnection,
            ScenarioClient.ConnectionListener,
            RuntimeEventListener {
        private final android.content.Context appContext;
        private final Executor callbackExecutor;
        private final String uiScenarioId;
        private final String scenarioId;
        private final String userText;
        private final ScenarioCallback callback;
        private final boolean legacyCompatibility;
        private final boolean resumeExisting;
        private final String initialResumeCursor;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean legacyInitialProjection = new AtomicBoolean();

        private SessionClient client;
        private SessionHandle handle;
        private SessionSnapshot latestSnapshot;

        Submission(
                android.content.Context appContext,
                Executor callbackExecutor,
                String uiScenarioId,
                String scenarioId,
                String userText,
                ScenarioCallback callback,
                boolean legacyCompatibility,
                SessionHandle resumeHandle,
                String resumeCursor) {
            this.appContext = appContext;
            this.callbackExecutor = callbackExecutor;
            this.uiScenarioId = uiScenarioId;
            this.scenarioId = scenarioId;
            this.userText = userText;
            this.callback = callback;
            this.legacyCompatibility = legacyCompatibility;
            this.resumeExisting = resumeHandle != null;
            this.initialResumeCursor = resumeCursor == null ? "" : resumeCursor;
            this.handle = copy(resumeHandle);
        }

        boolean start() {
            try {
                legacyStatus("Session/Event connecting: " + uiScenarioId);
                client = new SessionClient(appContext, callbackExecutor, this);
                if (!client.connect()) {
                    finishFailure(
                            ScenarioClient.ERROR_TRANSPORT,
                            "Session/Event bind rejected");
                    return false;
                }
                return true;
            } catch (RuntimeException exception) {
                finishFailure(
                        failureCode(exception),
                        "Session/Event setup failed: " + exception.getClass().getSimpleName());
                return false;
            }
        }

        @Override
        public void onConnected(ScenarioClient connectedClient, boolean reconnected) {
            if (closed.get()) {
                return;
            }
            callback.onSessionConnectionChanged(true, reconnected);
            if (reconnected) {
                legacyStatus("Session/Event reconnected: " + uiScenarioId);
                Log.i(TAG, baseMarkers()
                        + " client2_session_reconnected=true");
                return;
            }
            try {
                if (resumeExisting) {
                    connectedClient.observeSession(
                            copy(handle),
                            initialResumeCursor,
                            this);
                    callback.onSessionOpened(copy(handle), scenarioId);
                    legacyStatus("Session resumed: " + uiScenarioId);
                    Log.i(TAG, baseMarkers()
                            + " client2_session_transport_connected=true"
                            + " client2_session_resumed=true"
                            + " session_id_present=" + hasText(handle.sessionId)
                            + " resume_cursor_present=" + hasText(initialResumeCursor));
                    return;
                }
                handle = connectedClient.openSession(request(), this);
                callback.onSessionOpened(copy(handle), scenarioId);
                legacyStatus("Session opened: " + uiScenarioId);
                Log.i(TAG, baseMarkers()
                        + " client2_session_transport_connected=true"
                        + " client2_session_opened=true"
                        + " session_id_present=" + hasText(handle.sessionId));
            } catch (RuntimeException exception) {
                finishFailure(
                        failureCode(exception),
                        "Session open failed: " + exception.getClass().getSimpleName());
            }
        }

        @Override
        public void onDisconnected() {
            if (closed.get()) {
                return;
            }
            callback.onSessionConnectionChanged(false, false);
            legacyStatus("Session/Event disconnected; reconnecting");
            Log.w(TAG, baseMarkers()
                    + " client2_session_disconnected=true");
            try {
                SessionClient current = client;
                if (current == null || !current.reconnect()) {
                    finishFailure(
                            ScenarioClient.ERROR_TRANSPORT,
                            "Session/Event reconnect rejected");
                }
            } catch (RuntimeException exception) {
                finishFailure(
                        failureCode(exception),
                        "Session/Event reconnect failed: "
                                + exception.getClass().getSimpleName());
            }
        }

        @Override
        public void onConnectionFailed(String code, String message) {
            finishFailure(code, "Session/Event runtime unavailable: " + safe(message));
        }

        @Override
        public void onSnapshot(SessionSnapshot snapshot) {
            if (closed.get() || snapshot == null) {
                return;
            }
            SessionSnapshot accepted = copy(snapshot);
            latestSnapshot = accepted;
            callback.onSessionSnapshot(copy(accepted));
            legacyStatus("Session state: " + stateName(accepted.state));
            Log.i(TAG, baseMarkers()
                    + " client2_session_snapshot_received=true"
                    + " session_state=" + stateName(accepted.state)
                    + " last_event_sequence=" + accepted.lastEventSequence);
            if (SessionContract.isTerminalState(accepted.state)) {
                projectLegacyReply(accepted.summary);
                close();
            }
        }

        @Override
        public void onEvent(RuntimeEvent event) {
            if (closed.get() || event == null) {
                return;
            }
            RuntimeEvent accepted = copy(event);
            callback.onSessionEvent(copy(accepted));
            legacyStatus("Session event " + accepted.sequence + ": " + accepted.type);
            Log.i(TAG, baseMarkers()
                    + " client2_session_event_received=true"
                    + " event_type=" + safeToken(accepted.type)
                    + " event_sequence=" + accepted.sequence
                    + " event_payload_kind=" + accepted.payloadKind);
            if (accepted.payloadKind == EventContract.PAYLOAD_MESSAGE
                    && accepted.message != null
                    && accepted.message.role == EventContract.MESSAGE_ASSISTANT) {
                projectLegacyReply(accepted.message.displayText);
            }
        }

        @Override
        public void onReplayComplete(long lastSequence) {
            if (closed.get()) {
                return;
            }
            SessionHandle currentHandle = copy(handle);
            callback.onSessionReplayComplete(currentHandle, lastSequence);
            Log.i(TAG, baseMarkers()
                    + " client2_session_replay_complete=true"
                    + " last_event_sequence=" + lastSequence);
            if (legacyCompatibility) {
                String summary = latestSnapshot == null ? "Session stream ready" : latestSnapshot.summary;
                projectLegacyReply(summary);
                if (legacyInitialProjection.compareAndSet(false, true)) {
                    Log.i(TAG, baseMarkers()
                            + " client2_legacy_callback_projected=true");
                } else {
                    Log.i(TAG, baseMarkers()
                            + " client2_legacy_callback_reprojected=true");
                }
            }
        }

        @Override
        public void onOverflow(String resumeCursor) {
            if (closed.get()) {
                return;
            }
            callback.onSessionOverflow(copy(handle), resumeCursor);
            legacyStatus("Session event overflow; replaying");
            Log.w(TAG, baseMarkers()
                    + " client2_session_overflow_recovery=true"
                    + " resume_cursor_present=" + hasText(resumeCursor));
        }

        @Override
        public void onClosed(int reasonCode, String resumeCursor) {
            if (closed.get()) {
                return;
            }
            callback.onSessionClosed(copy(handle), reasonCode, resumeCursor);
            finishFailure(
                    ScenarioClient.ERROR_SUBSCRIPTION,
                    "Session event stream closed: " + reasonCode);
        }

        @Override
        public void onError(String code, String message) {
            finishFailure(code, "Session event stream failed: " + safe(message));
        }

        @Override
        public boolean isConnected() {
            SessionClient current = client;
            return !closed.get() && current != null && current.isConnected();
        }

        @Override
        public SessionHandle getSessionHandle() {
            return copy(handle);
        }

        @Override
        public boolean cancel() {
            SessionClient current = client;
            SessionHandle currentHandle = handle;
            if (closed.get() || current == null || currentHandle == null) {
                return false;
            }
            try {
                return current.cancelSession(
                        currentHandle,
                        ICentralBrainSessionRuntime.CANCEL_REASON_USER);
            } catch (RuntimeException exception) {
                finishFailure(
                        failureCode(exception),
                        "Session cancel failed: " + exception.getClass().getSimpleName());
                return false;
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            SessionClient current = client;
            client = null;
            if (current != null) {
                current.close();
            }
            synchronized (LEGACY_LOCK) {
                if (legacySubmission == this) {
                    legacySubmission = null;
                }
            }
        }

        private SessionRequest request() {
            long now = System.currentTimeMillis();
            SessionRequest request = new SessionRequest();
            request.requestId = UUID.randomUUID().toString();
            request.scenarioId = scenarioId;
            request.utterance = userText;
            request.source = ICentralBrainSessionRuntime.SOURCE_HMI_BUTTON;
            request.seatZone = ICentralBrainSessionRuntime.SEAT_ZONE_DRIVER;
            request.locale = "zh-CN";
            request.deadlineEpochMs = now + DEADLINE_MS;
            request.clientContextVersion = 0;
            return request;
        }

        private void finishFailure(String code, String reason) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            String boundedCode = safe(code);
            String boundedReason = safe(reason);
            callback.onSessionError(copy(handle), boundedCode, boundedReason);
            if (legacyCompatibility) {
                callback.onBridgeFailure(boundedReason);
            }
            Log.e(TAG, baseMarkers()
                    + " client2_session_bridge_failed=true"
                    + " error_code=" + safeToken(boundedCode)
                    + " reason=" + boundedReason);
            SessionClient current = client;
            client = null;
            if (current != null) {
                current.close();
            }
            synchronized (LEGACY_LOCK) {
                if (legacySubmission == this) {
                    legacySubmission = null;
                }
            }
        }

        private void legacyStatus(String status) {
            if (legacyCompatibility) {
                callback.onBridgeStatus(status);
            }
        }

        private void projectLegacyReply(String value) {
            if (legacyCompatibility) {
                callback.onBridgeReply(hasText(value) ? value : "Session update available");
            }
        }

        private String baseMarkers() {
            return "ui_scenario_id=" + uiScenarioId
                    + " scenario_id=" + scenarioId
                    + " session_event_transport_used=true"
                    + " legacy_callback_compatibility=" + legacyCompatibility
                    + " http_transport_used=false"
                    + " service_dispatch_triggered=false"
                    + " hardware_accessed=false";
        }
    }

    private static SessionHandle copy(SessionHandle original) {
        if (original == null) {
            return null;
        }
        SessionHandle copy = new SessionHandle();
        copy.schemaVersion = original.schemaVersion;
        copy.sessionId = original.sessionId;
        copy.acceptedAtEpochMs = original.acceptedAtEpochMs;
        copy.expiresAtEpochMs = original.expiresAtEpochMs;
        return copy;
    }

    private static void validateCursor(String cursor) {
        if (cursor == null || cursor.length() > EventContract.MAX_CURSOR_CHARS) {
            throw new IllegalArgumentException("invalid resume cursor");
        }
        for (int index = 0; index < cursor.length(); index++) {
            if (Character.isISOControl(cursor.charAt(index))) {
                throw new IllegalArgumentException("invalid resume cursor");
            }
        }
    }

    private static SessionSnapshot copy(SessionSnapshot original) {
        if (original == null) {
            return null;
        }
        SessionSnapshot copy = new SessionSnapshot();
        copy.schemaVersion = original.schemaVersion;
        copy.sessionId = original.sessionId;
        copy.requestId = original.requestId;
        copy.scenarioId = original.scenarioId;
        copy.state = original.state;
        copy.activePlanRevision = original.activePlanRevision;
        copy.lastEventSequence = original.lastEventSequence;
        copy.createdAtEpochMs = original.createdAtEpochMs;
        copy.updatedAtEpochMs = original.updatedAtEpochMs;
        copy.deadlineEpochMs = original.deadlineEpochMs;
        copy.summary = original.summary;
        return copy;
    }

    private static RuntimeEvent copy(RuntimeEvent original) {
        if (original == null) {
            return null;
        }
        RuntimeEvent copy = new RuntimeEvent();
        copy.schemaVersion = original.schemaVersion;
        copy.eventId = original.eventId;
        copy.sequence = original.sequence;
        copy.sessionId = original.sessionId;
        copy.parentEventId = original.parentEventId;
        copy.parentSequence = original.parentSequence;
        copy.type = original.type;
        copy.source = original.source;
        copy.occurredAtEpochMs = original.occurredAtEpochMs;
        copy.privacyClass = original.privacyClass;
        copy.payloadDigest = original.payloadDigest;
        copy.eventDigest = original.eventDigest;
        copy.payloadKind = original.payloadKind;
        copy.action = copy(original.action);
        copy.observation = copy(original.observation);
        copy.message = copy(original.message);
        return copy;
    }

    private static ActionEvent copy(ActionEvent original) {
        if (original == null) {
            return null;
        }
        ActionEvent copy = new ActionEvent();
        copy.schemaVersion = original.schemaVersion;
        copy.actionId = original.actionId;
        copy.nodeId = original.nodeId;
        copy.capabilityId = original.capabilityId;
        copy.state = original.state;
        copy.actionDigest = original.actionDigest;
        copy.required = original.required;
        return copy;
    }

    private static ObservationEvent copy(ObservationEvent original) {
        if (original == null) {
            return null;
        }
        ObservationEvent copy = new ObservationEvent();
        copy.schemaVersion = original.schemaVersion;
        copy.observationId = original.observationId;
        copy.subjectType = original.subjectType;
        copy.subjectId = original.subjectId;
        copy.outcome = original.outcome;
        copy.quality = original.quality;
        copy.evidenceDigest = original.evidenceDigest;
        copy.terminal = original.terminal;
        return copy;
    }

    private static MessageEvent copy(MessageEvent original) {
        if (original == null) {
            return null;
        }
        MessageEvent copy = new MessageEvent();
        copy.schemaVersion = original.schemaVersion;
        copy.messageId = original.messageId;
        copy.role = original.role;
        copy.locale = original.locale;
        copy.displayText = original.displayText;
        copy.contentDigest = original.contentDigest;
        copy.redacted = original.redacted;
        copy.redactionReason = original.redactionReason;
        return copy;
    }

    private static Map<String, String> scenarioAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("care.cold", "scene.comfort.cold.v1");
        aliases.put("care.fatigue", "scene.fatigue.assist.v1");
        aliases.put("task.home", "scene.navigation.home.v1");
        aliases.put("skill.nap", "scene.rest.nap.v1");
        aliases.put("state.vehicle", "scene.diagnostics.vehicle.v1");
        aliases.put("memory.preference", "scene.memory.preference.v1");
        aliases.put("skills.catalog", "scene.skills.catalog.v1");
        aliases.put("governance.audit", "scene.governance.audit.v1");
        aliases.put("security.denied", "scene.security.denied.v1");
        aliases.put("security.privacy", "scene.security.privacy.v1");
        aliases.put("runtime.npu", "scene.runtime.npu.v1");
        aliases.put("system.overview", "scene.system.overview.v1");
        return Collections.unmodifiableMap(aliases);
    }

    private static String failureCode(RuntimeException exception) {
        return exception instanceof ScenarioClient.Failure
                ? ((ScenarioClient.Failure) exception).getCode()
                : ScenarioClient.ERROR_TRANSPORT;
    }

    private static String stateName(int state) {
        switch (state) {
            case ICentralBrainSessionRuntime.SESSION_STATE_CREATED:
                return "CREATED";
            case ICentralBrainSessionRuntime.SESSION_STATE_PLANNING:
                return "PLANNING";
            case ICentralBrainSessionRuntime.SESSION_STATE_WAITING_FOR_CONFIRMATION:
                return "WAITING_FOR_CONFIRMATION";
            case ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING:
                return "EXECUTING";
            case ICentralBrainSessionRuntime.SESSION_STATE_PARTIALLY_COMPLETED:
                return "PARTIALLY_COMPLETED";
            case ICentralBrainSessionRuntime.SESSION_STATE_COMPENSATING:
                return "COMPENSATING";
            case ICentralBrainSessionRuntime.SESSION_STATE_STUCK:
                return "STUCK";
            case ICentralBrainSessionRuntime.SESSION_STATE_COMPLETED:
                return "COMPLETED";
            case ICentralBrainSessionRuntime.SESSION_STATE_FAILED:
                return "FAILED";
            case ICentralBrainSessionRuntime.SESSION_STATE_CANCELLED:
                return "CANCELLED";
            default:
                return "UNKNOWN";
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String safe(String value) {
        String clean = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        return clean.length() <= 256 ? clean : clean.substring(0, 256);
    }

    private static String safeToken(String value) {
        return safe(value).replace(' ', '_');
    }
}
